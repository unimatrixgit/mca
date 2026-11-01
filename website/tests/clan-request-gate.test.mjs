import test from 'node:test';import assert from 'node:assert/strict';
import {createClanRequestGate} from '../control-panel/public/clan-request-gate.mjs';
const deferred=()=>{let resolve;const promise=new Promise(done=>resolve=done);return {promise,resolve};};
test('logout invalidates pending private responses even if the same account signs in again',async()=>{
 const gate=createClanRequestGate();gate.setActor('alice');const token=gate.begin('detail'),pending=deferred();let applicants=null;
 const render=(async()=>{const response=await pending.promise;if(gate.current(token))applicants=response;})();
 gate.setActor(null);applicants=null;gate.setActor('alice');pending.resolve(['private applicant']);await render;assert.equal(applicants,null);assert(!gate.current(token));assert(gate.current(gate.begin('detail')));
});
test('an account switch blocks old detail, directory, mutation success and error rendering',async()=>{
 const gate=createClanRequestGate();gate.setActor('alice');const tokens=['detail','directory','mutation'].map(lane=>gate.begin(lane));const pending=tokens.map(()=>deferred());let rendered=[];
 const tasks=pending.map((request,index)=>(async()=>{const result=await request.promise;if(gate.current(tokens[index]))rendered.push(result);})());gate.setActor('bob');for(const [index,request]of pending.entries())request.resolve({privateResult:index});await Promise.all(tasks);assert.deepEqual(rendered,[]);assert(tokens.every(token=>!gate.current(token)));
});
test('latest request in a lane wins when responses arrive out of order',async()=>{
 const gate=createClanRequestGate();gate.setActor('alice');const old=gate.begin('directory'),oldRequest=deferred(),fresh=gate.begin('directory'),freshRequest=deferred();let result=null;
 const render=async(request,token)=>{const value=await request.promise;if(gate.current(token))result=value;};const first=render(oldRequest,old),second=render(freshRequest,fresh);freshRequest.resolve('fresh');await second;oldRequest.resolve('stale');await first;assert.equal(result,'fresh');
});
test('back navigation cancels pending details without cancelling unrelated mutation state',()=>{
 const gate=createClanRequestGate();gate.setActor('alice');const detail=gate.begin('detail'),mutation=gate.begin('mutation');gate.invalidate('detail');assert(!gate.current(detail));assert(gate.current(mutation));
});
test('a late bootstrap session response cannot restore authentication after a session event',()=>{
 const gate=createClanRequestGate(),initial=gate.snapshot();gate.setActor(null);assert(!gate.sameSession(initial));assert.equal(gate.begin('directory'),null);const second=gate.snapshot();gate.setActor('bob');assert(!gate.sameSession(second));
});
