import test from 'node:test';
import assert from 'node:assert/strict';
import {createModeration,validateBanFeed} from '../control-panel/moderation.mjs';
const uuid='00000000-0000-4000-8000-000000000001';
const row=(scope='arrakis')=>({scope,...(scope==='network'?{recordId:'00000000-0000-4000-8000-000000000009'}:{}),uuid,key:scope+':'+uuid,name:'DemoPlayer',operator:'Moderator',reason:'Recorded reason',created:'2026-10-29 00:00:00 +0000',expires:'forever'});
test('ban feeds accept only fixed local planet scopes with exact UUID keys and complete metadata',()=>{
 const sources=['lobby','arrakis','kaitain','giedi_prime','caladan','sigma_draconis_iv','network'],valid={sources,bans:sources.map(scope=>row(scope)),globalConnected:false};
 assert.equal(validateBanFeed(valid),valid);
 for(const invalid of [null,{sources:[],bans:[]},{sources:['arrakis','arrakis'],bans:[]},{sources:['global'],bans:[]},
  {sources:['arrakis'],bans:[row('kaitain')]},{sources:['arrakis'],bans:[{...row(),key:'kaitain:'+uuid}]},
  {sources:['arrakis'],bans:[{...row(),uuid:'bad'}]},{sources:['arrakis'],bans:[{...row(),created:''}]},
  {sources:['arrakis'],bans:[{...row(),reason:'injected\ncommand'}]},{sources:['arrakis'],bans:[row(),row()]}])assert.throws(()=>validateBanFeed(invalid));
});
test('new planet feeds import exact snapshot keys and deactivate only checked absent sources',async()=>{
 const writes=[],known=[{id:'old-arrakis',source_key:'arrakis:old',scope:'arrakis'},{id:'old-kaitain',source_key:'kaitain:old',scope:'kaitain'},{id:'old-giedi',source_key:'giedi_prime:old',scope:'giedi_prime'}];
 const db={prepare(sql){return {bind(...args){return {async run(){writes.push({sql,args});return{meta:{changes:1}};}};},async all(){return{results:known};}};}};
 const moderation=createModeration(db,{SERVER_CONTROL_TOKEN:'t'.repeat(64)},{fetcher:async()=>Response.json({sources:['kaitain','giedi_prime'],bans:[row('kaitain'),row('giedi_prime')]})});
 await moderation.sync();
 const inserts=writes.filter(w=>w.sql.startsWith('INSERT'));
 assert.deepEqual(inserts.map(w=>w.args[2]),['kaitain:'+uuid,'giedi_prime:'+uuid]);
 assert.ok(inserts.every(w=>w.args[1]===w.args[2]+':'+row().created));
 assert.deepEqual(writes.filter(w=>w.sql.startsWith('UPDATE')).map(w=>w.args[1]),['old-kaitain','old-giedi']);
});
test('one malformed or unavailable source cannot write rows or retire retained ban history',async()=>{
 let writes=0;const db={prepare(){writes++;throw Error('No database access should occur');}};
 for(const response of [()=>Response.json({sources:['arrakis'],bans:[row(),row('ssh')]}),()=>Response.json({message:'Planet host unavailable'},{status:503})]){
  const moderation=createModeration(db,{SERVER_CONTROL_TOKEN:'t'.repeat(64)},{fetcher:async()=>response()});
  await assert.rejects(moderation.sync());assert.equal(writes,0);
 }
});
test('global ban reference shown at login is the same public CP and appeal record ID',async()=>{
 const ban=row('network'),writes=[];
 const db={prepare(sql){return{bind(...args){return{async run(){writes.push({sql,args});}};},async all(){return{results:[]};}};}};
 await createModeration(db,{SERVER_CONTROL_TOKEN:'t'.repeat(64)},{fetcher:async()=>Response.json({sources:['network'],bans:[ban]})}).sync();
 assert.equal(writes[0].args[0],ban.recordId);assert.equal(writes[0].args[2],'network:'+uuid);
 assert.throws(()=>validateBanFeed({sources:['network'],bans:[{...ban,recordId:'unknown'}]}));
});


import {startModerationSync} from '../control-panel/moderation.mjs';

test('background synchronization starts without a page request and stops cleanly',async()=>{
 let calls=0,tick,cleared=false;const db={prepare(){return{async all(){return{results:[]}}}}};const stop=startModerationSync(db,{SERVER_CONTROL_TOKEN:'t'.repeat(64)},{fetcher:async()=>{calls++;return Response.json({sources:['arrakis'],bans:[]})},setTimer(fn,ms){assert.equal(ms,30000);tick=fn;return{unref(){}}},clearTimer(){cleared=true},log(){assert.fail('Unexpected sync failure')}});await new Promise(r=>setImmediate(r));assert.equal(calls,1);await tick();assert.equal(calls,2);stop();await tick();assert.equal(calls,2);assert.equal(cleared,true);
});
