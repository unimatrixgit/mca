import {test} from 'node:test';
import assert from 'node:assert/strict';
import {LocalD1} from './d1-adapter.mjs';
import {createOverview} from '../control-panel/overview.mjs';
const schema=`CREATE TABLE drsupport_ticket(ticket_id INTEGER,user_id INTEGER,status_id INTEGER,topic_id INTEGER);
CREATE TABLE drsupport_ticket_status(id INTEGER,state TEXT);
CREATE TABLE drsupport_help_topic(topic_id INTEGER,topic TEXT);
CREATE TABLE drsupport_user_email(user_id INTEGER,address TEXT);
CREATE TABLE site_game_links(user_id TEXT,minecraft_uuid TEXT);
CREATE TABLE native_ban_appeals(ticket_id INTEGER,status TEXT);
INSERT INTO drsupport_ticket_status VALUES(1,'open'),(2,'closed');
INSERT INTO drsupport_help_topic VALUES(1,'General support'),(2,'Ban appeal'),(3,'Report forum post');
INSERT INTO drsupport_user_email VALUES(1,'account-a@accounts.dunerealms.invalid'),(2,'account-b@accounts.dunerealms.invalid');
INSERT INTO drsupport_ticket VALUES(1,1,1,1),(2,1,1,2),(3,2,1,1),(4,2,2,2),(5,2,1,3),(6,1,2,1);`;
const request=path=>new Request('https://cp.dunerealms.com/api/overview'+path);
function handler(db,nodes,user={id:'account-a',username:'traveller'}){return createOverview(db,{identity:async()=>user,access:async()=>({label:'User',nodes})});}
test('user overview counts only that immutable account and ignores supplied account IDs',async()=>{
 const db=new LocalD1(schema);try{const response=await handler(db,['web.tickets.view','web.menu.game'])(request('?userId=account-b'));
 assert.equal(response.status,200);assert.match(response.headers.get('Cache-Control'),/no-store/);
 const result=await response.json();assert.equal(result.metrics.find(x=>x.key==='open-tickets').value,2);assert.equal(result.metrics.find(x=>x.key==='open-appeals').value,1);
 assert.equal(result.metrics.find(x=>x.key==='game-link').value,'Not linked');}finally{db.close();}
});
test('support and appeal staff stats stay separated by their website permissions',async()=>{
 const db=new LocalD1(schema);try{
 const support=await (await handler(db,['web.tickets.staff','web.tickets.support'])(request('/staff'))).json();
 assert.deepEqual(support.metrics.map(x=>[x.key,x.value]),[['open-support',2]]);
 const appeal=await (await handler(db,['web.tickets.staff','web.tickets.appeals'])(request('/staff'))).json();
 assert.deepEqual(appeal.metrics.map(x=>[x.key,x.value]),[['open-appeals',1]]);
 assert.equal((await handler(db,['web.tickets.view'])(request('/staff'))).status,403);
 }finally{db.close();}
});
test('unavailable native data never becomes a fake zero, and authentication is required',async()=>{
 const db=new LocalD1('');try{
 const result=await (await handler(db,['web.tickets.view'])(request(''))).json();
 assert.equal(result.metrics[0].value,null);assert.equal(result.metrics[0].available,false);
 assert.equal((await handler(db,[],null)(request(''))).status,401);
 assert.equal((await handler(db,[])(new Request('https://cp.dunerealms.com/api/overview',{method:'POST'}))).status,405);
 }finally{db.close();}
});

test('closed native appeals disappear from counters and generic reopen does not erase final decisions',async()=>{
 const db=new LocalD1(schema);try{
 const nodes=['web.tickets.staff','web.tickets.appeals'];
 const count=async()=>{const result=await(await handler(db,nodes)(request('/staff'))).json();return result.metrics.find(x=>x.key==='open-appeals').value;};
 assert.equal(await count(),1);
 await db.prepare("INSERT INTO native_ban_appeals VALUES(2,'pending')").run();
 await db.prepare('UPDATE drsupport_ticket SET status_id=2 WHERE ticket_id=2').run();assert.equal(await count(),0);
 await db.prepare('UPDATE drsupport_ticket SET status_id=1 WHERE ticket_id=2').run();assert.equal(await count(),1);
 for(const decision of ['approved','denied']){await db.prepare('UPDATE native_ban_appeals SET status=? WHERE ticket_id=2').bind(decision).run();assert.equal(await count(),0);}
 }finally{db.close();}
});
