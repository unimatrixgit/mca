import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {randomUUID,randomBytes,createHash,scryptSync} from 'node:crypto';
import {LocalD1} from './d1-adapter.mjs';
import {archiveAccount,restoreAccount} from '../node/account-archive.mjs';
import {createAuth} from '../node/auth.mjs';
import {DEFAULT_RANKS,rankSection} from '../node/permissions.mjs';
import {createRankAdmin} from '../node/rank-admin.mjs';
import {honourAction,honoursFor,honourMarkup,validCitation} from '../node/honours.mjs';
import {createMemorials,memorialPortalBlock} from '../node/memorials.mjs';
const origin='https://www.dunerealms.com';
function fixture(){
 const tail=readFileSync(new URL('../node/schema.sql',import.meta.url),'utf8').split('CREATE TABLE IF NOT EXISTS site_game_links')[1].replace(/^/,'CREATE TABLE IF NOT EXISTS site_game_links').replace(/ ENGINE=InnoDB DEFAULT CHARSET=utf8mb4/g,'');
 const db=new LocalD1('CREATE TABLE site_accounts(id TEXT PRIMARY KEY,username TEXT UNIQUE,display_name TEXT,password_salt TEXT,password_hash TEXT,role TEXT,created_at INTEGER);CREATE TABLE site_sessions(token_hash TEXT PRIMARY KEY,user_id TEXT,expires_at INTEGER);'+tail);
 const add=async(name,role='user',password='0'.repeat(128))=>{const u={id:randomUUID(),username:name,role};await db.prepare('INSERT INTO site_accounts VALUES(?,?,?,?,?,?,?)').bind(u.id,name,name,'0'.repeat(32),password,role,Date.now()).run();return u;};
 const session=async u=>{const token=randomBytes(32).toString('base64url');await db.prepare('INSERT INTO site_sessions VALUES(?,?,?)').bind(createHash('sha256').update(token).digest('hex'),u.id,Date.now()+60000).run();return '__Host-dune_session='+token;};
 const req=(path,body,cookie,from=origin)=>new Request(origin+path,{method:body===undefined?'GET':'POST',headers:{Origin:from,'Content-Type':'application/json',...(cookie?{Cookie:cookie}:{})},...(body===undefined?{}:{body:JSON.stringify(body)})});
 return {db,add,session,req};
}
test('Owner-only removal is recoverable, revokes sessions and retains unrelated receipts',async()=>{
 const {db,add,session}=fixture();try{
 const owner=await add('owner','owners'),member=await add('member'),victim=await add('victim'),protectedUser=await add('protected','web_net_owner');await session(victim);
 await db.prepare('INSERT INTO cp_account_ranks VALUES(?,?)').bind(victim.id,'patron_guild').run();
 db.sqlite.exec('CREATE TABLE retained_receipts(id TEXT PRIMARY KEY,user_id TEXT);');await db.prepare('INSERT INTO retained_receipts VALUES(?,?)').bind('receipt',victim.id).run();
 const input={userId:victim.id,confirmation:victim.username};assert.equal((await archiveAccount(db,member,input)).status,403);
 assert.equal((await archiveAccount(db,owner,{userId:owner.id,confirmation:'owner'})).status,403);
 assert.equal((await archiveAccount(db,owner,{userId:protectedUser.id,confirmation:'protected'})).status,403);
 assert.equal((await archiveAccount(db,owner,{...input,confirmation:'wrong'})).status,400);
 const native=[];assert.equal((await archiveAccount(db,owner,input,{native:async(id,disabled)=>native.push([id,disabled])})).status,200);
 assert.equal((await db.prepare('SELECT password_hash FROM site_accounts WHERE id=?').bind(victim.id).first()).password_hash,'!deleted');
 assert.equal((await db.prepare('SELECT count(*) AS n FROM site_sessions WHERE user_id=?').bind(victim.id).first()).n,0);
 assert.equal((await db.prepare('SELECT count(*) AS n FROM cp_account_ranks WHERE user_id=?').bind(victim.id).first()).n,0);
 assert.equal((await db.prepare('SELECT count(*) AS n FROM retained_receipts').first()).n,1);assert.deepEqual(native,[[victim.id,true]]);
 assert.equal((await archiveAccount(db,owner,input)).status,409);
 assert.equal((await restoreAccount(db,member,input)).status,403);
 assert.equal((await restoreAccount(db,owner,input)).status,200);
 assert.equal((await db.prepare('SELECT password_hash FROM site_accounts WHERE id=?').bind(victim.id).first()).password_hash,'0'.repeat(128));
 assert.equal((await db.prepare('SELECT rank_key FROM cp_account_ranks WHERE user_id=?').bind(victim.id).first()).rank_key,'patron_guild');
 }finally{db.close();}
});
test('disabled memorial and archived accounts cannot sign in even with a valid-shaped password',async()=>{
 const {db,add,req}=fixture();try{await add('memorial','user','!memorial');await add('removed','user','!deleted');const auth=createAuth(db,origin);for(const username of ['memorial','removed'])assert.equal((await auth(req('/api/auth/login',{username,password:'a sufficiently long password'}))).status,401);}finally{db.close();}
});
test('honour is automatic, citation is protected, multiline, versioned and safely rendered',async()=>{
 const {db,add,req}=fixture();try{
 const owner=await add('owner','owners'),recipient=await add('recipient','web_net_owner'),member=await add('member');
 const basic=DEFAULT_RANKS.find(r=>r.key==='user').nodes;
 await db.prepare('INSERT INTO cp_ranks VALUES(?,?,?,?,?,?)').bind('crysknife','Holy Order of The Crysknife',20,'access',JSON.stringify(basic),1).run();
 await db.prepare('INSERT INTO cp_account_ranks VALUES(?,?)').bind(recipient.id,'crysknife').run();
 assert.equal((await honoursFor(db,recipient))[0].version,0);
 const body={userId:recipient.id,citation:'For years of service\nAcross our community\n<script>alert(1)</script>',version:0};
 assert.equal((await honourAction(db,member,req('/api/ranks/honour',body),origin)).status,403);
 assert.equal((await honourAction(db,owner,req('/api/ranks/honour',body,null,'https://evil.test'),origin)).status,403);
 assert.equal((await honourAction(db,owner,req('/api/ranks/honour',body),origin)).status,200);
 assert.equal((await honourAction(db,owner,req('/api/ranks/honour',body),origin)).status,409);
 const current=await honoursFor(db,recipient);assert(current[0].citation.includes('\n'));assert(honourMarkup(current).includes('&lt;script&gt;'));assert(!honourMarkup(current).includes('<script>'));
 assert(!validCitation('x\n'.repeat(20)));assert(!validCitation('x'.repeat(2001)));assert(validCitation('First line\nSecond line'));assert(!validCitation('bad\u0000'));
 await db.prepare('DELETE FROM cp_account_ranks WHERE user_id=?').bind(recipient.id).run();assert.deepEqual(await honoursFor(db,recipient),[]);
 }finally{db.close();}
});
test('memorial is clearly unaffiliated; condolences require login and moderation before publication',async()=>{
 const {db,add,session,req}=fixture();try{
 const person=await add('memorial','memorial','!memorial'),member=await add('member'),owner=await add('owner','owners');const cookie=await session(member),admin=await session(owner);
 await db.prepare('INSERT INTO site_memorials VALUES(?,?,?,?)').bind('patrick-stewart',person.id,'Patrick Stewart — Community Memorial','A tribute maintained by the community.').run();
 const route=createMemorials(db,{PUBLIC_ORIGIN:origin});
 let html=await(await route(req('/memorial/patrick-stewart'))).text();assert(html.includes('not the person'));assert(html.includes('cannot sign in'));assert(html.includes('No condolences'));
 assert.equal((await route(req('/api/memorials/patrick-stewart',{message:'A respectful tribute'}))).status,401);
 assert.equal((await route(req('/api/memorials/patrick-stewart',{message:'x'},cookie))).status,400);
 assert.equal((await route(req('/api/memorials/patrick-stewart',{message:'Respect',status:'approved'},cookie))).status,400);
 assert.equal((await route(req('/api/memorials/patrick-stewart',{message:'<script>Respect</script>'},cookie,'https://evil.test'))).status,403);
 assert.equal((await route(req('/api/memorials/patrick-stewart',{message:'<script>Respect</script>'},cookie))).status,201);
 assert.equal((await route(req('/api/memorials/patrick-stewart',{message:'<script>Respect</script>'},cookie))).status,409);
 html=await(await route(req('/memorial/patrick-stewart'))).text();assert(!html.includes('&lt;script&gt;Respect'));
 assert.equal((await route(req('/api/memorials/patrick-stewart',undefined,cookie))).status,403);
 const queue=await(await route(req('/api/memorials/patrick-stewart',undefined,admin))).json();assert.equal(queue.pending.length,1);
 const decision={id:queue.pending[0].id,version:1,status:'approved'};
 assert.equal((await route(req('/api/memorials/patrick-stewart/decision',decision,cookie))).status,403);
 assert.equal((await route(req('/api/memorials/patrick-stewart/decision',decision,admin))).status,200);
 assert.equal((await route(req('/api/memorials/patrick-stewart/decision',decision,admin))).status,409);
 html=await(await route(req('/memorial/patrick-stewart'))).text();assert(html.includes('&lt;script&gt;Respect&lt;/script&gt;'));
 assert(!html.includes('<script>Respect'));
 assert.equal((await route(req('/api/memorials/patrick-stewart',{message:'Another tribute'},cookie))).status,201);
 assert.equal((await route(req('/api/memorials/patrick-stewart',{message:'Third tribute'},cookie))).status,201);
 assert.equal((await route(req('/api/memorials/patrick-stewart',{message:'Fourth tribute'},cookie))).status,429);
 }finally{db.close();}
});
test('Custom Memorial assignment locks current sessions and login, creates portal entry, rejects staff mixtures',async()=>{
 const{db,add,session,req}=fixture();try{
  const owner=await add('owner','owners'),member=await add('remembered'),staff=await add('staff','web_support');const cookie=await session(owner),oldSession=await session(member);const password='Valid password for test';
  await db.prepare('UPDATE site_accounts SET password_hash=? WHERE id=?').bind(scryptSync(password,'0'.repeat(32),64).toString('hex'),member.id).run();
  const auth=createAuth(db,origin);assert.equal((await auth(req('/api/auth/login',{username:member.username,password}))).status,200);
  const route=createRankAdmin(db,{},origin);assert.equal(rankSection('memorial'),'legacy');
  assert.equal((await route(req('/api/ranks/assign',{userId:member.id,ranks:['memorial','web_support']},cookie))).status,403);
  assert.equal((await route(req('/api/ranks/assign',{userId:member.id,ranks:['memorial','crysknife']},cookie))).status,403); // Existing custom rank must be registered before assignment.
  assert.equal((await route(req('/api/ranks/assign',{userId:member.id,ranks:['memorial']},cookie))).status,200);
  assert.equal((await db.prepare('SELECT role FROM site_accounts WHERE id=?').bind(member.id).first()).role,'memorial');
  assert.equal((await(await auth(req('/api/auth/session',undefined,oldSession))).json()).authenticated,false);
  assert.equal((await auth(req('/api/auth/login',{username:member.username,password}))).status,401);
  const block=await memorialPortalBlock(db);assert(block.includes('Community memorials'));assert(block.includes('/memorial/remembered'));assert(!block.includes('password'));
  const publicRoute=createMemorials(db,{PUBLIC_ORIGIN:origin});assert.equal((await publicRoute(req('/people/remembered'))).status,302);assert.equal((await publicRoute(req('/memorial/remembered'))).status,200);
 }finally{db.close();}
});
