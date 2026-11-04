import test from 'node:test';
import assert from 'node:assert/strict';
import {DEFAULT_RANKS,rankSection,accessFor} from '../node/permissions.mjs';
import {LocalD1} from './d1-adapter.mjs';
import {communitySealEligible} from '../node/community-badges.mjs';
import {visibleCommunityBadges} from '../node/native-seals.mjs';
import {gameNodesForRank,validateGameNodes} from '../node/game-ranks.mjs';
const keys=['patron_caravan','patron_guild','vip','patron_grand'];
test('paid ranks grant exactly Member website permissions; all are player ranks',()=>{
 const member=DEFAULT_RANKS.find(r=>r.key==='user');
 for(const key of keys){const rank=DEFAULT_RANKS.find(r=>r.key===key);assert(rank);assert.deepEqual([...rank.nodes].sort(),[...member.nodes].sort());assert.equal(rankSection(key),'players');assert(rank.level<40);}
});
test('supporters retain earned community badges instead of becoming staff',()=>{
 for(const key of keys){assert(communitySealEligible(['user',key]));assert.deepEqual(visibleCommunityBadges(['alpha-tester'],['user',key]),['alpha-tester']);assert(!communitySealEligible([key,'owners']));}
});
test('new supporter ranks add no gameplay permissions beyond Member and preserve Owner',async()=>{
 const db=new LocalD1('CREATE TABLE cp_account_ranks(user_id TEXT,rank_key TEXT);CREATE TABLE cp_ranks(rank_key TEXT,label TEXT,rank_level INTEGER,kind TEXT,permissions_json TEXT,version INTEGER);CREATE TABLE cp_rank_game_permissions(rank_key TEXT,permissions_json TEXT,version INTEGER);');
 try{
 const member=await gameNodesForRank(db,DEFAULT_RANKS.find(r=>r.key==='user'));
 assert.equal(validateGameNodes(['dune.admin'],'patron_grand'),false);
 assert.equal(validateGameNodes(['worldedit.*'],'vip'),false);
 await db.prepare('INSERT INTO cp_rank_game_permissions VALUES(?,?,?)').bind('patron_guild',JSON.stringify(['dune.admin','duneess.chat.colors']),1).run();
 const guarded=await gameNodesForRank(db,DEFAULT_RANKS.find(r=>r.key==='patron_guild'));assert(!guarded.nodes.includes('dune.admin'));assert(guarded.nodes.includes('duneess.chat.colors'));
 for(const key of keys.filter(k=>k!=='vip')){const result=await gameNodesForRank(db,DEFAULT_RANKS.find(r=>r.key===key));assert(result.nodes.every(n=>member.nodes.includes(n)||['duneess.chat.colors','duneess.chat.format'].includes(n)));}
 await db.prepare('INSERT INTO cp_account_ranks VALUES(?,?)').bind('owner','patron_grand').run();
 const owner=await accessFor(db,{id:'owner',role:'owners'});assert.equal(owner.rank,'owners');assert(owner.nodes.includes('web.ranks.manage'));
 }finally{db.close();}
});
