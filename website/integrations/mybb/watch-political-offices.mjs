import {readFile,unlink,chmod}from 'node:fs/promises';
import {createServer}from 'node:net';
import {createRequire}from 'node:module';
import {spawn}from 'node:child_process';
import{officesFor}from './political-offices.mjs';
const require=createRequire('/home/dunerealms/apps/dunerealms/current/package.json');
const pg=require('pg'),mysql=require('mysql2/promise');
const{parse}=await import('/home/dunerealms/apps/integrations/politics/node_modules/yaml/dist/index.js');
const settings=JSON.parse(await readFile('/home/dunerealms/.config/integrations.json','utf8'));
const central=mysql.createPool({host:process.env.DB_HOST,user:process.env.DB_USER,password:process.env.DB_PASSWORD,database:process.env.DB_NAME,connectionLimit:2});
const forum=mysql.createPool({host:settings.host,user:settings.username,password:settings.password,database:settings.database,connectionLimit:2});
let game,busy=false,pending=false,expiry,reconnect,lastFingerprint,latestFacts=new Map(),gameEventTimer,lastGameEvent='0';
async function apply(){
 if(busy){pending=true;return;}busy=true;
 try{
  const{rows:[row]}=await game.query('SELECT yaml FROM dune_politics WHERE id=1');
  if(!row||typeof row.yaml!=='string')throw Error('Political state unavailable');
  const state=parse(row.yaml,{maxAliasCount:0,uniqueKeys:true})||{};
  const{rows:players}=await game.query('SELECT uuid,faction FROM dune_players');
  const now=Date.now(),facts=new Map(players.map(p=>[p.uuid,officesFor(state,p,now)]));
  latestFacts=facts;
  const fingerprint=JSON.stringify([...facts].sort(([a],[b])=>a.localeCompare(b)));
  if(fingerprint===lastFingerprint)return;
  const[links]=await central.execute('SELECT a.id,l.minecraft_uuid FROM site_accounts a LEFT JOIN site_game_links l ON l.user_id=a.id');
  const c=await forum.getConnection();try{await c.beginTransaction();
   for(const link of links){const fact=facts.get(String(link.minecraft_uuid||'').toLowerCase());
    await c.execute('INSERT INTO drforum_dune_political_roles(website_id,roles_json,expires_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE roles_json=VALUES(roles_json),expires_at=VALUES(expires_at)',[link.id,JSON.stringify(fact?.roles||[]),Math.floor((fact?.expiresAt||now)/1000)]);
   }
   await c.commit();
  }catch(error){await c.rollback();throw error;}finally{c.release();}
  await new Promise((resolve,reject)=>{const p=spawn('/usr/bin/php',['/home/dunerealms/apps/integrations/mybb/apply-political-groups.php'],{stdio:'ignore'});p.once('error',reject);p.once('exit',code=>code===0?resolve():reject(Error('Forum apply failed')));});
  clearTimeout(expiry);expiry=setTimeout(()=>void apply().catch(report),(Math.floor(now/604800000)+1)*604800000-now+1000);
  console.log('Political office snapshot reconciled.');
  lastFingerprint=fingerprint;
 }finally{busy=false;if(pending){pending=false;void apply().catch(report);}}
}
function report(){console.error('Political office synchronization unavailable; no new offices granted.');}
function reconnectLater(){if(!reconnect)reconnect=setTimeout(()=>{reconnect=null;void connect();},10000);}
async function connect(){
 try{
  if((process.env.DUNE_INVENTORY_DATABASE_URL||'').startsWith('mysql:')){
   const{openGameMysqlPool}=await import('/home/dunerealms/apps/dunerealms/current/node/game-mysql-pool.mjs');
   game=await openGameMysqlPool(process.env.DUNE_INVENTORY_DATABASE_URL);await apply();
   // Observe committed event IDs, not ranks or permission tables. Core appends
   // only actual political/identity changes in the same transaction as its save.
   async function events(){
    try{const{rows}=await game.query({text:'SELECT id,topic FROM dune_game_events WHERE id>$1 ORDER BY id LIMIT 100',values:[lastGameEvent]});
     if(rows.some(row=>row.topic==='politics'))await apply();
     if(rows.length)lastGameEvent=String(rows.at(-1).id);
    }catch{report();}
    gameEventTimer=setTimeout(()=>void events(),2000);
   }
   void events();return;
  }
  game=new pg.Client({connectionString:process.env.DUNE_INVENTORY_DATABASE_URL,connectionTimeoutMillis:5000});await game.connect();
  game.on('notification',()=>void apply().catch(report));game.on('error',report);game.once('end',reconnectLater);
  await game.query('LISTEN dune_political_change');await apply();
 }catch{report();try{await game?.end();}catch{}reconnectLater();}
}
await connect();
// Verified forum requests refresh only their central UUID link, from cached game facts.
// This also covers freshly created forum accounts and account unlink/relink without polling.
const socket='/home/dunerealms/apps/integrations/politics/office-sync.sock';
try{await unlink(socket);}catch(error){if(error.code!=='ENOENT')throw error;}
createServer(client=>{let input='';client.on('error',()=>{});client.setTimeout(3000,()=>client.destroy());client.on('data',async chunk=>{
 input+=chunk.toString();if(input.length>80){client.destroy();return;}if(!input.includes('\n'))return;
 const id=input.trim();if(!/^[a-f0-9-]{36}$/i.test(id)){client.destroy();return;}
 try{const[links]=await central.execute('SELECT minecraft_uuid FROM site_game_links WHERE user_id=?',[id]);const fact=latestFacts.get(String(links[0]?.minecraft_uuid||'').toLowerCase()),now=Date.now();
  await forum.execute('INSERT INTO drforum_dune_political_roles(website_id,roles_json,expires_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE roles_json=VALUES(roles_json),expires_at=VALUES(expires_at)',[id,JSON.stringify(fact&&fact.expiresAt>now?fact.roles:[]),Math.floor((fact?.expiresAt||now)/1000)]);client.end('OK\n');
 }catch{client.end('ERROR\n');}
 });}).listen(socket,()=>void chmod(socket,0o600));
