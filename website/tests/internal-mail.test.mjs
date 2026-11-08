import test from 'node:test';
import assert from 'node:assert/strict';
import {createInternalMail,resolveMailRecipients} from '../node/internal-mail.mjs';
import {clanMailboxId} from '../node/internal-mail-clans.mjs';
import {isStaffMailAccess} from '../node/staff-mail-policy.mjs';
import {politicalMailContext} from '../node/internal-mail-government.mjs';
import {convertReportedMail} from '../node/internal-mail-reports.mjs';
const origin='https://cp.dunerealms.com';
const user={id:'8a8fd110-496e-4a20-a398-359c3dc07109'};
function fixture(options={}){
 const calls=[];
 const route=createInternalMail({}, {getIdentity:async()=>user,eligible:async()=>true,broker:async payload=>{calls.push(payload);return {ok:true,items:[]};},...options});
 return {calls,route,post:(body,headers={Origin:origin})=>route(new Request(origin+'/api/internal-mail/messages/list',{method:'POST',headers:{...headers,'Content-Type':'application/json'},body:JSON.stringify(body)}))};
}
test('mailbox owner always comes from the authenticated session',async()=>{
 const f=fixture();const response=await f.post({userId:'another-user',action:'internal_reveal_password',folder:'INBOX'});
 assert.equal(response.status,200);assert.deepEqual(f.calls,[{action:'internal_list',userId:user.id,folder:'INBOX'}]);
});
test('anonymous and ineligible accounts never reach the broker',async()=>{
 for(const [options,status] of [[{getIdentity:async()=>null},401],[{eligible:async()=>false},403]]){
  const f=fixture(options);assert.equal((await f.post({})).status,status);assert.equal(f.calls.length,0);
 }
});
test('cross-origin mailbox mutations are rejected',async()=>{
 const f=fixture();assert.equal((await f.post({},{Origin:'https://other.example'})).status,403);assert.equal(f.calls.length,0);
});
test('unexpected provider errors do not disclose private configuration',async()=>{
 const f=fixture({broker:async()=>{throw Error('private database credentials');}});
 const response=await f.post({});assert.equal(response.status,503);assert.equal((await response.json()).message,'Internal Mail is temporarily unavailable.');
});
test('forwarding requires the authenticated account verified contact',async()=>{
 const calls=[];const db={prepare:()=>({bind:id=>{assert.equal(id,user.id);return{first:async()=>({address:'verified@example.org',verified_at:123})};}})};
 const route=createInternalMail(db,{getIdentity:async()=>user,eligible:async()=>true,broker:async p=>{calls.push(p);return{ok:true};}});
 const send=address=>route(new Request(origin+'/api/internal-mail/forwarding',{method:'POST',headers:{Origin:origin,'Content-Type':'application/json'},body:JSON.stringify({address,userId:'other',verified:true})}));
 assert.equal((await send('unverified@example.org')).status,403);assert.equal(calls.length,0);
 assert.equal((await send('verified@example.org')).status,200);assert.deepEqual(calls[0],{action:'internal_forwarding',userId:user.id,address:'verified@example.org',verified:true});
 assert.equal((await send(null)).status,200);assert.equal(calls[1].address,null);
});
test('account-name recipients resolve on the server in every recipient field',async()=>{
 const db={prepare:()=>({bind:name=>{assert.equal(name,'traveller');return{first:async()=>({id:user.id,username:'traveller'})};}})};
 const calls=[];const values=await resolveMailRecipients(db,['@Traveller','traveller'],{eligible:async()=>true,broker:async p=>{calls.push(p);return{ok:true};}});
 assert.deepEqual(values,[user.id+'.internal@dunerealms.com']);assert.ok(calls.every(p=>p.userId===user.id));
});
test('clan mailbox identifiers are stable and separate from personal mailbox identifiers',()=>{
 assert.equal(clanMailboxId(user.id),clanMailboxId(user.id));assert.notEqual(clanMailboxId(user.id),user.id);assert.throws(()=>clanMailboxId('untrusted'));
});
test('a personal mailbox permission does not permit reading a clan mailbox',async()=>{
 const db={prepare:()=>({bind:()=>({first:async()=>null})})};let called=false;
 const route=createInternalMail(db,{getIdentity:async()=>user,eligible:async()=>true,broker:async()=>{called=true;return{ok:true};}});
 const response=await route(new Request(origin+'/api/internal-mail/messages/list',{method:'POST',headers:{Origin:origin,'Content-Type':'application/json'},body:JSON.stringify({scope:'clan',userId:'another-user'})}));
 assert.equal(response.status,403);assert.equal(called,false);
});
test('staff mailbox access follows staff responsibility, not a personal mailbox grant',()=>{
 assert.equal(isStaffMailAccess({rank:'user',ranks:['user'],nodes:['web.mail.internal.view']}),false);
 assert.equal(isStaffMailAccess({rank:'custom_support',ranks:['custom_support'],nodes:['web.tickets.staff']}),true);
 assert.equal(isStaffMailAccess({rank:'game_mod',ranks:['game_mod'],nodes:[]}),true);
});
test('external addresses and non-staff targets cannot bypass scoped recipients',async()=>{
 await assert.rejects(resolveMailRecipients({},['person@example.org']),e=>e.status===403);
 await assert.rejects(resolveMailRecipients({},['Staff (internal)'],{accountsOnly:true}),e=>e.status===403);
 const db={prepare:()=>({bind:()=>({first:async()=>user})})};
 await assert.rejects(resolveMailRecipients(db,['player'],{eligible:async()=>true,recipientAllowed:async()=>false}),e=>e.status===403);
});
test('website owner permissions alone cannot open private political mail',async()=>{
 const db={prepare:sql=>({bind:()=>({first:async()=>sql.includes('site_game_links')?{minecraft_uuid:user.id}:{role:'owners',assigned:0}})})};
 const value=await politicalMailContext(db,user,{SERVER_CONTROL_TOKEN:'a'.repeat(64)},{accessLookup:async()=>({nodes:['web.menu.game','web.mail.faction.view','web.mail.imperial.view']}),fetcher:async url=>({ok:true,json:async()=>url.includes('/bank/')?{playerUuid:user.id,factionId:'corrino'}:{playerUuid:user.id,offices:[]}})});
 assert.equal(value.canImperialMail,false);assert.equal(value.canFactionMail,false);
});
test('verified current faction leadership is bound to that faction only',async()=>{
 const db={prepare:sql=>({bind:()=>({first:async()=>sql.includes('site_game_links')?{minecraft_uuid:user.id}:{role:'user',assigned:0}})})};
 const get=office=>politicalMailContext(db,user,{SERVER_CONTROL_TOKEN:'a'.repeat(64)},{accessLookup:async()=>({nodes:['web.menu.game']}),fetcher:async url=>({ok:true,json:async()=>url.includes('/bank/')?{playerUuid:user.id,factionId:'corrino'}:{playerUuid:user.id,offices:[{office}]}})});
 assert.equal((await get('house-leader:Corrino')).canFactionMail,true);assert.equal((await get('house-leader:Atreides')).canFactionMail,false);
});
test('mail report ticket belongs to the reporter and conversion retries reuse it',async()=>{
 const row={id:user.id,reporter_id:user.id,reporter_name:'original_reporter',staff_message_id:'<report>',ticket_id:null};let created=0;
 const db={prepare:sql=>{let values=[];return{bind(...a){values=a;return this;},first:async()=>row,run:async()=>{if(sql.startsWith('UPDATE site_mail_reports')){row.ticket_id=values[0];row.ticket_number=values[1];}}};}};
 const broker=async p=>p.action==='internal_get'?{reportId:user.id,messageId:'<report>',subject:'Reported message',text:'Reason',attachments:[{partId:'1',contentType:'message/rfc822'}]}:{dataBase64:Buffer.from('Subject: original\r\n\r\nOriginal').toString('base64')};
 const createTicket=async p=>{created++;assert.equal(p.reporterId,user.id);assert.equal(p.reporterName,'original_reporter');return{ok:true,ticketId:123,ticketNumber:'DR_LG-0000006'};};
 const args={body:{folder:'INBOX',uid:'1',uidValidity:'1',reporterId:'forged-staff'},broker,createTicket};
 assert.equal((await convertReportedMail(db,args)).ticketId,123);assert.equal((await convertReportedMail(db,args)).ticketId,123);assert.equal(created,1);
});
