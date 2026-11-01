import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DEFAULT_RANKS} from '../node/permissions.mjs';
// Exercise the actual expression serialized into the native MyBB projection.
const native=readFileSync(new URL('../node/native-seals.mjs',import.meta.url),'utf8');
const expression=native.match(/forumGroup=([^\n]+?),roleSeals=/)?.[1];
assert(expression,'Native forum group projection must remain explicit and auditable');
const group=new Function('access','return '+expression);
test('rank management does not grant native MyBB administration',()=>{
 for(const [nodes,expected]of [
  [[],2],[['web.ranks.manage'],2],[['web.forum.moderate'],6],
  [['web.ranks.manage','web.forum.moderate'],6],[['web.forum.admin'],4],
  [['web.ranks.manage','web.forum.admin'],4],[['web.forum.admin','web.forum.moderate'],4],
  [['web.ranks.manage','web.forum.admin','web.forum.moderate'],4],
  [['web.forum.admin.fake'],2]
 ])assert.equal(group({nodes}),expected,nodes.join(','));
});
test('existing intended leadership remains explicitly authorised and forum team remains moderator',()=>{
 for(const [key,expected]of [['owners',4],['web_net_admin',4],['web_network',4],['corestaff',4],['web_forum',6],['web_news',2],['web_support',2],['user',2]]){
  const rank=DEFAULT_RANKS.find(r=>r.key===key);assert(rank,key);assert.equal(group({nodes:rank.nodes}),expected,key);
 }
});
test('native SSO, guard and admin CP use the same explicit PHP ACL helper',()=>{
 const sso=readFileSync(new URL('../integrations/mybb/dune-sso.php',import.meta.url),'utf8'),plugin=readFileSync(new URL('../integrations/mybb/dune_accounts.php',import.meta.url),'utf8');
 for(const source of [sso,plugin]){assert(source.includes('forum-access.php'));assert(source.includes('dune_forum_group_for($identity)'));assert(!source.includes('web.ranks.manage'));}
 assert(plugin.includes('!dune_forum_can_admin($identity)'));assert(plugin.indexOf("if (!defined('IN_MYBB'))")<plugin.indexOf("require_once MYBB_ROOT.'forum-access.php'"));
});
