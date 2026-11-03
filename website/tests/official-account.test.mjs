import test from 'node:test';
import assert from 'node:assert/strict';
import {LocalD1} from './d1-adapter.mjs';
import {accessFor,WEBSITE_NODES,DEFAULT_RANKS,isOwnerRank,rankSection} from '../node/permissions.mjs';
import {publicStaffRoster} from '../node/staff-roster.mjs';
function db(){return new LocalD1('CREATE TABLE site_accounts(id TEXT,username TEXT,role TEXT);CREATE TABLE cp_account_ranks(user_id TEXT,rank_key TEXT);CREATE TABLE cp_ranks(rank_key TEXT,label TEXT,rank_level INTEGER,kind TEXT,permissions_json TEXT,version INTEGER);');}
test('Official Account and Main Network Administrator have every registered Owner permission',async()=>{const d=db();try{for(const role of ['owners','official_account','web_net_admin']){const rights=await accessFor(d,{id:role,role});assert.deepEqual([...rights.nodes].sort(),Object.keys(WEBSITE_NODES).sort());assert(isOwnerRank(rights.rank));}assert(!isOwnerRank('web_network'));assert(!isOwnerRank('web_dev'));assert.equal(rankSection('official_account'),'website');assert.equal(DEFAULT_RANKS.find(r=>r.key==='web_net_admin').level,95);}finally{d.close();}});
test('Official account is absent from every public staff department even with full rights',async()=>{const d=db();try{await d.prepare('INSERT INTO site_accounts VALUES(?,?,?)').bind('official','dunerealms','official_account').run();await d.prepare('INSERT INTO site_accounts VALUES(?,?,?)').bind('main','grmacken','web_net_admin').run();const roster=await publicStaffRoster(d);assert(!roster.some(p=>p.username==='dunerealms'));assert(roster.some(p=>p.username==='grmacken'));}finally{d.close();}});

