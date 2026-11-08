import test from 'node:test';
import assert from 'node:assert/strict';
import {validateBallot,electionOutcome,planRoundResult} from '../control-panel/election-ballots.mjs';
const id='12345678-1234-1234-1234-123456789abc',second='22345678-1234-1234-1234-123456789abc';
const context={linked:true,playerUuid:id,faction:'atreides',houseLeader:true};
const input={kind:'imperial',start:'2026-11-15T15:00:00Z',confirmed:true,requestId:second,allocations:[{applicationId:id,votes:5}]};
const now=Date.parse(input.start);
test('five leader votes can support one candidate or multiple candidates',()=>{
 assert.equal(validateBallot(input,context,now).total,5);
 assert.equal(validateBallot({...input,allocations:[{applicationId:id,votes:2},{applicationId:second,votes:3}]},context,now).total,5);
 assert.equal(validateBallot({...input,allocations:[{applicationId:id,votes:1}]},context,now).total,1);
});
test('ordinary member has one Imperial vote; House elections have one vote',()=>{
 assert.throws(()=>validateBallot(input,{...context,houseLeader:false},now));
 assert.throws(()=>validateBallot({...input,kind:'house',start:'2026-11-15T09:00:00Z'},context,Date.parse('2026-11-15T09:00:00Z')));
});
test('votes require genuine integer amounts, consent and unique candidates',()=>{
 for(const votes of [0,-1,1.5,'1',NaN,6])assert.throws(()=>validateBallot({...input,allocations:[{applicationId:id,votes}]},context,now));
 assert.throws(()=>validateBallot({...input,confirmed:'true'},context,now));
 assert.throws(()=>validateBallot({...input,allocations:[{applicationId:id,votes:1},{applicationId:id.toUpperCase(),votes:1}]},context,now));
});
test('votes outside the exact three-hour window are rejected',()=>{
 assert.throws(()=>validateBallot(input,context,now-1));
 assert.equal(validateBallot(input,context,now+10800000-1).total,5);
 assert.throws(()=>validateBallot(input,context,now+10800000));
 assert.throws(()=>validateBallot(input,{...context,faction:null},now));
});
test('ties require runoff, never an arbitrary UUID winner',()=>{
 assert.deepEqual(electionOutcome([{id,votes:2},{id:second,votes:2}]),{status:'runoff_required',winner:null,runoff:[id,second]});
 assert.equal(electionOutcome([{id,votes:3},{id:second,votes:2}]).winner,id);
 assert.equal(electionOutcome([{id,votes:0}]).status,'no_votes');
});
test('runoff follows immediately for three hours, only tied leaders qualify',()=>{
 const election={kind:'imperial',start:input.start,candidates:[{id,votes:3},{id:second,votes:3},{id:'third',votes:1}]};
 assert.throws(()=>planRoundResult(election,now+10800000-1));
 const result=planRoundResult(election,now+10800000);
 assert.equal(result.nextRound.opensAt,Date.parse('2026-11-15T18:00:00Z'));
 assert.equal(result.nextRound.closesAt,Date.parse('2026-11-15T21:00:00Z'));
 assert.deepEqual(result.nextRound.candidateIds,[id,second]);
 const repeated=planRoundResult({...election,round:1},now+21600000);
 assert.equal(repeated.nextRound.opensAt,now+21600000);
 assert.equal(repeated.nextRound.closesAt,now+32400000);
 assert.equal(planRoundResult({...election,candidates:[{id,votes:4},{id:second,votes:3}]},now+10800000).nextRound,null);
});
