import test from 'node:test';import assert from 'node:assert/strict';
import {LocalD1} from './d1-adapter.mjs';
import {createGameTickets} from '../control-panel/game-tickets.mjs';

const key='game-secret-'.padEnd(64,'x'),uuidA='00000000-0000-4000-8000-000000000001',uuidB='00000000-0000-4000-8000-000000000002',uuidC='00000000-0000-4000-8000-000000000003';
const idA='11111111-1111-4111-8111-111111111111',idB='22222222-2222-4222-8222-222222222222';
const schema=`
 CREATE TABLE site_accounts(id TEXT,username TEXT,role TEXT);CREATE TABLE site_game_links(user_id TEXT,minecraft_uuid TEXT);
 CREATE TABLE drsupport_ticket(ticket_id INTEGER,user_id INTEGER,status_id INTEGER,dept_id INTEGER,staff_id INTEGER,team_id INTEGER,lastupdate INTEGER,number TEXT);
 CREATE TABLE drsupport_ticket_status(id INTEGER,state TEXT,name TEXT);CREATE TABLE drsupport_department(id INTEGER,name TEXT);
 CREATE TABLE drsupport_ticket__cdata(ticket_id INTEGER,subject TEXT);CREATE TABLE native_ban_appeals(ticket_id INTEGER,status TEXT);
 CREATE TABLE drsupport_user_email(user_id INTEGER,address TEXT);CREATE TABLE drsupport_staff(staff_id INTEGER,username TEXT,isactive INTEGER,dept_id INTEGER,assigned_only INTEGER);
 CREATE TABLE drsupport_staff_dept_access(staff_id INTEGER,dept_id INTEGER);CREATE TABLE drsupport_team_member(staff_id INTEGER,team_id INTEGER);
 INSERT INTO site_accounts VALUES('${idA}','PlayerA','user'),('${idB}','PlayerB','user');
 INSERT INTO site_game_links VALUES('${idA}','${uuidA}'),('${idB}','${uuidB}');
 INSERT INTO drsupport_ticket_status VALUES(1,'open','Open'),(2,'closed','Resolved'),(3,'deleted','Deleted');
 INSERT INTO drsupport_department VALUES(1,'Support'),(2,'Ban appeals'),(3,'Legal'),(4,'Sales');
 INSERT INTO drsupport_user_email VALUES(1,'${idA}@accounts.dunerealms.invalid'),(2,'${idB}@accounts.dunerealms.invalid');
 INSERT INTO drsupport_ticket VALUES(1,1,1,1,0,0,1,'100001'),(2,2,1,1,0,0,2,'100002'),(3,1,2,2,0,0,3,'100003'),(4,2,1,3,7,0,4,'100004'),(5,2,1,4,0,8,5,'100005'),(6,1,3,1,7,0,6,'100006');
 INSERT INTO drsupport_ticket__cdata VALUES(1,'My support case'),(2,'Other player support'),(3,'Appeal closed'),(4,'Private legal case'),(5,'Private sales case'),(6,'Deleted case');
 INSERT INTO native_ban_appeals VALUES(3,'pending');
 INSERT INTO drsupport_staff VALUES(7,'${idA.replaceAll('-','')}',1,1,0);
 INSERT INTO drsupport_staff_dept_access VALUES(7,2),(7,3);
 INSERT INTO drsupport_team_member VALUES(7,8);
`;
function fixture(nodes=['web.tickets.view']){const db=new LocalD1(schema);const route=createGameTickets(db,{GAME_LINK_SECRET:key},{access:async()=>({nodes})});return {db,route};}
function request(body={uuid:uuidA},options={}){return new Request('https://cp.dunerealms.com/api/game/tickets'+(options.query||''),{method:options.method||'POST',headers:{Authorization:'Bearer '+(options.token??key),'Content-Type':'application/json',...(options.headers||{})},...(options.method==='GET'?{}:{body:options.raw??JSON.stringify(body)})});}

