import test from 'node:test';import assert from 'node:assert/strict';
import {createGameTicketContext,validateTicketContextRequest} from '../control-panel/game-ticket-context.mjs';
const actor='00000000-0000-0000-0009-01f09764757f',creator='10000000-0000-4000-8000-000000000001',suspect='20000000-0000-4000-8000-000000000002';
const key='context-game-bridge-secret'.padEnd(64,'x'),account='30000000-0000-4000-8000-000000000003';
const settings={GAME_LINK_SECRET:key};
const request=(body={actorUuid:actor,ticketId:'42',action:'read'},options={})=>new Request('https://cp.dunerealms.com/api/game/ticket-context'+(options.query||''),{method:options.method||'POST',headers:{Authorization:'Bearer '+(options.key??key),'Content-Type':'application/json'},...(options.method==='GET'?{}:{body:options.raw??JSON.stringify(body)})});

// Transactional route harness, not a substitute for the real MySQL procedure test.
class Fixture {
 constructor(){this.state={version:1,assigned:0,suspect:null,lastAction:null,lastActor:null,lastFrom:null,events:[],audits:[],nativeEvents:[],visible:true,manage:true,admin:true,trigger:true};this.calls=[];this.fail=null;}
 row(){const s=this.state;if(!s.visible)throw {sqlMessage:'case_denied'};const assigned=s.assigned===7;return{ticket_id:42,context_version:s.version,actor_uuid:actor,assigned_agent_uuid:assigned?actor:null,creator_uuid:creator,suspect_uuid:s.suspect,agent_id:7,assigned_staff_id:s.assigned,ticket_state:'open',actor_account_id:account,can_take:Number(s.manage&&(s.assigned===0||assigned)),can_set_suspect:Number(s.manage&&assigned),can_teleport:Number(s.admin&&assigned),can_inspect_inventory:Number(s.admin&&assigned&&!!s.suspect),can_edit_inventory:Number(s.admin&&assigned&&!!s.suspect),can_adjust_economy:Number(s.admin&&assigned&&!!s.suspect),can_edit_faction:Number(s.admin&&assigned&&!!s.suspect),last_action:s.lastAction,last_actor_uuid:s.lastActor,last_from_version:s.lastFrom};}
 async execute(sql,args){this.calls.push({sql,args});if(this.fail?.(sql))throw Error('private database password should never appear');const s=this.state;
  if(sql.startsWith('CALL ')){assert.equal(args[0],actor);assert.equal(args[1],'42');assert.equal(args[2],'read');return [[[this.row()],{}],[]];}
  if(sql.startsWith('INSERT IGNORE INTO dune_game_ticket_context')){if(s.version===0)s.version=1;return [{affectedRows:1},[]];}
  if(sql.startsWith('SELECT th.id'))return [[{thread_id:9,event_id:3}],[]];
  if(sql.startsWith('UPDATE drsupport_ticket')){if(s.assigned!==0)return [{affectedRows:0},[]];s.assigned=Number(args[0]);if(s.trigger){s.version++;s.lastAction=null;s.lastActor=null;s.lastFrom=null;}return [{affectedRows:1},[]];}
  if(sql.startsWith('INSERT INTO drsupport_thread_event')){s.nativeEvents.push(args);return [{affectedRows:1},[]];}
  if(sql.startsWith('SELECT minecraft_uuid'))return [args[0]===suspect?[{minecraft_uuid:suspect}]:[],[]];
  if(sql.startsWith('UPDATE dune_game_ticket_context SET suspect_uuid')){if(s.version!==args[3])return [{affectedRows:0},[]];s.suspect=args[0];s.version++;return [{affectedRows:1},[]];}
  if(sql.startsWith('UPDATE dune_game_ticket_context SET last_action')){s.lastAction=args[0];s.lastActor=args[1];s.lastFrom=args[2];return [{affectedRows:1},[]];}
  if(sql.startsWith('INSERT INTO dune_game_ticket_context_events')){s.events.push(args);return [{affectedRows:1},[]];}
  if(sql.startsWith('INSERT INTO account_audit')){s.audits.push(args);return [{affectedRows:1},[]];}
  throw Error('Unexpected query: '+sql);
 }
 async batch(statements){const before=structuredClone(this.state);try{const results=[];for(const stmt of statements)results.push(await stmt.run(this));return results;}catch(error){this.state=before;throw error;}}
 route(){return createGameTicketContext(this,settings,{now:()=>1000});}
}
const take=version=>({actorUuid:actor,ticketId:'42',action:'take',version});
const set=(version,uuid=suspect)=>({actorUuid:actor,ticketId:'42',action:'set-suspect',version,suspectUuid:uuid});

