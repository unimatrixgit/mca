import test from 'node:test';
import assert from 'node:assert/strict';
import {LocalD1} from './d1-adapter.mjs';
import {publicStaffRoster,createStaffRoster} from '../node/staff-roster.mjs';
function setup(){return new LocalD1('CREATE TABLE site_accounts(id TEXT PRIMARY KEY,username TEXT,display_name TEXT,role TEXT,email TEXT,minecraft_uuid TEXT);CREATE TABLE cp_account_ranks(user_id TEXT,rank_key TEXT,PRIMARY KEY(user_id,rank_key));CREATE TABLE cp_ranks(rank_key TEXT PRIMARY KEY,label TEXT,rank_level INTEGER,kind TEXT,permissions_json TEXT,version INTEGER);');}
test('public roster follows current effective roles, excludes users and never exports private identity fields',async()=>{
 const db=setup();try{
  for(const[id,name,role]of[['owner-id-private','unimatrix999','owners'],['network-id-private','dunerealms','web_network'],['admin-id-private','grmacken','web_net_admin'],['support-id-private','helper','user'],['member-id-private','ordinary','user']])await db.prepare('INSERT INTO site_accounts(id,username,display_name,role,email,minecraft_uuid) VALUES(?,?,?,?,?,?)').bind(id,name,name,role,'private@secret.invalid','private-minecraft-uuid').run();
  await db.prepare("INSERT INTO cp_account_ranks(user_id,rank_key) VALUES('support-id-private','web_support')").run();
  const roster=await publicStaffRoster(db);assert.deepEqual(roster.map(p=>p.username),['unimatrix999','grmacken','dunerealms','helper']);assert.equal(roster.find(p=>p.username==='helper').rankLabels[0],'Support Team');assert.deepEqual(roster.find(p=>p.username==='helper').teams,['Player support']);
  const serialized=JSON.stringify(roster);for(const secret of ['private@secret.invalid','private-minecraft-uuid','owner-id-private','password','permissions'])assert(!serialized.includes(secret));
  await db.prepare("DELETE FROM cp_account_ranks WHERE user_id='support-id-private'").run();assert(!(await publicStaffRoster(db)).some(p=>p.username==='helper'));
 }finally{db.close();}
});
test('anonymous roster HTML escapes names, shares header and supports HEAD without a body',async()=>{
 const db=setup();try{
  await db.prepare('INSERT INTO site_accounts(id,username,display_name,role) VALUES(?,?,?,?)').bind('id','<script>alert(1)</script>','unused display name','web_support').run();
  const route=createStaffRoster(db),response=await route(new Request('https://www.dunerealms.com/staff.html')),html=await response.text();assert.equal(response.status,200);assert(html.includes('aria-label="Main navigation"'));assert(html.includes('&lt;script&gt;alert(1)&lt;/script&gt;'));assert(!html.includes('<script>alert(1)</script>'));assert(html.includes('Player support'));assert(html.includes('rel="canonical"'));assert.equal(await(await route(new Request('https://www.dunerealms.com/staff.html',{method:'HEAD'}))).text(),'');assert.equal((await route(new Request('https://www.dunerealms.com/staff.html',{method:'POST'}))).status,405);
 }finally{db.close();}
});
test('team tables contain member cards and only validated public profile details',async()=>{
 const db=setup();try{
  await db.prepare('CREATE TABLE site_profiles(user_id TEXT PRIMARY KEY,bio TEXT,public_info TEXT,socials_json TEXT,avatar_key TEXT,birthday TEXT,email TEXT)').run();
  await db.prepare('CREATE TABLE site_game_links(user_id TEXT,minecraft_uuid TEXT)').run();
  await db.prepare("INSERT INTO site_accounts(id,username,display_name,role) VALUES('private-id','helper','Helper','web_support')").run();
  await db.prepare('INSERT INTO site_profiles(user_id,bio,public_info,socials_json,birthday,email) VALUES(?,?,?,?,?,?)').bind('private-id','Public biography','Public community details',JSON.stringify({github:'https://github.com/example',discord:'javascript:alert(1)'}),'1990-02-03','secret@example.invalid').run();
  await db.prepare('INSERT INTO site_game_links(user_id,minecraft_uuid) VALUES(?,?)').bind('private-id','49d2fe76-7977-4d49-bd39-e48fc8c648b8').run();
  let response=await createStaffRoster(db)(new Request('https://www.dunerealms.com/staff.html')),html=await response.text();
  assert(html.includes('class="roster-team-table"'));assert(html.includes('class="roster-card roster-member-card"'));assert(!html.includes('Staff directory'));assert(!html.includes('class="roster-directory"'));assert(!html.includes('<dt>Teams</dt>'));assert(html.includes('/staff-roster.css?v=team-cards-4'));assert(html.includes('Public biography'));assert(!html.includes('Public community details'));assert(!html.includes('class="roster-rank-label"'));for(const secret of ['secret@example.invalid','1990-02-03','49d2fe76-7977-4d49-bd39-e48fc8c648b8','javascript:alert'])assert(!html.includes(secret));
  await db.prepare('UPDATE site_profiles SET socials_json=? WHERE user_id=?').bind(JSON.stringify({github:'https://github.com/example'}),'private-id').run();
  html=await(await createStaffRoster(db)(new Request('https://www.dunerealms.com/staff.html'))).text();assert(html.includes('href="https://github.com/example"'));assert(html.includes('nofollow ugc'));
 }finally{db.close();}
});
test('team tables keep leadership first, members in effective-rank order and full-width uncached styles',async()=>{
 const db=setup();try{
  for(const[id,username,role]of[['o','owner','owners'],['a','admin','web_net_admin'],['s','support','web_support']])await db.prepare('INSERT INTO site_accounts(id,username,role) VALUES(?,?,?)').bind(id,username,role).run();
  const route=createStaffRoster(db),html=await(await route(new Request('https://www.dunerealms.com/staff.html'))).text();
  assert(html.indexOf('Network staff<small>')<html.indexOf('Legal team<small>'));assert(html.indexOf('<h3><a href="/people/owner">owner</a></h3>')<html.indexOf('<h3><a href="/people/admin">admin</a></h3>'));assert(!html.includes('roster-empty-cell"></td>'));
  const css=await route(new Request('https://www.dunerealms.com/staff-roster.css?v=team-cards-4'));assert.equal(css.headers.get('cache-control'),'no-cache');assert((await css.text()).includes('.roster-teams{display:block!important;width:100%!important'));
 }finally{db.close();}
});
test('all staff portraits use username-resolved Minecraft or Steve images, never a seal as the main portrait',async()=>{
 const db=setup();try{
  await db.prepare('CREATE TABLE site_game_links(user_id TEXT,minecraft_uuid TEXT)').run();
  await db.prepare("INSERT INTO site_accounts(id,username,role) VALUES('o','unimatrix999','owners'),('a','grmacken','web_net_admin')").run();
  await db.prepare("INSERT INTO site_game_links(user_id,minecraft_uuid) VALUES('o','49d2fe76-7977-4d49-bd39-e48fc8c648b8')").run();
  const members=await publicStaffRoster(db);assert.equal(members[0].portrait.mode,'skin');assert.equal(members[1].portrait.mode,'image');assert.equal(members[0].portrait.url,'https://cp.dunerealms.com/api/avatars/roster/unimatrix999');
  const html=await(await createStaffRoster(db)(new Request('https://www.dunerealms.com/staff.html'))).text();assert(html.includes('roster-skin-face'));assert(html.includes('roster-skin-hat'));assert(html.includes('class="roster-steve-avatar"'));assert(!html.includes('49d2fe76-7977-4d49-bd39-e48fc8c648b8'));assert(html.includes('Introduction coming soon.'));assert(!html.includes('class="roster-rank-label"'));
 }finally{db.close();}
});

