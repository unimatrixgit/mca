// Explicit opt-in only. All SQL targets connection-local temporary tables.
// MySQL temporary tables cannot have foreign keys: that DDL constraint is checked at deployment.
import fs from 'node:fs';import assert from 'node:assert/strict';import {createRequire} from 'node:module';
import {mysqlSql} from '../node/mysql.mjs';
import {initializeClanShop,createClanShop} from '../control-panel/clan-shop.mjs';
import {clanKitCatalogue} from '../node/clan-kit-catalogue.mjs';
const require=createRequire(new URL('../node/mysql.mjs',import.meta.url)),mysql=require('mysql2/promise');
const cfg=JSON.parse(fs.readFileSync(process.env.DUNE_KIT_TEST_CONFIG));
const connection=await mysql.createConnection({host:cfg.host,user:cfg.username,password:cfg.password,database:cfg.database});
const tables=['site_clan_kits','site_clan_kit_designs','site_clan_kit_quality_unlocks','site_clans','site_clan_members','site_game_links','site_accounts','cp_account_ranks','cp_ranks','site_kit_design_unlocks','account_audit','website_settings'];
const rewrite=sql=>mysqlSql(sql).replace(new RegExp('\\b('+tables.join('|')+')\\b','g'),name=>'qa312_'+name)
 .replace(/CREATE TABLE IF NOT EXISTS/g,'CREATE TEMPORARY TABLE IF NOT EXISTS').replace(/,FOREIGN KEY\(clan_id\) REFERENCES qa312_site_clans\(id\)/g,'');
let auditFailure=false,beforeBatch=null,checks=0;const equal=(a,b)=>{assert.deepEqual(a,b);checks++;};
const execute=async(sql,values=[])=>connection.execute(rewrite(sql),values);
const db={prepare(sql){let values=[];return{bind(...args){values=args;return this;},async first(){return(await execute(sql,values))[0][0]||null;},async all(){return{results:(await execute(sql,values))[0]};},async run(c){const[result]=c?await c.execute(sql,values):await execute(sql,values);return{meta:{changes:result.affectedRows}};}};},async batch(statements){await beforeBatch?.();beforeBatch=null;await connection.beginTransaction();try{for(const statement of statements)await statement.run({execute(sql,values){if(auditFailure&&sql.startsWith('INSERT INTO account_audit'))throw Error('injected audit failure');return execute(sql,values);}});await connection.commit();}catch(error){await connection.rollback();throw error;}}};
const owner='11111111-1111-4111-8111-111111111111',uuid='22222222-2222-4222-8222-222222222222',clan='33333333-3333-4333-8333-333333333333',original='44444444-4444-4444-8444-444444444444';
const app=createClanShop(db,{}, {authenticate:async()=>({id:owner}),access:async()=>({nodes:['web.menu.game']})});
const post=body=>app(new Request('https://cp.dunerealms.com/api/clans/shop',{method:'POST',headers:{Origin:'https://cp.dunerealms.com'},body:JSON.stringify(body)}));
const design={quality:'uncommon',name:'Isolated House design',color:'#385B49',lore:['Temporary fixture only.'],version:7,shopPriceDunemarks:300};
try{
 await connection.query("SET SESSION sql_mode='STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION,NO_BACKSLASH_ESCAPES'");
 for(const table of tables.filter(t=>!['site_clan_kit_designs','site_clan_kit_quality_unlocks'].includes(t)))await connection.query('CREATE TEMPORARY TABLE qa312_'+table+' LIKE '+table);
 await execute('INSERT INTO site_accounts(id,username,display_name,password_salt,password_hash,role,created_at) VALUES(?,?,?,?,?,?,?)',[owner,'qa312','QA temporary','','','owners',1]);
 await execute('INSERT INTO site_clans(id,name,name_key,tag,description,owner_id,active,version,created_at,updated_at) VALUES(?,?,?,?,?,?,1,1,1,1)',[clan,'QA house','qa-house','QA312','Temporary fixture',owner]);
 await execute('INSERT INTO site_clan_members(user_id,minecraft_uuid,clan_id,clan_role,joined_at) VALUES(?,?,?,?,1)',[owner,uuid,clan,'owner']);
 await execute('INSERT INTO site_game_links(user_id,minecraft_uuid,linked_at) VALUES(?,?,1)',[owner,uuid]);
 await execute('INSERT INTO cp_ranks(rank_key,label,rank_level,kind,permissions_json,version) VALUES(?,?,100,?,?,1)',['owners','Owner','access','[]']);
 for(const quality of ['common','uncommon','rare','very_rare','legendary'])await execute('INSERT INTO site_kit_design_unlocks(user_id,quality,granted_by,granted_at) VALUES(?,?,?,1)',[owner,quality,owner]);
 await execute('INSERT INTO site_clan_kits(id,clan_id,name,color,lore_json,version,updated_at,updated_by,shop_price_cents) VALUES(?,?,?,?,?,7,1,?,25000)',[original,clan,'Original saved design','#385B49','[]',owner]);
 const initial=await db.prepare('SELECT * FROM site_clan_kits').first();await initializeClanShop(db);await initializeClanShop(db);equal(await db.prepare('SELECT * FROM site_clan_kits').first(),initial);equal((await db.prepare('SELECT id,version,shop_price_cents FROM site_clan_kit_designs').first()),{id:original,version:7,shop_price_cents:25000});
 equal((await post(design)).status,200);equal((await post(design)).status,409);
 const legacy=await db.prepare('SELECT * FROM site_clan_kits').first();equal(legacy.version,8);
 for(const[quality,price]of [['common',100],['rare',400],['very_rare',800],['legendary',1600]])equal((await post({...design,version:0,quality,shopPriceDunemarks:price,...(quality==='common'?{}:{itemNames:{helmet:quality+' hood'}})})).status,200);
 equal(await db.prepare('SELECT * FROM site_clan_kits').first(),legacy);
 const catalogue=await clanKitCatalogue(db);equal(catalogue.length,5);equal(catalogue.map(row=>row.quality),['common','uncommon','rare','very_rare','legendary']);equal(catalogue.map(row=>row.priceCents),[10000,30000,40000,80000,160000]);equal(catalogue.every(row=>row.purchaseAvailable===false),true);equal(catalogue.find(row=>row.quality==='uncommon').listingId,original);
 auditFailure=true;await assert.rejects(()=>post({...design,version:8}),/injected audit failure/);checks++;auditFailure=false;equal(await db.prepare('SELECT * FROM site_clan_kits').first(),legacy);equal((await db.prepare("SELECT version FROM site_clan_kit_designs WHERE quality='uncommon'").first()).version,8);
 beforeBatch=()=>execute('DELETE FROM site_kit_design_unlocks WHERE quality=?',['legendary']);equal((await post({...design,quality:'legendary',version:1,shopPriceDunemarks:1600})).status,409);equal((await db.prepare("SELECT version FROM site_clan_kit_designs WHERE quality='legendary'").first()).version,1);
 beforeBatch=()=>execute('UPDATE site_clan_members SET clan_role=?',['member']);equal((await post({...design,version:8})).status,409);equal(await db.prepare('SELECT * FROM site_clan_kits').first(),legacy);
 console.log(JSON.stringify({passed:checks,isolation:'real InnoDB transactions; connection-local temporary tables; no production writes',foreignKeys:'not exercised by temporary-table QA'}));
}finally{await connection.end();}