test('bridge capability, HTTP method and strict bounded actor request are mandatory',async()=>{
 const f=new Fixture(),route=f.route();assert.equal((await route(request(undefined,{key:'bad'}))).status,401);assert.equal((await route(request(undefined,{method:'GET'}))).status,405);
 assert.equal((await route(request(undefined,{query:'?actorUuid='+creator}))).status,400);
 for(const body of [null,{},take(0),take('1'),{...take(1),actorUuid:'00000000-0000-0000-0000-000000000000'},{...take(1),ticketId:42},{...take(1),userId:account},{...set(1),suspectUuid:'not-a-uuid'}])assert.equal((await route(request(body))).status,400);
 assert.equal((await route(request(undefined,{raw:'{"actorUuid":"'+actor+'","actorUuid":"'+creator+'","ticketId":"42","action":"read"}'}))).status,400);
 assert.equal((await route(request(undefined,{raw:'x'.repeat(2049)}))).status,400);assert.equal(f.calls.length,0);
});
test('context response exposes current canonical UUIDs and flags, never internal agent/account fields',async()=>{
 const f=new Fixture();const response=await f.route()(request());assert.equal(response.status,200);assert.match(response.headers.get('cache-control'),/private, no-store/);
 assert.deepEqual(await response.json(),{ticketId:'42',contextVersion:1,assignedAgentUuid:null,creatorUuid:creator,suspectUuid:null,canTake:true,canSetSuspect:false,actions:{teleport:false,inspectInventory:false,editInventory:false,adjustEconomy:false,editFaction:false}});
});
test('first authorised read establishes a stable context row; wrong scope never does',async()=>{
 const f=new Fixture();f.state.version=0;assert.equal((await(await f.route()(request())).json()).contextVersion,1);
 const denied=new Fixture();denied.state.version=0;denied.state.visible=false;assert.equal((await denied.route()(request())).status,403);assert.equal(denied.state.version,0);assert(!denied.calls.some(c=>c.sql.startsWith('INSERT')));
});
test('Take changes native assignment and its native event and audit in one transaction',async()=>{
 const f=new Fixture();const response=await f.route()(request(take(1)));assert.equal(response.status,200);assert.equal((await response.json()).assignedAgentUuid,actor);assert.equal(f.state.version,2);assert.equal(f.state.assigned,7);assert.equal(f.state.nativeEvents.length,1);assert.equal(f.state.events.length,1);assert.equal(f.state.audits.length,1);
});
test('identical Take retry is idempotent; altered or obsolete context cannot be replayed',async()=>{
 const f=new Fixture(),route=f.route();await route(request(take(1)));assert.equal((await route(request(take(1)))).status,200);assert.equal(f.state.nativeEvents.length,1);
 await route(request(set(2)));assert.equal((await route(request(take(1)))).status,409);assert.equal(f.state.version,3);
});
test('another assigned agent, removal of triage authority or native visibility denies Take',async()=>{
 for(const change of [f=>f.state.assigned=8,f=>f.state.manage=false,f=>f.state.visible=false]){const f=new Fixture();change(f);assert.equal((await f.route()(request(take(1)))).status,403);assert.equal(f.state.events.length,0);}
});
test('suspect update requires current assignment and a known verified actual UUID',async()=>{
 const f=new Fixture(),route=f.route();assert.equal((await route(request(set(1)))).status,403);await route(request(take(1)));
 assert.equal((await route(request(set(2,creator)))).status,409);assert.equal(f.state.suspect,null);
 const changed=await route(request(set(2)));assert.equal(changed.status,200);const context=await changed.json();assert.equal(context.suspectUuid,suspect);assert.equal(context.contextVersion,3);assert.equal(context.actions.inspectInventory,true);
 assert.equal((await route(request(set(2)))).status,200);assert.equal(f.state.events.length,2);
});
test('assignment does not manufacture dune.admin; returned high-risk flags stay false',async()=>{
 const f=new Fixture();f.state.admin=false;const route=f.route();await route(request(take(1)));const response=await route(request(set(2)));assert.deepEqual((await response.json()).actions,{teleport:false,inspectInventory:false,editInventory:false,adjustEconomy:false,editFaction:false});
});
test('missing native invalidation trigger rolls back Take instead of acknowledging a stale context',async()=>{
 const f=new Fixture();f.state.trigger=false;assert.equal((await f.route()(request(take(1)))).status,503);assert.equal(f.state.assigned,0);assert.equal(f.state.events.length,0);assert.equal(f.state.nativeEvents.length,0);
});
test('late audit/storage failure rolls back assignment, context, native event and audit together',async()=>{
 const f=new Fixture();f.fail=sql=>sql.startsWith('INSERT INTO account_audit');const response=await f.route()(request(take(1)));assert.equal(response.status,503);assert(!(await response.text()).includes('password'));assert.equal(f.state.assigned,0);assert.equal(f.state.version,1);assert.equal(f.state.events.length,0);assert.equal(f.state.nativeEvents.length,0);
});
test('action-authority procedure failure and unexpected identity never return optimistic success',async()=>{
 const f=new Fixture();f.fail=sql=>sql.startsWith('CALL');assert.equal((await f.route()(request())).status,503);
 const wrong=new Fixture();const original=wrong.row.bind(wrong);wrong.row=()=>({...original(),actor_uuid:creator});assert.equal((await wrong.route()(request())).status,503);
});