test('hierarchy groups effective levels, sorts ties and keeps each person once',async()=>{
 const {staffHierarchy}=await import('../node/staff-roster.mjs');
 const people=[{username:'beta',level:55,rankLabels:['Dev Team']},{username:'owner',level:100,rankLabels:['Owner']},{username:'alpha',level:55,rankLabels:['Legal Team']}];
 const bands=staffHierarchy(people);assert.deepEqual(bands.map(b=>b.level),[100,55]);assert.deepEqual(bands[1].members.map(p=>p.username),['alpha','beta']);assert.equal(people[0].username,'beta');
});
test('hierarchy deduplicates equivalent web/game labels and reflects revocation',async()=>{
 const db=setup();try{
 await db.prepare("INSERT INTO site_accounts(id,username,role) VALUES('a','helper','web_support')").run();
 await db.prepare("INSERT INTO cp_account_ranks(user_id,rank_key) VALUES('a','game_support')").run();
 let roster=await publicStaffRoster(db);assert.deepEqual(roster[0].rankLabels,['Support Team']);
 let html=await(await createStaffRoster(db)(new Request('https://www.dunerealms.com/staff.html'))).text();assert(html.includes('Staff hierarchy'));assert(html.includes('href="#staff-team-3"'));assert(html.includes('id="staff-team-3"'));assert(!html.includes('reporting to'));
 await db.prepare("DELETE FROM cp_account_ranks WHERE user_id='a'").run();await db.prepare("UPDATE site_accounts SET role='user' WHERE id='a'").run();html=await(await createStaffRoster(db)(new Request('https://www.dunerealms.com/staff.html'))).text();assert(!html.includes('class="roster-hierarchy"'));
 }finally{db.close();}
});

test('Build Team is last, includes builder-only accounts and follows rank removal',async()=>{
 const db=setup();try{
 await db.prepare("INSERT INTO site_accounts(id,username,role) VALUES('o','owner','owners'),('b','builder','user'),('u','ordinary','user')").run();
 await db.prepare("INSERT INTO cp_account_ranks(user_id,rank_key) VALUES('b','game_builder')").run();
 let roster=await publicStaffRoster(db);
 assert.deepEqual(roster.find(p=>p.username==='builder').teams,['Build Team']);
 assert(!roster.find(p=>p.username==='owner').teams.includes('Build Team'));
 assert(!roster.some(p=>p.username==='ordinary'));
 const html=await(await createStaffRoster(db)(new Request('https://www.dunerealms.com/staff.html'))).text();
 assert(html.indexOf('Build Team<small>')>html.indexOf('Sales team<small>'));
 assert(html.includes('/assets/seals/rank-builder-150.png'));
 assert(html.includes('href="/people/builder"'));
 await db.prepare("DELETE FROM cp_account_ranks WHERE user_id='b'").run();
 assert(!(await publicStaffRoster(db)).some(p=>p.username==='builder'));
 }finally{db.close();}
});
