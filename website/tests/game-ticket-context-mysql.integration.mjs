// Explicit isolated-server QA only. Never point this harness at a production database.
import assert from 'node:assert/strict';import fs from 'node:fs';import path from 'node:path';import {createRequire} from 'node:module';
import {createGameTicketContext} from '../control-panel/game-ticket-context.mjs';
const socket=process.env.DUNE_CASE_TEST_SOCKET,schemaFile=process.env.DUNE_CASE_TEST_SCHEMA;
assert.match(socket||'',/^\/tmp\/dune-case324-qa-[a-zA-Z0-9_-]+\/mysql\.sock$/);
const qa=path.dirname(socket);assert.equal(fs.readFileSync(path.join(qa,'.codex-qa324'),'utf8'),'isolated-casework-324');
const require=createRequire(new URL('../node/mysql.mjs',import.meta.url)),mysql=require('mysql2/promise');
const connect=()=>mysql.createConnection({socketPath:socket,user:'root',database:'website',multipleStatements:false});
const bootstrap=await mysql.createConnection({socketPath:socket,user:'root'});let c,checks=0,injectAuditFailure=false;
const equal=(a,b)=>{assert.deepEqual(a,b);checks++;};
const source=fs.readFileSync(new URL('../integrations/osticket/game-ticket-context.sql',import.meta.url),'utf8');
const schema=JSON.parse(fs.readFileSync(schemaFile,'utf8'));
const actor='00000000-0000-0000-0009-01f09764757f',actorAccount='11111111-1111-4111-8111-111111111111',creator='10000000-0000-4000-8000-000000000001',creatorAccount='22222222-2222-4222-8222-222222222222',suspect='20000000-0000-4000-8000-000000000002';
const settings={GAME_LINK_SECRET:'isolated-case324-fixture'.padEnd(64,'x')};
async function script(connection,text){
 let delimiter=';',buffer='';
 for(const line of text.split(/\r?\n/)){
  if(line.trim().startsWith('GRANT '))continue; // No production grants or users are installed.
  if(line.trim().startsWith('DELIMITER ')){assert.equal(buffer.trim(),'');delimiter=line.trim().slice(10);continue;}
  if(!buffer.trim()&&(!line.trim()||line.trim().startsWith('--')))continue;
  buffer+=line+'\n';if(buffer.trimEnd().endsWith(delimiter)){await connection.query(buffer.trimEnd().slice(0,-delimiter.length));buffer='';}
 }
 assert.equal(buffer.trim(),'');
}
async function insert(table,overrides){
 assert.match(table,/^[a-z_]+$/);const[columns]=await c.query('SHOW COLUMNS FROM '+table),values={...overrides};
 for(const column of columns){if(Object.hasOwn(values,column.Field)||column.Null==='YES'||column.Default!==null||column.Extra.includes('auto_increment'))continue;
  const type=column.Type.toLowerCase();values[column.Field]=/^(tinyint|smallint|mediumint|int|bigint|decimal|float|double|bit)/.test(type)?0:type.startsWith('datetime')||type.startsWith('timestamp')?'2026-11-01 00:00:00':type.startsWith('date')?'2026-11-01':type.startsWith('enum(')?type.match(/^enum\('([^']*)'/)[1]:type.includes('blob')?Buffer.alloc(0):type==='json'?'[]':'';
 }
 const names=Object.keys(values);await c.execute('INSERT INTO '+table+'('+names.map(n=>'`'+n+'`').join(',')+') VALUES('+names.map(()=>'?').join(',')+')',names.map(n=>values[n]));
}
async function seed(){
 await c.query('SET FOREIGN_KEY_CHECKS=0');for(const table of [...Object.keys(schema),'dune_game_ticket_context','dune_game_ticket_context_events'])await c.query('TRUNCATE TABLE '+table);await c.query('SET FOREIGN_KEY_CHECKS=1');
 await insert('site_accounts',{id:actorAccount,username:'case_fixture',display_name:'Case fixture',role:'case-support'});
 await insert('site_accounts',{id:creatorAccount,username:'creator_fixture',display_name:'Creator fixture',role:'user'});
 await insert('site_game_links',{user_id:actorAccount,minecraft_uuid:actor,linked_at:1});await insert('site_game_links',{user_id:creatorAccount,minecraft_uuid:creator,linked_at:1});
 await insert('site_game_identities',{minecraft_uuid:suspect,first_linked_at:1,last_name:'KnownFixture'});
 await insert('cp_ranks',{rank_key:'case-support',label:'Case fixture rank',kind:'access',rank_level:20,permissions_json:JSON.stringify(['web.tickets.staff','web.tickets.manage','web.tickets.support'])});
 await insert('cp_rank_game_permissions',{rank_key:'case-support',permissions_json:'["dune.admin"]'});
 for(const[id,name]of [[1,'Support'],[2,'Ban appeals'],[3,'Legal'],[4,'Sales'],[5,'Network staff'],[6,'Forum support']])await insert('drsupport_department',{id,name,flags:0});
 await insert('drsupport_staff',{staff_id:7,username:actorAccount.replaceAll('-',''),isactive:1,dept_id:1,role_id:1,assigned_only:0,onvacation:0});
 await insert('drsupport_ticket_status',{id:1,name:'Open',state:'open'});await insert('drsupport_ticket_status',{id:2,name:'Resolved',state:'closed'});
 await insert('drsupport_user',{id:11,name:'Creator fixture'});await insert('drsupport_user_email',{id:21,user_id:11,address:creatorAccount+'@accounts.dunerealms.invalid'});
 await insert('drsupport_ticket',{ticket_id:42,number:'424242',user_id:11,user_email_id:21,status_id:1,dept_id:1,staff_id:0,team_id:0,topic_id:1});
 await insert('drsupport_ticket__cdata',{ticket_id:42,subject:'Isolated case fixture'});await insert('drsupport_thread',{id:9,object_id:42,object_type:'T'});await insert('drsupport_event',{id:3,name:'assigned'});
}
const execute=(sql,values=[])=>{if(injectAuditFailure&&sql.startsWith('INSERT INTO account_audit'))throw Error('private fixture audit failure');return c.execute(sql,values);};
const db={async batch(statements){await c.beginTransaction();try{const results=[];for(const statement of statements)results.push(await statement.run({execute}));await c.commit();return results;}catch(error){await c.rollback();throw error;}}};
const route=createGameTicketContext(db,settings);
const request=body=>route(new Request('https://cp.dunerealms.com/api/game/ticket-context',{method:'POST',headers:{Authorization:'Bearer '+settings.GAME_LINK_SECRET,'Content-Type':'application/json'},body:JSON.stringify({actorUuid:actor,ticketId:'42',...body})}));
async function call(action='read',version=null,connection=c){const[sets]=await connection.execute('CALL dunegameplay.dune_case_action_lock(?,?,?,?)',[actor,42,version,action]);return sets[0][0];}
async function denied(fn,code='case_denied'){await assert.rejects(fn,error=>error.sqlMessage===code);checks++;}
async function count(table){return Number((await c.query('SELECT COUNT(*) n FROM '+table))[0][0].n);}
try{
 const[info]=await bootstrap.query('SELECT @@datadir AS dir,@@skip_networking AS isolated');assert.equal(path.resolve(info[0].dir),path.join(qa,'data'));assert.equal(Number(info[0].isolated),1);
 await bootstrap.query('CREATE DATABASE website CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci');await bootstrap.query('CREATE DATABASE dunegameplay CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci');await bootstrap.query('USE website');
 await bootstrap.query('SET FOREIGN_KEY_CHECKS=0');for(const[table,ddl]of Object.entries(schema)){assert.match(table,/^[a-z_]+$/);assert.ok(ddl,'Missing source DDL '+table);await bootstrap.query(ddl);}await bootstrap.query('SET FOREIGN_KEY_CHECKS=1');
 await script(bootstrap,source.replaceAll('CREATE DEFINER=`website`@`%`','CREATE DEFINER=CURRENT_USER'));await bootstrap.end();
 c=await connect();await c.query("SET SESSION sql_mode='STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION,NO_BACKSLASH_ESCAPES'");
 await seed();let row=await call();equal(Number(row.context_version),0);equal(Number(row.can_take),1);equal(Number(row.can_inspect_inventory),0);equal(await count('dune_game_ticket_context'),0);
 let response=await request({action:'read'});equal(response.status,200);equal((await response.json()).contextVersion,1);equal(await count('dune_game_ticket_context'),1);
 response=await request({action:'take',version:1});equal(response.status,200);equal((await response.json()).assignedAgentUuid,actor);equal(Number((await call()).context_version),2);equal(await count('drsupport_thread_event'),1);equal(await count('dune_game_ticket_context_events'),1);equal(await count('account_audit'),1);
 equal((await request({action:'take',version:1})).status,200);equal(await count('drsupport_thread_event'),1);
 equal((await request({action:'set-suspect',version:2,suspectUuid:creator})).status,409);equal((await call()).suspect_uuid,null);
 equal((await request({action:'set-suspect',version:2,suspectUuid:suspect})).status,200);row=await call();equal(Number(row.context_version),3);equal(row.suspect_uuid,suspect);equal((await call('inspect_inventory',3)).target_uuid,suspect);await denied(()=>call('inspect_inventory',2),'case_conflict');
 await c.query("UPDATE cp_rank_game_permissions SET permissions_json='[]'");equal(Number((await call()).can_inspect_inventory),0);await denied(()=>call('inspect_inventory',3));
 await seed();await c.query('UPDATE drsupport_ticket SET dept_id=3 WHERE ticket_id=42');await insert('drsupport_staff_dept_access',{staff_id:7,dept_id:3});await denied(()=>call());
 await c.execute('UPDATE cp_ranks SET permissions_json=?',[JSON.stringify(['web.tickets.staff','web.tickets.manage','web.tickets.legal'])]);equal(Number((await call()).can_take),1);
 await c.query('UPDATE drsupport_staff SET assigned_only=1');await denied(()=>call());await insert('drsupport_team',{team_id:8,name:'Isolated team',flags:1});await insert('drsupport_team_member',{staff_id:7,team_id:8});await c.query('UPDATE drsupport_ticket SET team_id=8 WHERE ticket_id=42');equal(Number((await call()).can_take),1);await c.query('DELETE FROM drsupport_team_member');await denied(()=>call());
 await seed();await request({action:'read'});for(const update of ['staff_id=8','dept_id=2','status_id=2','user_id=12','user_email_id=22']){const before=Number((await c.query('SELECT version FROM dune_game_ticket_context'))[0][0].version);await c.query('UPDATE drsupport_ticket SET '+update+' WHERE ticket_id=42');equal(Number((await c.query('SELECT version FROM dune_game_ticket_context'))[0][0].version),before+1);}
 await seed();await request({action:'read'});injectAuditFailure=true;response=await request({action:'take',version:1});injectAuditFailure=false;equal(response.status,503);assert.ok(!(await response.text()).includes('private fixture'));checks++;equal(Number((await c.query('SELECT staff_id FROM drsupport_ticket WHERE ticket_id=42'))[0][0].staff_id),0);equal(Number((await call()).context_version),1);equal(await count('drsupport_thread_event'),0);equal(await count('dune_game_ticket_context_events'),0);equal(await count('account_audit'),0);
 // Real current-rank locks serialize revocation after a previously authorised transaction.
 await seed();const other=await connect();try{await c.beginTransaction();await call();let settled=false;const revoke=other.query("UPDATE cp_ranks SET permissions_json='[]' WHERE rank_key='case-support'").then(()=>{settled=true;});await new Promise(resolve=>setTimeout(resolve,80));equal(settled,false);await c.commit();await revoke;await denied(()=>call());}finally{await c.rollback();await other.end();}
 await seed();await c.query('UPDATE site_game_links SET linked_at=0 WHERE user_id=?',[actorAccount]);await denied(()=>call());
 console.log(JSON.stringify({passed:checks,isolation:'dedicated socket-only MySQL process, actual native table DDL, synthetic rows only',productionDataChanged:false,productionRoutesActivated:false,productionGrantsChanged:false}));
}finally{if(c)await c.end();else await bootstrap.end().catch(()=>{});}
