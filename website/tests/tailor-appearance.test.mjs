import test from 'node:test';import assert from 'node:assert/strict';import {createHash} from 'node:crypto';
import {LocalD1} from './d1-adapter.mjs';import {createTailorAppearance} from '../node/tailor-appearance.mjs';
import {javaUuid,otherUuid,png,syntheticSkin} from './skin-fixtures.mjs';
import {createPanel} from '../control-panel/server.mjs';
const origin='https://cp.dunerealms.com',token='A'.repeat(43),otherToken='B'.repeat(43),digest=t=>createHash('sha256').update(t).digest('hex');
function request(path='/api/tailor/appearance',session=token,method='GET'){return new Request(origin+path,{method,headers:session?{Cookie:'__Host-dune_session='+session}:{}});}
async function fixture(){
 const db=new LocalD1("CREATE TABLE site_accounts(id TEXT,username TEXT,display_name TEXT,role TEXT,password_hash TEXT);CREATE TABLE site_sessions(token_hash TEXT,user_id TEXT,expires_at INTEGER);CREATE TABLE cp_account_ranks(user_id TEXT,rank_key TEXT);CREATE TABLE site_game_links(user_id TEXT,minecraft_uuid TEXT,linked_at INTEGER);");
 await db.prepare("INSERT INTO site_accounts VALUES('a','alice','Alice','user','active'),('b','bob','Bob','user','active')").run();
 for(const[t,id]of[[token,'a'],[otherToken,'b']])await db.prepare('INSERT INTO site_sessions VALUES(?,?,?)').bind(digest(t),id,Date.now()+60000).run();
 await db.prepare("INSERT INTO site_game_links VALUES('a',?,100),('b',?,200)").bind(javaUuid,otherUuid).run();return db;
}
test('metadata derives only the signed-in linked identity and returns model plus an opaque same-origin skin URL',async()=>{
 const db=await fixture();try{const calls=[];const handle=createTailorAppearance(db,{resolveSkin:async uuid=>{calls.push(uuid);return{...syntheticSkin,model:'slim'};}});
  const response=await handle(request()),data=await response.json();assert.deepEqual(Object.keys(data).sort(),['linked','model','skinUrl']);assert.equal(data.linked,true);assert.equal(data.model,'slim');assert(data.skinUrl.startsWith('/api/tailor/skin?v='));assert(!JSON.stringify(data).includes(javaUuid));assert(!JSON.stringify(data).includes('Alice'));
  const image=await handle(request(data.skinUrl));assert.equal(image.status,200);assert.deepEqual(Buffer.from(await image.arrayBuffer()),png);assert.equal(image.headers.get('content-type'),'image/png');assert.match(image.headers.get('cache-control'),/private.*no-store/);assert.equal(image.headers.get('vary'),'Cookie');assert.equal(image.headers.get('cross-origin-resource-policy'),'same-origin');assert.equal(image.headers.get('x-content-type-options'),'nosniff');assert.deepEqual(calls,[javaUuid,javaUuid]);
 }finally{db.close();}
});
test('unauthenticated, banned, expired and memorial sessions cannot obtain appearance or start a provider request',async()=>{
 for(const mode of ['anonymous','banned','expired','memorial']){const db=await fixture();try{
  if(mode==='banned')await db.prepare("UPDATE site_accounts SET password_hash='!banned' WHERE id='a'").run();if(mode==='expired')await db.prepare('UPDATE site_sessions SET expires_at=0').run();if(mode==='memorial')await db.prepare("INSERT INTO cp_account_ranks VALUES('a','memorial')").run();
  let calls=0;const handle=createTailorAppearance(db,{resolveSkin:async()=>{calls++;return syntheticSkin;}});assert.equal((await handle(request('/api/tailor/appearance',mode==='anonymous'?null:token))).status,401);assert.equal(calls,0);
 }finally{db.close();}}
});
test('caller-selected UUID, XUID, account and unsafe skin parameters are rejected without provider work',async()=>{
 const db=await fixture();try{let calls=0;const handle=createTailorAppearance(db,{resolveSkin:async()=>{calls++;return syntheticSkin;}});
  for(const path of ['/api/tailor/appearance?uuid='+otherUuid,'/api/tailor/appearance?xuid=9007199254740993','/api/tailor/appearance?user_id=b','/api/tailor/skin?v=x','/api/tailor/skin?v=x&v=y','/api/tailor/skin?v='+('a'.repeat(64)+'.'+'b'.repeat(64))+'&url=https://example.com'])assert.equal((await handle(request(path))).status,400);
  assert.equal((await handle(request('/api/tailor/appearance',token,'POST'))).status,405);assert.equal((await handle(request('/api/tailor/unknown'))).status,404);assert.equal(calls,0);
 }finally{db.close();}
});
test('unlinked and incomplete links return a neutral fallback; an unavailable linked skin is distinguished without exposing identity',async()=>{
 for(const mode of ['unlinked','incomplete','malformed','unavailable']){const db=await fixture();try{
  if(mode==='unlinked')await db.prepare("DELETE FROM site_game_links WHERE user_id='a'").run();if(mode==='incomplete')await db.prepare("UPDATE site_game_links SET linked_at=NULL WHERE user_id='a'").run();if(mode==='malformed')await db.prepare("UPDATE site_game_links SET minecraft_uuid='bad' WHERE user_id='a'").run();
  let calls=0;const handle=createTailorAppearance(db,{resolveSkin:async()=>{calls++;return null;}}),data=await(await handle(request())).json();assert.deepEqual(data,{linked:mode==='unavailable',model:'default',skinUrl:null});assert.equal(calls,mode==='unavailable'?1:0);
 }finally{db.close();}}
});
test('old image URLs reject another account, unlink/relink and model/content changes instead of showing a previous skin',async()=>{
 const db=await fixture();try{let calls=0,skin=syntheticSkin;const handle=createTailorAppearance(db,{resolveSkin:async()=>{calls++;return skin;}}),data=await(await handle(request())).json();
  assert.equal((await handle(request(data.skinUrl,otherToken))).status,409);assert.equal(calls,1);
  await db.prepare("UPDATE site_game_links SET linked_at=101 WHERE user_id='a'").run();assert.equal((await handle(request(data.skinUrl))).status,409);assert.equal(calls,1);
  const current=await(await handle(request())).json();assert.notEqual(current.skinUrl,data.skinUrl);skin={...skin,hash:'e'.repeat(64)};assert.equal((await handle(request(current.skinUrl))).status,409);
  await db.prepare("DELETE FROM site_game_links WHERE user_id='a'").run();assert.equal((await handle(request(current.skinUrl))).status,409);
 }finally{db.close();}
});
test('logout, ban, account switch and unlink/relink while remote work is pending never release the old result',async()=>{
 for(const change of ['logout','ban','unlink','relink','switch']){const db=await fixture();try{
  let release,started;const gate=new Promise(r=>release=r),waiting=new Promise(r=>started=r);const handle=createTailorAppearance(db,{resolveSkin:async()=>{started();await gate;return syntheticSkin;}});const result=handle(request());await waiting;
  if(change==='logout')await db.prepare('DELETE FROM site_sessions WHERE token_hash=?').bind(digest(token)).run();if(change==='ban')await db.prepare("UPDATE site_accounts SET password_hash='!banned' WHERE id='a'").run();if(change==='unlink')await db.prepare("DELETE FROM site_game_links WHERE user_id='a'").run();if(change==='relink')await db.prepare("UPDATE site_game_links SET minecraft_uuid=?,linked_at=102 WHERE user_id='a'").bind(otherUuid).run();if(change==='switch')await db.prepare("UPDATE site_sessions SET user_id='b' WHERE token_hash=?").bind(digest(token)).run();
  release();const response=await result;assert.equal(response.status,['logout','ban'].includes(change)?401:409);assert(!(await response.text()).includes('skinUrl'));
 }finally{db.close();}}
});
test('authenticated preview request windows are bounded and recover after their deadline',async()=>{
 const db=await fixture();try{let time=1000;const handle=createTailorAppearance(db,{now:()=>time,resolveSkin:async()=>null});for(let i=0;i<60;i++)assert.equal((await handle(request())).status,200);assert.equal((await handle(request())).status,429);time=61001;assert.equal((await handle(request())).status,200);}finally{db.close();}
});
test('the real CP router mounts appearance behind the existing first-login gate',async()=>{
 const db=await fixture();try{
  await db.prepare('CREATE TABLE site_first_login(user_id TEXT,password_changed INTEGER,created_at INTEGER)').run();await db.prepare('CREATE TABLE site_contact_emails(user_id TEXT,address TEXT,verified_at INTEGER)').run();await db.prepare('CREATE TABLE site_terms_acceptances(user_id TEXT,version TEXT,accepted_at INTEGER)').run();
  let calls=0;const panel=createPanel(db,{}, {tailorAppearance:{resolveSkin:async()=>{calls++;return syntheticSkin;}}});
  const response=await panel(request());assert.equal(response.status,200);assert.equal((await response.json()).linked,true);assert.equal(calls,1);
  await db.prepare("INSERT INTO site_first_login VALUES('a',0,1)").run();const gated=await panel(request());assert.equal(gated.status,428);assert.equal(calls,1);assert.equal((await gated.json()).firstLogin.passwordRequired,true);
 }finally{db.close();}
});
