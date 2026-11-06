// Run only after game-ticket-context-mysql.integration.mjs in its socket-only disposable server.
import assert from 'node:assert/strict';import fs from 'node:fs';import path from 'node:path';import {createRequire} from 'node:module';
import {decideNativeAppeal} from '../control-panel/native-appeal-decision.mjs';
const socket=process.env.DUNE_CASE_TEST_SOCKET;assert.match(socket||'',/^\/tmp\/dune-case324-qa-[a-zA-Z0-9_-]+\/mysql\.sock$/);const qa=path.dirname(socket);assert.equal(fs.readFileSync(path.join(qa,'.codex-qa324'),'utf8'),'isolated-casework-324');
const require=createRequire(new URL('../node/mysql.mjs',import.meta.url)),mysql=require('mysql2/promise'),connect=()=>mysql.createConnection({socketPath:socket,user:'root',database:'website'}),c=await connect(),other=await connect();
let beforeBatch=null,failAudit=false,checks=0,calls=0;const equal=(a,b)=>{assert.deepEqual(a,b);checks++;};
const execute=(sql,values)=>{if(failAudit&&sql.startsWith('INSERT INTO moderation_events'))throw Error('isolated audit failure');return c.execute(sql,values);};
const db={prepare(sql){let values=[];return{bind(...args){values=args;return this;},async first(){return(await execute(sql,values))[0][0]||null;},async run(transaction){const[result]=transaction?await transaction.execute(sql,values):await execute(sql,values);return{meta:{changes:result.affectedRows}};}};},async batch(statements){const hook=beforeBatch;beforeBatch=null;await hook?.();await c.beginTransaction();try{const results=[];for(const statement of statements)results.push(await statement.run({execute}));await c.commit();return results;}catch(error){await c.rollback();throw error;}}};
const banId='55555555-5555-4555-8555-555555555555',appealId='66666666-6666-4666-8666-666666666666',actor='11111111-1111-4111-8111-111111111111',creator='22222222-2222-4222-8222-222222222222';
const options={user:{id:actor,username:'Isolated reviewer'},access:{nodes:['web.bans.manage','web.tickets.staff','web.tickets.appeals']},ban:{id:banId,source_key:'isolated:case324',native_key:'arrakis:fixture',created_text:'fixture-issued'},body:{action:'approve',ticketId:42,reason:'Isolated review completed'}};
async function seed(status='pending'){
 await c.query('DELETE FROM native_ban_appeals');await c.query('DELETE FROM moderation_events');await c.query('DELETE FROM moderation_bans');
 await c.execute('INSERT INTO moderation_bans(id,source_key,native_key,minecraft_uuid,player_name,banned_by,reason,scope,created_text,expires_text,active,first_seen,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,1,1,1)',[banId,options.ban.source_key,options.ban.native_key,'77777777-7777-4777-8777-777777777777','Isolated fixture','Fixture issuer','Fixture only','arrakis','fixture-issued','forever']);
 await c.execute('INSERT INTO native_ban_appeals(id,ban_id,ticket_id,user_id,status,ban_snapshot,opened_at) VALUES(?,?,42,?,?,?,1)',[appealId,banId,creator,status,'{}']);
}
const status=async()=>(await c.query('SELECT status FROM native_ban_appeals WHERE id=?',[appealId]))[0][0].status;
const active=async()=>Number((await c.query('SELECT active FROM moderation_bans WHERE id=?',[banId]))[0][0].active);
const events=async()=>Number((await c.query('SELECT COUNT(*) n FROM moderation_events'))[0][0].n);
const ack=async(url,init)=>{calls++;equal(url,'http://127.0.0.1:15433/bans/action');const body=JSON.parse(init.body);equal(body.key,options.ban.native_key);equal(body.created,'fixture-issued');return Response.json({confirmed:true,absent:true,key:body.key});};
const decide=(action='approve',fetcher=ack)=>decideNativeAppeal(db,{SERVER_CONTROL_TOKEN:'fixture'.padEnd(64,'x')},{...options,body:{...options.body,action},fetcher});
try{
 const[info]=await c.query('SELECT @@datadir AS dir,@@skip_networking AS isolated');assert.equal(path.resolve(info[0].dir),path.join(qa,'data'));assert.equal(Number(info[0].isolated),1);
 await seed();beforeBatch=()=>other.execute("UPDATE native_ban_appeals SET status='approving' WHERE id=?",[appealId]);equal((await decide('deny')).status,409);equal(await status(),'approving');equal(await events(),0);equal(await active(),1);equal(calls,0);
 await seed('denied');beforeBatch=()=>other.execute("UPDATE native_ban_appeals SET status='approving' WHERE id=?",[appealId]);equal((await decide('reopen')).status,409);equal(await status(),'approving');equal(await events(),0);equal(calls,0);
 await seed();beforeBatch=async()=>{await other.execute("UPDATE native_ban_appeals SET status='approved' WHERE id=?",[appealId]);await other.execute('UPDATE moderation_bans SET active=0,updated_at=2 WHERE id=?',[banId]);};equal((await decide()).status,409);equal(await status(),'approved');equal(Number((await c.query('SELECT updated_at FROM moderation_bans WHERE id=?',[banId]))[0][0].updated_at),2);equal(await events(),0);
 await seed();failAudit=true;await assert.rejects(()=>decide(),/isolated audit failure/);checks++;failAudit=false;equal(await status(),'approving');equal(await active(),1);equal(await events(),0);equal((await decide()).status,200);equal(await status(),'approved');equal(await active(),0);equal(await events(),1);const before=calls;equal((await decide()).status,200);equal(calls,before);equal(await events(),1);
 await seed();equal((await decide('approve',async()=>{throw Error('fixture outage')})).status,503);equal(await status(),'pending');equal(await active(),1);equal(await events(),1);
 await seed();equal((await decide('deny')).status,200);equal(await status(),'denied');equal(await active(),1);equal((await decide('reopen')).status,200);equal(await status(),'pending');const count=await events();equal((await decide('reopen')).status,200);equal(await events(),count);
 console.log(JSON.stringify({passed:checks,isolation:'same dedicated socket-only MySQL process; real second-connection races and rollback',productionDataChanged:false,realGameRequests:0}));
}finally{await c.end();await other.end();}
