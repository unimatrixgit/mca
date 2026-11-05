// Run explicitly with DUNE_KIT_TEST_CONFIG pointing to an existing private database config.
// Every write is rewritten to connection-local temporary tables; no provider/game call.
import {createRequire} from 'node:module';import fs from 'node:fs';import assert from 'node:assert/strict';import {createKitStudio} from '../control-panel/kit-studio.mjs';
const require=createRequire(new URL('../node/mysql.mjs',import.meta.url)),mysql=require('mysql2/promise'),cfg=JSON.parse(fs.readFileSync(process.env.DUNE_KIT_TEST_CONFIG));const connection=await mysql.createConnection({host:cfg.host,user:cfg.username,password:cfg.password,database:cfg.database});let checks=0;const ok=(a,b)=>{assert.deepEqual(a,b);checks++};
const tables=['site_kit_blueprints','site_kit_listings','site_game_links','site_kit_design_unlocks','website_settings','site_clan_members','site_clans','account_audit'];for(const t of tables)await connection.query(`CREATE TEMPORARY TABLE test311_${t} LIKE ${t}`);
const rewrite=sql=>sql.replace(new RegExp('\\b('+tables.join('|')+')\\b','g'),name=>'test311_'+name);
const c={query:(sql,v)=>connection.query(rewrite(sql),v),execute:(sql,v)=>connection.execute(rewrite(sql),v),beginTransaction:()=>connection.beginTransaction(),commit:()=>connection.commit(),rollback:()=>connection.rollback(),end:()=>connection.end()};
const uuid='11111111-1111-4111-8111-111111111111',owner='22222222-2222-4222-8222-222222222222';

await c.execute('INSERT INTO site_game_links(user_id,minecraft_uuid,linked_at) VALUES(?,?,?)',[owner,uuid,1]);
let failAudit=false,actor={id:owner};const db={prepare(sql){let values=[];return{bind(...v){values=v;return this},async first(){return (await c.execute(sql,values))[0][0]},async all(){return{results:(await c.execute(sql,values))[0]}}}},async batch(statements){await c.beginTransaction();try{for(const s of statements)await s.run({execute(sql,values){if(failAudit&&sql.startsWith('INSERT INTO account_audit'))throw Error('injected-audit-failure');return c.execute(sql,values)}});await c.commit()}catch(e){await c.rollback();throw e}}};
const route=createKitStudio(db,{SERVER_CONTROL_TOKEN:'t'.repeat(64)},{authenticate:async()=>actor,access:async()=>({nodes:['web.menu.inventory']}),fetcher:async url=>Response.json({playerUuid:new URL(url).searchParams.get('uuid'),offices:[],chapters:['common','uncommon','rare','very_rare','legendary'].map((quality,index)=>({quality,index,unlocked:true}))})});
const post=b=>route(new Request('https://cp.dunerealms.com/api/kits/studio',{method:'POST',headers:{origin:'https://cp.dunerealms.com'},body:JSON.stringify(b)}));const rows=async t=>(await c.query('SELECT * FROM '+t))[0];
const base={action:'save_publish',quality:'common',kind:'personal_sale',version:0,name:'Desert test',color:'#385B49',lore:['Fixture'],listingVersion:0,shopType:'player',priceCents:10000};
try{
 ok((await post(base)).status,200);let b=(await rows('site_kit_blueprints'))[0],l=(await rows('site_kit_listings'))[0];ok(b.version,1);ok(l.blueprint_version,1);ok(l.published,1);
 ok((await post(base)).status,409);ok((await rows('site_kit_blueprints')).length,1);ok((await rows('site_kit_listings')).length,1);
 const edit={...base,version:1,listingVersion:1,name:'Changed test'};ok((await post({...edit,priceCents:1})).status,400);ok((await rows('site_kit_blueprints'))[0].name,'Desert test');
 ok((await post({...edit,listingVersion:0})).status,409);ok((await rows('site_kit_blueprints'))[0].version,1);
 failAudit=true;await assert.rejects(()=>post(edit),/injected-audit-failure/);checks++;failAudit=false;ok((await rows('site_kit_blueprints'))[0].version,1);ok((await rows('site_kit_listings'))[0].version,1);
 ok((await post({...edit,shopType:'clan',listingVersion:0})).status,403);ok((await rows('site_kit_blueprints'))[0].version,1);
 ok((await post(edit)).status,200);ok((await rows('site_kit_listings'))[0].blueprint_version,2);
 ok((await post({action:'archive',kind:'personal_sale',quality:'common',version:1})).status,409);
 ok((await post({action:'archive',kind:'personal_sale',quality:'common',version:2})).status,200);ok((await rows('site_kit_blueprints'))[0].active,0);ok((await rows('site_kit_listings'))[0].active,0);ok((await rows('site_kit_listings'))[0].published,0);
 ok((await post({...edit,version:3,listingVersion:3})).status,409);
 ok((await post({action:'restore',kind:'personal_sale',quality:'common',version:3})).status,200);ok((await rows('site_kit_blueprints'))[0].id,b.id);ok((await rows('site_kit_blueprints'))[0].active,1);ok((await rows('site_kit_listings'))[0].published,0);
 ok((await post({...edit,version:4,listingVersion:4})).status,200);ok((await rows('site_kit_blueprints'))[0].version,5);ok((await rows('site_kit_listings'))[0].blueprint_version,5);
 await c.execute('UPDATE site_kit_blueprints SET upgrade_json=?',[JSON.stringify({policy:'legendary-player-v1'})]);ok((await post({...edit,version:5,listingVersion:5})).status,409);ok((await rows('site_kit_blueprints'))[0].version,5);
 const snapshot=JSON.stringify(await rows('site_kit_blueprints'));actor={id:'33333333-3333-4333-8333-333333333333'};
 await c.execute('INSERT INTO site_game_links(user_id,minecraft_uuid,linked_at) VALUES(?,?,?)',[actor.id,'44444444-4444-4444-8444-444444444444',1]);
 ok((await post({action:'archive',kind:'personal_sale',quality:'common',version:5})).status,409);ok(JSON.stringify(await rows('site_kit_blueprints')),snapshot);
 actor={id:owner};
 const routine=(await connection.query('SHOW CREATE PROCEDURE dunegameplay.dune_kit_listing_lock'))[0][0]['Create Procedure'];
 ok(/b\.active\s*=\s*1/i.test(routine),true);ok(/l\.active\s*=\s*1/i.test(routine),true);ok(/l\.published\s*=\s*1/i.test(routine),true);ok(/FOR SHARE/i.test(routine),true);ok(/b\.version\s*=\s*l\.blueprint_version/i.test(routine),true);
 console.log(JSON.stringify({passed:checks,isolation:'connection-local temporary tables; real InnoDB rollback; no production writes'}));
}finally{await c.end()}

