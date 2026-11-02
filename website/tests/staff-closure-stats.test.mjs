import test from 'node:test';
import assert from 'node:assert/strict';
import {LocalD1} from './d1-adapter.mjs';
import {staffClosureStats,statsForAccount} from '../node/staff-closure-stats.mjs';
import {publicStaffRoster,createStaffRoster} from '../node/staff-roster.mjs';
function setup(){return new LocalD1(`
 CREATE TABLE site_accounts(id TEXT PRIMARY KEY,username TEXT,role TEXT);
 CREATE TABLE cp_account_ranks(user_id TEXT,rank_key TEXT);
 CREATE TABLE cp_ranks(rank_key TEXT,label TEXT,rank_level INTEGER,kind TEXT,permissions_json TEXT,version INTEGER);
 CREATE TABLE drsupport_staff(staff_id INTEGER PRIMARY KEY,username TEXT,backend TEXT);
 CREATE TABLE drsupport_event(id INTEGER PRIMARY KEY,name TEXT);
 CREATE TABLE drsupport_thread(id INTEGER PRIMARY KEY,object_id INTEGER,object_type TEXT);
 CREATE TABLE drsupport_ticket(ticket_id INTEGER PRIMARY KEY,status_id INTEGER);
 CREATE TABLE drsupport_ticket_status(id INTEGER PRIMARY KEY,state TEXT);
 CREATE TABLE drsupport_department(id INTEGER PRIMARY KEY,name TEXT);
 CREATE TABLE drsupport_thread_event(id INTEGER PRIMARY KEY,thread_id INTEGER,event_id INTEGER,uid_type TEXT,uid INTEGER,dept_id INTEGER,annulled INTEGER,staff_id INTEGER);
 CREATE TABLE dune_dev_tasks(ticket_id INTEGER PRIMARY KEY,approval TEXT);
 CREATE TABLE dune_dev_task_events(id INTEGER PRIMARY KEY,ticket_id INTEGER,actor_id TEXT,action TEXT);
 INSERT INTO site_accounts VALUES('owner-private-id','unimatrix999','owners'),('admin-private-id','grmacken','web_net_admin'),('dev-private-id','dev','web_dev');
 INSERT INTO drsupport_staff VALUES(1,'ownerprivateid','dunerealms-staff'),(2,'adminprivateid','dunerealms-staff'),(3,'devprivateid','dunerealms-staff');
 INSERT INTO drsupport_event VALUES(2,'closed');
 INSERT INTO drsupport_ticket_status VALUES(1,'open'),(3,'closed');
 INSERT INTO drsupport_department VALUES(1,'Support'),(2,'Legal'),(8,'Dev team');
 `);}
async function ticket(db,id,{uid=1,type='S',dept=1,state=3,annulled=0,assigned=2,devState,devActor='dev-private-id',devAction='completed'}={}){
 await db.prepare('INSERT INTO drsupport_thread VALUES(?,?,?)').bind(id,id,'T').run();
 await db.prepare('INSERT INTO drsupport_ticket VALUES(?,?)').bind(id,state).run();
 await db.prepare('INSERT INTO drsupport_thread_event VALUES(?,?,2,?,?,?,?,?)').bind(id,id,type,uid,dept,annulled,assigned).run();
 if(devState){await db.prepare('INSERT INTO dune_dev_tasks VALUES(?,?)').bind(id,devState).run();await db.prepare('INSERT INTO dune_dev_task_events VALUES(?,?,?,?)').bind(id,id,devActor,devAction).run();}
}
test('credits the verified closing actor and department, not the assignee or account rank',async()=>{
 const db=setup();try{await ticket(db,1,{uid:1,assigned:2});const s=await staffClosureStats(db);assert.equal(statsForAccount(s,'owner-private-id').departments.support,1);assert.equal(statsForAccount(s,'admin-private-id').totalClosed,0);}finally{db.close();}
});
test('excludes reopened, annulled, system and administrative consolidation records',async()=>{
 const db=setup();try{
 await ticket(db,1,{state:1});await ticket(db,2,{annulled:1});await ticket(db,3,{type:null,uid:0});
 await ticket(db,4,{devState:'consolidated'});await ticket(db,5,{devState:'superseded'});await ticket(db,6,{devState:'approved'});
 const s=await staffClosureStats(db);assert.equal(statsForAccount(s,'owner-private-id').totalClosed,0);assert.equal(statsForAccount(s,'dev-private-id').totalClosed,0);
 }finally{db.close();}
});
test('custom Dev completion/denial attribution is used only for valid closed work, without double credit',async()=>{
 const db=setup();try{
 await ticket(db,1,{type:'S',uid:null,dept:8,devState:'completed'});
 await ticket(db,2,{type:'U',uid:56,dept:8,devState:'denied',devAction:'denied',devActor:'owner-private-id'});
 await ticket(db,3,{uid:1,dept:8,devState:'completed'});
 const s=await staffClosureStats(db);assert.equal(statsForAccount(s,'dev-private-id').departments.dev,1);assert.equal(statsForAccount(s,'owner-private-id').departments.dev,2);
 }finally{db.close();}
});
test('one latest valid closure wins even if older closure was not annulled',async()=>{
 const db=setup();try{await ticket(db,1,{uid:1});await db.prepare("INSERT INTO drsupport_thread_event VALUES(99,1,2,'S',2,2,0,1)").run();const s=await staffClosureStats(db);assert.equal(statsForAccount(s,'owner-private-id').totalClosed,0);assert.equal(statsForAccount(s,'admin-private-id').departments.legal,1);}finally{db.close();}
});
test('25 is a total per department, not a percentage; roster exposes only safe aggregate counts',async()=>{
 const db=setup();try{
 for(let id=1;id<=25;id++)await ticket(db,id,{uid:1});await ticket(db,26,{uid:1,dept:2});
 const roster=await publicStaffRoster(db),owner=roster.find(r=>r.username==='unimatrix999');assert.equal(owner.closedTickets.totalClosed,26);assert.equal(owner.closedTickets.departments.support,25);assert.equal(owner.closedTickets.departments.legal,1);
 const html=await(await createStaffRoster(db)(new Request('https://www.dunerealms.com/staff.html'))).text();assert(html.includes('Personal department seal earned'));assert(html.includes('1 / 25 for a personal department seal'));
 const publicJson=JSON.stringify(roster);for(const secret of ['owner-private-id','admin-private-id','ticket_id','staff_id','department_id'])assert(!publicJson.includes(secret));
 }finally{db.close();}
});
test('missing native statistics fail visibly unavailable, never grant an award or invent zero',async()=>{
 const db=new LocalD1('CREATE TABLE placeholder(id TEXT)');try{assert.equal(await staffClosureStats(db),null);assert.equal(statsForAccount(null,'some-id'),null);}finally{db.close();}
});
