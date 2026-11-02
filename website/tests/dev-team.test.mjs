import test from 'node:test';
import assert from 'node:assert/strict';
import {DEFAULT_RANKS} from '../node/permissions.mjs';
import {departmentsFor} from '../node/native-seals.mjs';
import {publicStaffRoster} from '../node/staff-roster.mjs';
import {LocalD1} from './d1-adapter.mjs';
test('Dev rank is limited to its department, leadership gains Dev access, ordinary users do not',()=>{
 const dev=DEFAULT_RANKS.find(r=>r.key==='web_dev');assert(dev.nodes.includes('web.tickets.dev'));assert(dev.nodes.includes('web.tickets.staff'));
 for(const n of ['web.tickets.admin','web.tickets.legal','web.git.admin','web.ranks.manage','web.servers.console','web.bans.manage'])assert(!dev.nodes.includes(n));
 for(const key of ['owners','corestaff','web_network','web_net_admin'])assert(DEFAULT_RANKS.find(r=>r.key===key).nodes.includes('web.tickets.dev'));
 for(const key of ['user','vip','web_support'])assert(!DEFAULT_RANKS.find(r=>r.key===key).nodes.includes('web.tickets.dev'));
});
test('Dev seal follows Dev access including protected Corestaff without leaking other department seals',()=>{
 assert.deepEqual(departmentsFor(['web.tickets.staff','web.tickets.dev'],['web_dev'],'dev'),['dev']);
 assert.deepEqual(departmentsFor(['web.tickets.staff','web.tickets.support','web.tickets.dev'],['corestaff'],'core'),['support','dev']);
 assert.deepEqual(departmentsFor([],['user'],'member'),[]);
});
test('public Dev roster contains Dev and protected leadership, not VIP submitters',async()=>{
 const db=new LocalD1('CREATE TABLE site_accounts(id TEXT PRIMARY KEY,username TEXT,role TEXT);CREATE TABLE cp_account_ranks(user_id TEXT,rank_key TEXT);CREATE TABLE cp_ranks(rank_key TEXT,label TEXT,rank_level INTEGER,kind TEXT,permissions_json TEXT,version INTEGER);');
 try{for(const [id,role] of [['dev','web_dev'],['core','corestaff'],['vip','vip']])await db.prepare('INSERT INTO site_accounts VALUES(?,?,?)').bind(id,id,role).run();
 const roster=await publicStaffRoster(db);assert(roster.find(r=>r.username==='dev').teams.includes('Dev team'));assert(roster.find(r=>r.username==='core').teams.includes('Dev team'));assert(!roster.some(r=>r.username==='vip'));
 }finally{db.close();}
});
