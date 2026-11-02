import {test} from 'node:test';
import assert from 'node:assert/strict';
import {pageArea,staffAreasFor} from '../control-panel/public/page-groups.mjs';
test('personal tickets and account tools stay separate from staff administration',()=>{
 for(const key of ['game','planetary-inventory','rewards','security','tickets','exchange'])assert.equal(pageArea(key),'user');
 for(const key of ['git-control','marketplace-home','adventure-home','account-home'])assert.equal(pageArea(key),'user');
 for(const key of ['git-admin','permissions','website-posts','shop-admin'])assert.equal(pageArea(key),'website-staff');
 for(const key of ['staff-tickets','forum-admin','bans','staff-manual'])assert.equal(pageArea(key),'community-staff');
 for(const key of ['server-operations','exchange-admin','luckperms'])assert.equal(pageArea(key),'game-staff');
 assert.equal(pageArea('webmail'),'webmail');
});

test('staff center areas remain permission based and ordinary players have none',()=>{
 assert.deepEqual(staffAreasFor(null),[]);assert.deepEqual(staffAreasFor({ranks:['user'],permissions:['web.menu.security']}),[]);
 assert.deepEqual(staffAreasFor({ranks:['vip'],permissions:['web.menu.security']}),[]);
 assert.deepEqual(staffAreasFor({ranks:['web_news'],permissions:['web.shop.support','web.bans.view']}),['website-staff','community-staff']);
 assert.deepEqual(staffAreasFor({ranks:['game_mod'],permissions:['web.bans.view']}),['community-staff','game-staff']);
 assert.deepEqual(staffAreasFor({ranks:['owners'],permissions:[]}),['website-staff','community-staff','game-staff','network-staff']);
});
