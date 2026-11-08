import test from 'node:test';
import assert from 'node:assert/strict';
import {electionDates,imperialVoteAllowance} from '../control-panel/public/election-rules.mjs';
test('confirmed opening Sunday and different term lengths',()=>{
 const now=Date.parse('2026-11-08T00:00:00Z');
 assert.deepEqual(electionDates('house',now,3),['2026-11-15T09:00:00.000Z','2026-11-29T09:00:00.000Z','2026-12-13T09:00:00.000Z']);
 assert.deepEqual(electionDates('imperial',now,2),['2026-11-15T15:00:00.000Z','2026-12-13T15:00:00.000Z']);
});
test('calendar advances after exact boundary and keeps GMT across DST',()=>{
 const boundary=Date.parse('2026-11-15T09:00:00Z');
 assert.equal(electionDates('house',boundary,1)[0],'2026-11-15T09:00:00.000Z');
 assert.equal(electionDates('house',boundary+1,1)[0],'2026-11-29T09:00:00.000Z');
 for(const date of electionDates('house',Date.parse('2027-03-01T00:00:00Z'),4)){assert.equal(new Date(date).getUTCDay(),0);assert.equal(new Date(date).getUTCHours(),9);}
 assert.throws(()=>electionDates('unknown'));assert.throws(()=>electionDates('house',NaN));
});
test('one ordinary member vote and five verified leader votes; unlinked denied',()=>{
 assert.equal(imperialVoteAllowance({linked:true,faction:'corrino'}),1);
 assert.equal(imperialVoteAllowance({linked:true,faction:'corrino',houseLeader:true}),5);
 assert.equal(imperialVoteAllowance({linked:true,faction:'corrino',houseLeader:'true'}),1);
 assert.equal(imperialVoteAllowance({houseLeader:true}),0);
 assert.equal(imperialVoteAllowance({linked:true,houseLeader:true}),0);
});
