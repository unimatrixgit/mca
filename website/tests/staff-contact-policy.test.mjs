import test from 'node:test';
import assert from 'node:assert/strict';
import {LocalD1} from './d1-adapter.mjs';
import {accessFor} from '../node/permissions.mjs';
import {publicStaffRoster} from '../node/staff-roster.mjs';

test('News and Streamer roster visibility never grants broad ticket access or changes ranks',async()=>{
 const db=new LocalD1('CREATE TABLE site_accounts(id TEXT PRIMARY KEY,username TEXT,role TEXT);CREATE TABLE cp_account_ranks(user_id TEXT,rank_key TEXT,PRIMARY KEY(user_id,rank_key));CREATE TABLE cp_ranks(rank_key TEXT PRIMARY KEY,label TEXT,rank_level INTEGER,kind TEXT,permissions_json TEXT,version INTEGER);');
 try{
  for(const role of ['web_news','web_streamer'])await db.prepare('INSERT INTO site_accounts(id,username,role) VALUES(?,?,?)').bind(role,role,role).run();
  const before=await db.prepare('SELECT * FROM site_accounts ORDER BY id').all();
  const roster=await publicStaffRoster(db);assert.equal(roster.length,2);assert(roster.every(member=>member.teams.includes('News team')));
  for(const user of before.results){const access=await accessFor(db,user);for(const node of ['web.tickets.staff','web.tickets.support','web.tickets.legal','web.tickets.sales','web.tickets.network'])assert(!access.nodes.includes(node),user.role+' must not gain '+node);}
  assert.deepEqual(await db.prepare('SELECT * FROM site_accounts ORDER BY id').all(),before);
  assert.deepEqual((await db.prepare('SELECT * FROM cp_account_ranks').all()).results,[]);
  for(const member of roster)for(const key of ['id','email','permissions','agentId','departmentId'])assert(!Object.hasOwn(member,key));
 }finally{db.close();}
});
