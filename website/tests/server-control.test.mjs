import test from 'node:test';
import assert from 'node:assert/strict';
import {createServerControl} from '../control-panel/server-control.mjs';
const origin='https://cp.dunerealms.com',cookie='__Host-dune_session='+'a'.repeat(43);
function rig(user){const calls=[],audit=[];const db={prepare(sql){return{bind(...args){return{async all(){assert.match(sql,/^SELECT rank_key FROM cp_account_ranks WHERE user_id=\?$/);return{results:[]};},async first(){if(sql.includes('FROM cp_ranks'))return null;assert.match(sql,/FROM site_sessions/);return user;},async run(){audit.push(args);}};}};}};const handle=createServerControl(db,{CP_ORIGIN:origin,SERVER_CONTROL_TOKEN:'t'.repeat(64),SERVER_CONTROL_OPERATORS:'owner'},{async fetcher(url,options){calls.push({url,options});return Response.json({ok:true});}});const request=(path='/api/servers',body,requestOrigin=origin)=>handle(new Request(origin+path,{method:body?'POST':'GET',headers:{Cookie:cookie,Origin:requestOrigin,'Content-Type':'application/json'},...(body?{body:JSON.stringify(body)}:{})}));return{request,calls,audit};}
test('server controls require session, staff role and explicit operator allowlist',async()=>{
 for(const [user,status] of [[null,401],[{id:'1',username:'owner',role:'player'},403],[{id:'2',username:'otherstaff',role:'staff'},403]]){const r=rig(user);assert.equal((await r.request()).status,status);assert.equal(r.calls.length,0);}
});
test('operator status/log paths are fixed and reject arbitrary targets',async()=>{
 const r=rig({id:'1',username:'owner',role:'owners'});
 assert.equal((await r.request()).status,200);assert.equal(r.calls[0].url,'http://127.0.0.1:15433/servers');
 assert.equal((await r.request('/api/servers/logs?server=lobby')).status,200);
 for(const path of ['/api/servers?url=https://attacker.test','/api/servers/logs?server=ssh','/api/servers/logs?server=lobby&server=proxy'])assert.equal((await r.request(path)).status,400);
 assert.equal(r.calls.length,2);
});
test('mutations enforce origin and single command, overwrite spoofed actor, and audit intent',async()=>{
 const r=rig({id:'1',username:'owner',role:'owners'}),body={server:'arrakis',action:'command',command:'list',actor:'intruder'};
 assert.equal((await r.request('/api/servers/action',body,'https://attacker.test')).status,403);
 for(const command of ['list\nstop','/op player'])assert.equal((await r.request('/api/servers/action',{...body,command})).status,400);
 assert.equal((await r.request('/api/servers/action',{...body,server:'proxy'})).status,400);
 assert.equal((await r.request('/api/servers/action',body)).status,200);
 assert.equal(JSON.parse(r.calls[0].options.body).actor,'owner');assert.equal(r.audit.length,1);assert.equal(r.audit[0][2],'server-control');assert.ok(!r.audit[0][3].includes('list'));
});
test('upstream failures do not expose secrets',async()=>{
 const db={prepare(sql){return{bind(){return{async all(){assert.match(sql,/^SELECT rank_key FROM cp_account_ranks WHERE user_id=\?$/);return{results:[]};},async first(){if(sql.includes('FROM cp_ranks'))return null;assert.match(sql,/FROM site_sessions/);return{id:'1',username:'owner',role:'owners'};}};}};}};
 const handle=createServerControl(db,{SERVER_CONTROL_TOKEN:'t'.repeat(64),SERVER_CONTROL_OPERATORS:'owner'},{fetcher:async()=>{throw Error('password secret');}});
 const response=await handle(new Request(origin+'/api/servers',{headers:{Cookie:cookie}}));assert.equal(response.status,503);assert.ok(!(await response.text()).includes('password'));
});
test('planet services use fixed tunnel routes and retain current permissions and actor attribution',async()=>{
 const r=rig({id:'1',username:'owner',role:'owners'});
 for(const server of ['kaitain','giedi_prime','caladan','sigma_draconis_iv']){
  assert.equal((await r.request('/api/servers/logs?server='+server)).status,200);
  assert.equal(r.calls.at(-1).url,'http://127.0.0.1:15433/logs?server='+server);
  assert.equal((await r.request('/api/servers/action',{server,action:'restart',actor:'attacker',host:'https://attacker.test'})).status,200);
  assert.deepEqual(JSON.parse(r.calls.at(-1).options.body),{server,action:'restart',actor:'owner'});
 }
 const before=r.calls.length;
 for(const server of ['kaitain;stop','giedi-prime','10.77.0.2:25573','https://attacker.test'])assert.equal((await r.request('/api/servers/action',{server,action:'restart'})).status,400);
 assert.equal(r.calls.length,before);
});
