import test from 'node:test';
import assert from 'node:assert/strict';
import {politicalSeals,POLITICAL_HERALDRY} from '../node/political-heraldry.mjs';
test('political recognition requires the exact assigned rank, never administrative authority',()=>{
 assert.deepEqual(politicalSeals(['owners','network_admin','web_dev']),[]);
 const granted=politicalSeals(['user','house_aerdrecht','seal_water']);
 assert.deepEqual(granted.map(x=>x.group),['House & faction','Imperial offices & rights']);
 assert(!granted.some(x=>x.rank==='seal_emperor'));
 assert.equal(politicalSeals(['user']).length,0);
 assert.equal(new Set(POLITICAL_HERALDRY.map(x=>x.rank)).size,POLITICAL_HERALDRY.length);
});