test('game ticket bridge authenticates capability, rejects malformed requests and does not trust website identity headers',async()=>{
 const {db,route}=fixture();try{
  assert.equal((await route(request(undefined,{token:'wrong',headers:{'oai-authenticated-user-id':idA}}))).status,401);
  assert.equal((await route(request(undefined,{method:'GET'}))).status,405);
  for(const body of [{uuid:'bad'}, {uuid:uuidA,view:'all'},{uuid:'A0000000-0000-4000-8000-000000000001'},null])assert.equal((await route(request(body))).status,400);
  assert.equal((await route(request(undefined,{raw:'not-json'}))).status,400);
 }finally{db.close();}
});
test('own summaries bind verified UUID to immutable account and never expose other tickets or private fields',async()=>{
 const {db,route}=fixture();try{
  const response=await route(request({uuid:uuidA,userId:idB,email:idB+'@accounts.dunerealms.invalid'},{query:'?userId='+idB}));
  assert.equal(response.status,200);assert.match(response.headers.get('Cache-Control'),/private, no-store/);
  const result=await response.json();assert.equal(result.view,'own');assert.equal(result.linked,true);assert.deepEqual(result.tickets.map(t=>t.id),['3','1']);
  assert.equal(result.tickets[0].appealStatus,'closed');assert.equal(result.tickets[0].status,'closed');assert.equal(result.tickets[0].statusLabel,'Resolved');
  for(const ticket of result.tickets)assert.equal(ticket.url,'https://support.dunerealms.com/tickets.php?id='+ticket.id);
  const data=JSON.stringify(result);for(const secret of [uuidA,idA,idB,'@','Private legal','Other player support','Deleted case'])assert(!data.includes(secret));
 }finally{db.close();}
});
test('unlinked identity returns no tickets and own permission remains mandatory',async()=>{
 const f=fixture();try{const result=await(await f.route(request({uuid:uuidC}))).json();assert.equal(result.linked,false);assert.deepEqual(result.tickets,[]);}finally{f.db.close();}
 const denied=fixture([]);try{assert.equal((await denied.route(request())).status,403);}finally{denied.db.close();}
});
test('staff summaries intersect current central departments and native visibility, never granting Legal from stale membership',async()=>{
 const f=fixture(['web.tickets.view','web.tickets.staff','web.tickets.support']);try{
  const result=await(await f.route(request({uuid:uuidA,view:'staff'}))).json();assert.deepEqual(result.tickets.map(t=>t.id),['2','1']);
  assert(result.tickets.every(t=>t.department==='Support'&&t.url.includes('/scp/tickets.php?id=')));
  await f.db.prepare('UPDATE drsupport_staff SET isactive=0').run();assert.deepEqual((await(await f.route(request({uuid:uuidA,view:'staff'}))).json()).tickets,[]);
 }finally{f.db.close();}
 for(const nodes of [['web.tickets.view'],['web.tickets.staff'],['web.tickets.staff','web.tickets.admin']]){const f=fixture(nodes);try{assert.equal((await f.route(request({uuid:uuidA,view:'staff'}))).status,403);}finally{f.db.close();}}
});
test('assigned-only staff cannot enumerate department tickets; explicit legal and sales nodes are still required',async()=>{
 const f=fixture(['web.tickets.staff','web.tickets.support','web.tickets.legal','web.tickets.sales']);try{
  await f.db.prepare('UPDATE drsupport_staff SET assigned_only=1').run();
  let result=await(await f.route(request({uuid:uuidA,view:'staff'}))).json();assert.deepEqual(result.tickets.map(t=>t.id),['5','4']);
  await f.db.prepare('UPDATE drsupport_ticket SET staff_id=7 WHERE ticket_id=1').run();result=await(await f.route(request({uuid:uuidA,view:'staff'}))).json();assert.deepEqual(result.tickets.map(t=>t.id),['5','4','1']);
 }finally{f.db.close();}
});
test('summary results are capped, plain text bounded and unavailable storage fails without secrets',async()=>{
 const f=fixture();try{
  for(let n=20;n<60;n++){await f.db.prepare('INSERT INTO drsupport_ticket VALUES(?,1,1,1,0,0,?,?)').bind(n,n,String(n)).run();await f.db.prepare('INSERT INTO drsupport_ticket__cdata VALUES(?,?)').bind(n,'§c\n'+('Long title '.repeat(40))).run();}
  const result=await(await f.route(request())).json();assert.equal(result.tickets.length,30);for(const t of result.tickets){assert(t.subject.length<=200);assert(!/[\x00-\x1f§]/.test(t.subject));}
 }finally{f.db.close();}
 const broken=createGameTickets({prepare(){throw Error('private SQL password')}},{GAME_LINK_SECRET:key});const response=await broken(request());assert.equal(response.status,503);assert(!(await response.text()).includes('password'));
});


test('Support and Appeals team scopes remain separate despite native cross-department membership',async()=>{
 for(const [scope,ids] of [['web.tickets.support',['2','1']],['web.tickets.appeals',['3']]]){
  const f=fixture(['web.tickets.staff',scope]);try{
   const response=await f.route(request({uuid:uuidA,view:'staff',department:'Legal',includeAll:true}));assert.equal(response.status,200);
   const result=await response.json();assert.deepEqual(result.tickets.map(t=>t.id),ids);
   assert(!result.tickets.some(t=>['Legal','Sales'].includes(t.department)));
  }finally{f.db.close();}
 }
});
test('department scope is freshly evaluated after team permission removal, not cached native membership',async()=>{
 const nodes=['web.tickets.staff','web.tickets.support'];const f=fixture(nodes);try{
  assert.deepEqual((await(await f.route(request({uuid:uuidA,view:'staff'}))).json()).tickets.map(t=>t.id),['2','1']);
  nodes.splice(1,1,'web.tickets.appeals');
  assert.deepEqual((await(await f.route(request({uuid:uuidA,view:'staff'}))).json()).tickets.map(t=>t.id),['3']);
  nodes.splice(1,1);assert.equal((await f.route(request({uuid:uuidA,view:'staff'}))).status,403);
 }finally{f.db.close();}
});
