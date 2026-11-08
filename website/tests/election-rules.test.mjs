import test from 'node:test';
import assert from 'node:assert/strict';
import {electionDates,imperialVoteAllowance,electionWindow,validateCandidature} from '../control-panel/public/election-rules.mjs';
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

test('applications close exactly when voting opens, voting lasts three hours',()=>{
 const start='2026-11-15T09:00:00.000Z',time=Date.parse(start);
 assert.equal(electionWindow('house',start,time-1).phase,'applications');
 assert.equal(electionWindow('house',start,time).phase,'voting');
 assert.equal(electionWindow('house',start,time+10800000-1).phase,'voting');
 assert.equal(electionWindow('house',start,time+10800000).phase,'closed');
 assert.throws(()=>electionWindow('house','2026-11-16T09:00:00Z'));
});
test('candidature validates consent, deadline and server-side faction',()=>{
 const input={kind:'house',start:'2026-11-15T09:00:00Z',accepted:true,motivation:'I will serve the House and its members.',faction:'harkonnen'};
 const context={linked:true,faction:'corrino'},now=Date.parse('2026-11-08T00:00:00Z');
 assert.equal(validateCandidature(input,context,now).faction,'corrino');
 assert.throws(()=>validateCandidature({...input,accepted:'true'},context,now));
 assert.throws(()=>validateCandidature(input,{linked:false},now));
 assert.throws(()=>validateCandidature(input,context,Date.parse(input.start)));
 assert.throws(()=>validateCandidature({...input,motivation:'short'},context,now));
 assert.equal(validateCandidature({...input,kind:'imperial',start:'2026-11-15T15:00:00Z'},context,now).faction,'imperium');
});


test('Imperial window closes at 18 GMT and recurring House windows at noon',()=>{
 for(const [kind,start,close] of [
  ['imperial','2026-11-15T15:00:00Z','2026-11-15T18:00:00.000Z'],
  ['house','2026-11-29T09:00:00Z','2026-11-29T12:00:00.000Z'],
  ['imperial','2026-12-13T15:00:00Z','2026-12-13T18:00:00.000Z']
 ]){
  const window=electionWindow(kind,start,Date.parse(start));
  assert.equal(window.applicationsClose,window.votingOpens);
  assert.equal(window.votingCloses,close);
  assert.equal(window.phase,'voting');
  assert.equal(electionWindow(kind,start,Date.parse(close)).phase,'closed');
 }
});
