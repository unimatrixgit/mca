import test from 'node:test';
import assert from 'node:assert/strict';
import {applicationProgress} from './election-applications.mjs';
const app={id:'a',election_start:100};
const round=(n,status,eligible=1,votes=4,winner_id=null)=>({round_number:n,status,eligible,votes,winner_id,opens_at:100+n*10,closes_at:110+n*10});
test('scheduled close does not falsely close an ongoing runoff',()=>{
 const p=applicationProgress(app,[round(0,'runoff'),round(1,'open',1,2)],null,null,115);
 assert.equal(p.state,'open');assert.equal(p.votes,2);assert.equal(p.result,'Pending');assert.equal(p.rounds[0].votes,4);
});
test('eliminated applicant keeps original round count and never inherits winner',()=>{
 const p=applicationProgress(app,[round(0,'runoff'),round(1,'winner',0,0,'b')],{application_id:'b'},null,125);
 assert.equal(p.votes,4);assert.equal(p.result,'Not elected');assert.equal(p.officeRecorded,false);
});
test('winner without reconciled term reports election only',()=>{
 const p=applicationProgress(app,[round(0,'winner',1,5,'a')],null,null,125);
 assert.equal(p.result,'Elected');assert.equal(p.officeRecorded,false);assert.equal(p.term,null);
});
test('permanent honour remains visible after subsequent defeat',()=>{
 const p=applicationProgress(app,[round(0,'winner',1,1,'b')],null,{title:'High Lord',earned_at:20},125);
 assert.equal(p.result,'Not elected');assert.equal(p.honour.title,'High Lord');
});
test('no votes closes without granting an office',()=>{
 const p=applicationProgress(app,[round(0,'no_votes',1,0)],null,null,125);
 assert.equal(p.state,'closed');assert.equal(p.result,'No votes cast');assert.equal(p.officeRecorded,false);
});
test('overdue unfinished round is shown as finalising, not a fabricated result',()=>{
 const p=applicationProgress(app,[round(0,'open')],null,null,125);
 assert.equal(p.phase,'Finalising round');assert.equal(p.state,'open');
});
