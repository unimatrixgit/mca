import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,existsSync} from 'node:fs';
import {randomUUID,randomBytes,createHash} from 'node:crypto';
import {LocalD1} from './d1-adapter.mjs';
import {initializeRanks,accessFor} from '../node/permissions.mjs';
import {badgesFor} from '../node/community-badges.mjs';
import {FORUM_AWARDS,forumAwardsFor} from '../node/recognition.mjs';
import {gameStaffSeals} from '../node/game-staff-seals.mjs';
import {createBadgeAdmin} from '../node/badge-admin.mjs';
const origin='https://cp.dunerealms.com';
async function setup(){
 const tail=readFileSync(new URL('../node/schema.sql',import.meta.url),'utf8').split('CREATE TABLE IF NOT EXISTS site_game_links')[1].replace(/^/,'CREATE TABLE IF NOT EXISTS site_game_links').replace(/ ENGINE=InnoDB DEFAULT CHARSET=utf8mb4/g,'');
 const db=new LocalD1('CREATE TABLE site_accounts(id TEXT PRIMARY KEY,username TEXT,display_name TEXT,role TEXT,password_hash TEXT,created_at INT);CREATE TABLE site_sessions(token_hash TEXT PRIMARY KEY,user_id TEXT,expires_at INT);'+tail);
 db.sqlite.exec('CREATE TABLE drforum_users(uid INT PRIMARY KEY,postnum INT);CREATE TABLE drforum_dune_accounts(website_id TEXT,uid INT);');
 await initializeRanks(db);
 async function add(username,role='user'){
  const id=randomUUID(),token=randomBytes(32).toString('base64url');
  await db.prepare('INSERT INTO site_accounts VALUES(?,?,?,?,?,?)').bind(id,username,username,role,'fixture-hash',Date.now()).run();
  await db.prepare('INSERT INTO site_sessions VALUES(?,?,?)').bind(createHash('sha256').update(token).digest('hex'),id,Date.now()+60000).run();
  return{id,username,role,cookie:'__Host-dune_session='+token};
 }
 const route=createBadgeAdmin(db,origin);
 const request=(actor,body,from=origin)=>route(new Request(origin+'/api/badges',{method:'POST',headers:{Origin:from,'Content-Type':'application/json',...(actor?{Cookie:actor.cookie}:{})},body:JSON.stringify(body)}));
 return{db,add,request};
}
test('forum milestones use authoritative counts at every boundary; Legend is an additional award after 1000',()=>{
 for(const [posts,count]of[[0,0],[1,1],[9,1],[10,2],[49,2],[50,3],[99,3],[100,4],[999,4],[1000,5],[1001,6]])assert.equal(forumAwardsFor(posts).length,count,String(posts));
 for(const invalid of[-1,NaN,Infinity,1.5])assert.equal(forumAwardsFor(invalid).length,0);
 assert.equal(forumAwardsFor(0,{showcase:true}).length,6);
 assert.equal(new Set(FORUM_AWARDS.map(a=>a.colour)).size,6);
});
test('Builder membership controls only the team seal; manual award survives rank removal; showcase includes future catalogue entries',async()=>{
 const r=await setup();try{
  const builder=await r.add('builder_fixture','game_builder');
  const rights=await accessFor(r.db,builder);
  assert(rights.ranks.includes('game_builder'));assert(!rights.nodes.includes('web.accounts.assign'));assert(!rights.nodes.includes('web.bank.staff.view'));
  assert(gameStaffSeals(rights.ranks).some(s=>s.key==='rank-builder'));
  assert(!(await badgesFor(r.db,builder.id)).includes('builder-award'));
  await r.db.prepare("INSERT INTO website_badges VALUES(?,'builder-award',1)").bind(builder.id).run();
  await r.db.prepare("UPDATE site_accounts SET role='user' WHERE id=?").bind(builder.id).run();
  assert(!gameStaffSeals(['user']).some(s=>s.key==='rank-builder'));
  assert((await badgesFor(r.db,builder.id)).includes('builder-award'));
  await r.db.prepare('INSERT INTO drforum_users VALUES(1,1001)').run();await r.db.prepare('INSERT INTO drforum_dune_accounts VALUES(?,1)').bind(builder.id).run();
  assert.equal((await badgesFor(r.db,builder.id)).filter(k=>k.startsWith('forum-')).length,6);
  await r.db.prepare('UPDATE drforum_users SET postnum=9').run();assert.equal((await badgesFor(r.db,builder.id)).filter(k=>k.startsWith('forum-')).length,1);
  const showcase=await r.add('DuneRealms','official_account');const keys=await badgesFor(r.db,showcase.id);
  assert(keys.includes('builder-award'));assert(keys.includes('forum-legend'));assert(!keys.some(k=>/crysknife/i.test(k)));
 }finally{r.db.close();}
});
test('manual Builder Award is staff-authorized, origin-checked, audited and independent of community editing',async()=>{
 // Never allow the isolated SQLite fixture to invoke an installed native adapter.
 assert(!existsSync('/home/dunerealms/apps/integrations/osticket/sync-agents.php'),'Run this fixture locally, not on the production web host');
 const r=await setup();try{
  const owner=await r.add('owner_fixture','owners'),member=await r.add('member_fixture'),builder=await r.add('staff_fixture','game_builder');
  const body={userId:builder.id,award:'builder-award',grant:true};
  assert.equal((await r.request(null,body)).status,401);assert.equal((await r.request(member,body)).status,403);
  assert.equal((await r.request(owner,body,'https://outside.test')).status,403);
  assert.equal((await r.request(owner,{...body,award:'forum-legend'})).status,400);
  assert.equal((await r.request(owner,body)).status,200);assert((await badgesFor(r.db,builder.id)).includes('builder-award'));
  assert.equal((await r.db.prepare("SELECT COUNT(*) AS n FROM account_audit WHERE action='award-granted'").first()).n,1);
  assert.equal((await r.request(owner,{...body,grant:false})).status,200);assert(!(await badgesFor(r.db,builder.id)).includes('builder-award'));
  await r.db.prepare("INSERT INTO website_badges VALUES(?,'builder-award',1)").bind(member.id).run();
  assert.equal((await r.request(owner,{userId:member.id,badges:['alpha-tester']})).status,200);
  assert((await badgesFor(r.db,member.id)).includes('builder-award'),'Community edit must not remove a manually awarded seal');
 }finally{r.db.close();}
});
