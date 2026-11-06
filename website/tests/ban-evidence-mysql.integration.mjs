// Opt in with DUNE_KIT_TEST_CONFIG. Writes target connection-local temporary tables only.
import fs from 'node:fs';import assert from 'node:assert/strict';import {createRequire} from 'node:module';import {randomUUID} from 'node:crypto';
import {createBanEvidence} from '../control-panel/ban-evidence.mjs';
const require=createRequire(new URL('../node/mysql.mjs',import.meta.url)),mysql=require('mysql2/promise'),cfg=JSON.parse(fs.readFileSync(process.env.DUNE_KIT_TEST_CONFIG));
const connection=await mysql.createConnection({host:cfg.host,user:cfg.username,password:cfg.password,database:cfg.database});
const tables=['moderation_bans','moderation_evidence','moderation_events'];
const rewrite=sql=>sql.replace(/\b(moderation_bans|moderation_evidence|moderation_events)\b/g,name=>'qa332_'+name);
let auditFailure=false,writer=true,actor={id:'22222222-2222-4222-8222-222222222222',username:'Temporary fixture'},checks=0;
const equal=(a,b)=>{assert.deepEqual(a,b);checks++;};
const execute=(sql,values=[])=>connection.execute(rewrite(sql),values);
const db={prepare(sql){let values=[];return{bind(...args){values=args;return this;},async first(){return(await execute(sql,values))[0][0]||null;},async all(){return{results:(await execute(sql,values))[0]};},async run(c){const[result]=c?await c.execute(sql,values):await execute(sql,values);return{meta:{changes:result.affectedRows}};}};},async batch(statements){await connection.beginTransaction();try{for(const statement of statements)await statement.run({execute(sql,values){if(auditFailure&&sql.startsWith('INSERT INTO moderation_events'))throw Error('injected audit failure');return execute(sql,values);}});await connection.commit();}catch(error){await connection.rollback();throw error;}}};
const banId='11111111-1111-4111-8111-111111111111',origin='https://cp.dunerealms.com';
const app=createBanEvidence(db,{}, {authenticate:async()=>actor,can:async(_,node)=>node!=='web.bans.evidence'||writer});
const base={id:randomUUID(),banId,title:'Temporary witness statement',notes:'Fixture context ✓',sourceUrl:'',file:null};
const post=(body,site=origin)=>app(new Request(origin+'/api/ban-evidence',{method:'POST',headers:{Origin:site,'Content-Type':'application/json'},body:JSON.stringify(body)}));
const count=async table=>Number((await db.prepare('SELECT COUNT(*) n FROM '+table).first()).n);
try{
 for(const table of tables)await connection.query('CREATE TEMPORARY TABLE qa332_'+table+' LIKE '+table);
 await execute('INSERT INTO moderation_bans(id,source_key,native_key,minecraft_uuid,player_name,banned_by,reason,scope,created_text,expires_text,active,first_seen,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,0,1,1)',[banId,'fixture:332','arrakis:fixture','33333333-3333-4333-8333-333333333333','Temporary fixture','Fixture staff','Temporary retained ban','arrakis','2026-11-06','forever']);
 equal((await post(base)).status,201);equal(await count('moderation_evidence'),1);equal(await count('moderation_events'),1);
 equal((await post(base)).status,200);equal(await count('moderation_evidence'),1);equal((await post({...base,notes:'Changed payload'})).status,409);
 const originalActor=actor;actor={id:'44444444-4444-4444-8444-444444444444',username:'Different fixture'};equal((await post(base)).status,409);actor=originalActor;
 equal((await post({...base,id:randomUUID()},'https://other.invalid')).status,403);writer=false;equal((await post({...base,id:randomUUID()})).status,403);writer=true;
 auditFailure=true;const retry={...base,id:randomUUID(),title:'Audit rollback fixture'};await assert.rejects(()=>post(retry),/injected audit failure/);checks++;auditFailure=false;equal(await count('moderation_evidence'),1);equal(await count('moderation_events'),1);equal((await post(retry)).status,201);equal(await count('moderation_evidence'),2);equal(await count('moderation_events'),2);
 const bytes=Buffer.alloc(5*1024*1024);Buffer.from('89504e470d0a1a0a','hex').copy(bytes);const upload={...base,id:randomUUID(),title:'Maximum upload fixture',notes:'',file:{name:'../../proof\r\n.png',base64:bytes.toString('base64')}};equal((await post(upload)).status,201);
 const saved=await db.prepare('SELECT actor_id,file_size,file_name,mime,sha256 FROM moderation_evidence WHERE id=?').bind(upload.id).first();equal(saved.actor_id,actor.id);equal(saved.file_size,5*1024*1024);equal(saved.file_name,'proof.png');equal(saved.mime,'image/png');assert.match(saved.sha256,/^[a-f0-9]{64}$/);checks++;
 const response=await app(new Request(origin+'/api/ban-evidence/file?id='+upload.id));equal(response.status,200);equal(response.headers.get('cache-control'),'private, no-store');equal(Buffer.from(await response.arrayBuffer()).length,bytes.length);
 equal(await count('moderation_evidence'),3);equal(await count('moderation_events'),3);
 console.log(JSON.stringify({passed:checks,isolation:'real InnoDB transactions; connection-local temporary tables; no production writes',maximumUploadBytes:bytes.length,realBansChanged:0}));
}finally{await connection.end();}
