import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {LocalD1} from './d1-adapter.mjs';
import {createIntegrationSSO} from '../node/integration-sso.mjs';
const secret='integration-test-secret-over-32-characters',token='x'.repeat(43),ref=createHash('sha256').update(token).digest('hex');
test('SSO binds fixed callbacks to a live account session and consumes codes once',async()=>{
 const db=new LocalD1(`CREATE TABLE site_profiles(user_id TEXT,bio TEXT,public_info TEXT,socials_json TEXT,avatar_key TEXT);CREATE TABLE cp_account_ranks(user_id TEXT,rank_key TEXT);CREATE TABLE website_badges(user_id TEXT,badge_key TEXT,earned_at INTEGER);CREATE TABLE site_account_policies(user_id TEXT PRIMARY KEY,service_uuid TEXT,game_link_exempt INTEGER);CREATE TABLE site_age_declarations(user_id TEXT PRIMARY KEY,age_band TEXT,declared_at INTEGER);CREATE TABLE site_accounts(id TEXT,username TEXT,display_name TEXT,role TEXT);CREATE TABLE site_sessions(token_hash TEXT,user_id TEXT,expires_at INTEGER);CREATE TABLE site_game_links(user_id TEXT,minecraft_uuid TEXT);CREATE TABLE cp_ranks(rank_key TEXT,label TEXT,rank_level INTEGER,kind TEXT,permissions_json TEXT,version INTEGER);CREATE TABLE integration_sso_codes(code_hash TEXT PRIMARY KEY,user_id TEXT,session_ref TEXT,client_id TEXT,expires_at INTEGER);`);
 try{
 await db.prepare('INSERT INTO site_accounts VALUES(?,?,?,?)').bind('user-1','traveller','Traveller','user').run();await db.prepare('INSERT INTO site_sessions VALUES(?,?,?)').bind(ref,'user-1',Date.now()+100000).run();
 await db.prepare('INSERT INTO website_badges VALUES(?,?,?)').bind('user-1','alpha-tester',1).run();await db.prepare('INSERT INTO website_badges VALUES(?,?,?)').bind('user-1','season-tavren-ii',1).run();
 await db.prepare('INSERT INTO site_profiles VALUES(?,?,?,?,?)').bind('user-1','Desert streams','Live weekends',JSON.stringify({twitch:'https://twitch.tv/caravan'}),null).run();
 const sso=createIntegrationSSO(db,{INTEGRATION_SSO_SECRET:secret}),url='https://cp.dunerealms.com/sso/authorize?client=mybb&state='+'a'.repeat(32);
 assert.equal((await sso(new Request(url))).status,302);
 assert.equal((await sso(new Request(url.replace('mybb','evil')))).status,400);
 const response=await sso(new Request(url,{headers:{Cookie:'__Host-dune_session='+token}})),callback=new URL(response.headers.get('Location'));
 assert.equal(callback.origin,'https://forums.dunerealms.com');assert.equal(callback.pathname,'/dune-sso.php');
 const req=(path,body,key=secret)=>new Request('https://cp.dunerealms.com'+path,{method:'POST',headers:{Authorization:'Bearer '+key,'Content-Type':'application/json'},body:JSON.stringify(body)});
 const payload={code:callback.searchParams.get('code'),client:'mybb'};
 assert.equal((await sso(req('/internal/integrations/redeem',payload,'wrong'))).status,401);
 assert.equal((await sso(req('/internal/integrations/redeem',{...payload,client:'osticket'}))).status,401);
 const redeemed=await sso(req('/internal/integrations/redeem',payload));assert.equal(redeemed.status,200);const profile=await redeemed.json();assert.equal(profile.id,'user-1');assert.equal(profile.publicProfile.bio,'Desert streams');assert.equal(profile.publicProfile.socials.twitch,'https://twitch.tv/caravan');assert(!Object.hasOwn(profile.publicProfile,'birthday'));assert.deepEqual(profile.ranks,['user']);assert.equal(profile.rankLabel,'User');assert.equal(profile.rankLevel,10);assert.deepEqual(profile.communityBadges,['alpha-tester','season-tavren-ii']);
 assert.equal((await sso(req('/internal/integrations/redeem',payload))).status,401);
 const shopRedirect=await sso(new Request(url.replace('client=mybb','client=shop')+'&redirect_uri=https://evil.invalid',{headers:{Cookie:'__Host-dune_session='+token}}));
 const shopCallback=new URL(shopRedirect.headers.get('Location'));assert.equal(shopCallback.origin,'https://cp.dunerealms.com');assert.equal(shopCallback.pathname,'/store/dune-login');
 const shopCode={client:'shop',code:shopCallback.searchParams.get('code')};assert.equal((await sso(req('/internal/integrations/redeem',{...shopCode,client:'mybb'}))).status,401);assert.equal((await sso(req('/internal/integrations/redeem',shopCode))).status,200);assert.equal((await sso(req('/internal/integrations/redeem',shopCode))).status,401);
 assert.equal((await sso(req('/internal/integrations/identity',{sessionRef:ref}))).status,200);
 await db.prepare('INSERT INTO site_age_declarations VALUES(?,?,?)').bind('user-1','under15',Date.now()).run();
 const restricted=await sso(req('/internal/integrations/identity',{sessionRef:ref}));assert.equal((await restricted.json()).forumAllowed,false);
 await db.prepare('INSERT INTO site_account_policies VALUES(?,?,?)').bind('user-1','12345678-1234-4234-9234-123456789012',1).run();
 const service=await (await sso(req('/internal/integrations/identity',{sessionRef:ref}))).json();
 assert.equal(service.gameLinkExempt,true);assert.equal(service.linked,false);assert.equal(service.forumAllowed,false);
 await db.prepare("UPDATE site_accounts SET role='web_support' WHERE id='user-1'").run();const staff=await(await sso(req('/internal/integrations/identity',{sessionRef:ref}))).json();assert.deepEqual(staff.communityBadges,[]);assert.deepEqual(staff.ranks,['web_support']);
 await db.prepare('DELETE FROM site_sessions').run();assert.equal((await sso(req('/internal/integrations/identity',{sessionRef:ref}))).status,401);
 }finally{db.close();}
});

