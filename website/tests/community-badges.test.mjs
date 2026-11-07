import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {createCommunityRewards} from '../node/community-rewards.mjs';
import {LocalD1} from './d1-adapter.mjs';
import {initializeBadges,badgesFor,alphaGrant,seasonGrant,awardDedicated} from '../node/community-badges.mjs';
const schema="CREATE TABLE site_accounts(id TEXT PRIMARY KEY,role TEXT,username TEXT,display_name TEXT,password_hash TEXT DEFAULT 'fixture-hash');"+readFileSync(new URL('../node/schema.sql',import.meta.url),'utf8').split('CREATE TABLE IF NOT EXISTS site_game_links')[1].replace(/^/,'CREATE TABLE IF NOT EXISTS site_game_links').replace(/ ENGINE=InnoDB DEFAULT CHARSET=utf8mb4/g,'');
test('Alpha excludes staff, season includes everyone, closed phases do not grant new accounts',async()=>{
 const db=new LocalD1(schema);try{
  for(const [id,role]of[['member','user'],['owner','owners'],['mixed','user']])await db.prepare('INSERT INTO site_accounts(id,role) VALUES(?,?)').bind(id,role).run();
  await db.prepare("INSERT INTO cp_account_ranks(user_id,rank_key) VALUES('mixed','web_support')").run();
  await initializeBadges(db);await initializeBadges(db);
  assert.deepEqual(new Set(await badgesFor(db,'member')),new Set(['alpha-tester','season-tavren-ii']));
  for(const id of ['owner','mixed'])assert.deepEqual(await badgesFor(db,id),['season-tavren-ii']);
  await db.prepare("UPDATE website_settings SET setting_value='0' WHERE setting_key IN ('alpha-badge-active','season-tavren-ii-active')").run();
  await db.prepare("INSERT INTO site_accounts(id,role) VALUES('late','user')").run();
  await db.batch([alphaGrant(db,'late'),seasonGrant(db,'late')]);await initializeBadges(db);
  assert.deepEqual(await badgesFor(db,'late'),[]);
 }finally{db.close();}
});
test('Dedicated plus VIP requires all three earned seals and excludes staff',async()=>{
 const db=new LocalD1(schema);try{
  for(const[id,role]of[['member','user'],['owner','owners']]){await db.prepare('INSERT INTO site_accounts(id,role) VALUES(?,?)').bind(id,role).run();for(const key of ['alpha-tester','beta-tester','launch-contributor'])await db.prepare('INSERT INTO website_badges(user_id,badge_key,earned_at) VALUES(?,?,1)').bind(id,key).run();}
  assert.equal(await awardDedicated(db,'owner'),false);assert.equal(await awardDedicated(db,'member'),true);assert.equal(await awardDedicated(db,'member'),true);
  assert((await badgesFor(db,'member')).includes('dedicated-tester'));assert.equal((await db.prepare("SELECT rank_key FROM cp_account_ranks WHERE user_id='member'").all()).results.length,1);
  assert.equal((await db.prepare("SELECT role FROM site_accounts WHERE id='member'").first()).role,'user');
 }finally{db.close();}
});

test('season codes bind verified UUID and disappear only after authenticated receipt acknowledgement',async()=>{const db=new LocalD1(schema);try{await db.prepare("INSERT INTO site_accounts(id,role) VALUES('member','user')").run();await initializeBadges(db);await db.prepare('CREATE TABLE IF NOT EXISTS site_sessions(token_hash TEXT,user_id TEXT,expires_at INTEGER)').run();const token='A'.repeat(43),uuid='49d2fe76-7977-4d49-bd39-e48fc8c648b8';await db.prepare('INSERT INTO site_sessions(token_hash,user_id,expires_at) VALUES(?,?,?)').bind(createHash('sha256').update(token).digest('hex'),'member',Date.now()+100000).run();await db.prepare('INSERT INTO site_game_links(user_id,minecraft_uuid,minecraft_name,linked_at) VALUES(?,?,?,?)').bind('member',uuid,'Player',1).run();await db.prepare('INSERT INTO site_game_identities(minecraft_uuid,first_linked_at,last_name) VALUES(?,?,?)').bind(uuid,1,'Player').run();const secret='x'.repeat(40),handler=createCommunityRewards(db,{GAME_LINK_SECRET:secret},'https://cp.dunerealms.com'),get=()=>handler(new Request('https://cp.dunerealms.com/api/community/rewards',{headers:{cookie:'__Host-dune_session='+token}}));let r=await(await get()).json();assert.equal(r.available[0].reward_amount,5000);const code=r.available[0].personal_code;const post=(path,data,auth=true)=>handler(new Request('https://cp.dunerealms.com/api/community/game/'+path,{method:'POST',headers:{'Content-Type':'application/json',...(auth?{Authorization:'Bearer '+secret}:{})},body:JSON.stringify(data)}));assert.equal((await post('prepare',{uuid,code},false)).status,401);assert.equal((await post('prepare',{uuid:'00000000-0000-4000-8000-000000000001',code})).status,404);const prepared=await(await post('prepare',{uuid,code})).json();assert.equal(prepared.month,'S000001');assert.equal((await(await get()).json()).available.length,1);assert.equal((await post('ack',{uuid,receipt:prepared.receipt})).status,200);assert.equal((await post('ack',{uuid,receipt:prepared.receipt})).status,200);r=await(await get()).json();assert.equal(r.available.length,0);assert.equal(r.history.length,1);assert(!r.history[0].personal_code);}finally{db.close();}});

