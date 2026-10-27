import test from 'node:test';
import assert from 'node:assert/strict';
import {createContact} from '../node/contact.mjs';
import {solveChallenge} from 'altcha-lib';
import {deriveKey} from 'altcha-lib/algorithms/pbkdf2';

const base='https://example.test';
const challenge=async handler=>(await handler(new Request(base+'/api/contact/challenge'))).json();
const request=(payload,origin=base)=>new Request(base+'/api/contact/reveal',{method:'POST',headers:{Origin:origin,'Content-Type':'application/json'},body:JSON.stringify({payload})});
const proof=async c=>Buffer.from(JSON.stringify({challenge:c,solution:await solveChallenge({challenge:c,deriveKey})})).toString('base64');

test('Contact email requires an authentic, unexpired, single-use CAPTCHA proof',async()=>{
  let time=Date.now();const handler=createContact({CONTACT_EMAIL:'operator@example.test'},{cost:1,counter:2,now:()=>time});
  const c=await challenge(handler),token=await proof(c);
  assert.ok(!JSON.stringify(c).includes('operator@example.test'));
  assert.equal((await handler(request(token,'https://evil.test'))).status,403);
  assert.equal((await handler(request('forged'))).status,400);
  const response=await handler(request(token));assert.equal(response.status,200);assert.equal((await response.json()).email,'operator@example.test');
  assert.equal((await handler(request(token))).status,403);
  const expired=await proof(await challenge(handler));time+=121000;
  assert.equal((await handler(request(expired))).status,403);
  assert.equal((await handler(new Request(base+'/api/contact/reveal'))).status,405);
});

test('Forged proof and concurrent replay never reveal the email twice',async()=>{
  const handler=createContact({CONTACT_EMAIL:'operator@example.test'},{cost:1,counter:2});
  const c=await challenge(handler);
  const invalid=Buffer.from(JSON.stringify({challenge:c,solution:{counter:2,derivedKey:'0'.repeat(64)}})).toString('base64');
  assert.equal((await handler(request(invalid))).status,403);
  const token=await proof(await challenge(handler));
  const replies=await Promise.all([handler(request(token)),handler(request(token))]);
  assert.deepEqual(replies.map(r=>r.status).sort(),[200,403]);
});
