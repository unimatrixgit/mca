import test from 'node:test';
import assert from 'node:assert/strict';
import {rankSection,RANK_SECTIONS} from '../node/permissions.mjs';
test('custom rank categories separate purchase routes from offices and earned memberships',()=>{
 assert.equal(rankSection('patron_guild'),'real_currency');assert.equal(rankSection('vanity_concord'),'real_currency');assert.equal(rankSection('vanity_dm_example'),'game_currency');
 for(const key of ['office_emperor','office_house_leader','guild_envoy','mentat'])assert.equal(rankSection(key),'appointed');
 for(const key of ['house_aerdrecht','crysknife','path_spice_smuggler'])assert.equal(rankSection(key),'legacy');
 assert.equal(rankSection('owners'),'game');assert.equal(rankSection('network_admin'),'website');assert.equal(rankSection('user'),'players');assert.equal(RANK_SECTIONS.game_currency,'Game Currency (Dunemarks)');
});
