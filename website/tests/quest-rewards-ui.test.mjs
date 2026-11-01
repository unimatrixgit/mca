import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createQuestRewardController} from '../control-panel/public/quest-rewards.mjs';

const alice={username:'alice',permissions:['web.menu.game']},bob={username:'bob',permissions:['web.menu.game']};
const code='DRQ-'+'A'.repeat(40);
const response=(data,status=200)=>Response.json(data,{status});
const ready={linked:true,amount:1000,available:false,discovered:true,redeemed:false};
const deferred=()=>{let resolve,reject;const promise=new Promise((ok,no)=>{resolve=ok;reject=no;});return {promise,resolve,reject};};
function fixture(){const calls=[],renders=[];const controller=createQuestRewardController({fetcher:(url,options)=>{const pending=deferred();calls.push({url,options,...pending});return pending.promise;},render:state=>renders.push(state)});return {calls,renders,controller};}
async function load(f,data=ready){const task=f.controller.refresh();f.calls.at(-1).resolve(response(data));await task;}

test('quest form discards a late status after logout, including same-account re-login',async()=>{
 const f=fixture();f.controller.setSession(alice);const pending=f.controller.refresh(),old=f.calls[0];f.controller.setSession(null);f.controller.setSession(alice);old.resolve(response(ready));assert.equal(await pending,false);assert.equal(f.controller.snapshot().linked,null);assert.equal(f.controller.snapshot().discovered,false);assert(old.options.signal.aborted);
});
test('a pending claim cannot render success or leave its code in another account session',async()=>{
 const f=fixture();f.controller.setSession(alice);await load(f);const claim=f.controller.claim(code),call=f.calls.at(-1);const before=f.controller.snapshot().revision;f.controller.setSession(bob);call.resolve(response({ok:true,credited:true,amount:1000}));assert.equal(await claim,false);const state=f.controller.snapshot();assert(state.revision>before);assert.equal(state.redeemed,false);assert.equal(state.message,'');assert(call.options.signal.aborted);
});
test('latest status wins when reads complete out of order',async()=>{
 const f=fixture();f.controller.setSession(alice);const first=f.controller.refresh(),old=f.calls.at(-1),second=f.controller.refresh(),fresh=f.calls.at(-1);fresh.resolve(response({...ready,redeemed:true}));await second;old.resolve(response(ready));await first;assert.equal(f.controller.snapshot().redeemed,true);assert.equal(f.controller.snapshot().status,'redeemed');
});
test('claim submits only the opaque code, blocks duplicate clicks and clears it through a revision after success',async()=>{
 const f=fixture();f.controller.setSession(alice);await load(f);const before=f.controller.snapshot().revision,claim=f.controller.claim(code.toLowerCase()),call=f.calls.at(-1);
 assert.equal(await f.controller.claim(code),false);assert.equal(f.calls.length,2);assert.equal(call.url,'/api/quest/rewards/claim');assert.deepEqual(JSON.parse(call.options.body),{code});assert.equal(call.options.method,'POST');assert.equal(call.options.credentials,'same-origin');assert.equal(call.options.redirect,'error');assert.equal(call.options.cache,'no-store');
 call.resolve(response({ok:true,credited:true,amount:1000}));assert.equal(await claim,true);assert.equal(f.controller.snapshot().redeemed,true);assert(f.controller.snapshot().revision>before);assert.equal(await f.controller.claim(code),false);
});
test('current link changes invalidate pending claims and require a new verified status',async()=>{
 const f=fixture();f.controller.setSession(alice);await load(f);const claim=f.controller.claim(code),old=f.calls.at(-1);f.controller.linkChanged();const fresh=f.calls.at(-1);assert.notEqual(old,fresh);old.resolve(response({ok:true,credited:true,amount:1000}));await claim;fresh.resolve(response({linked:false,message:'Link your Minecraft identity first.'},403));await Promise.resolve();await Promise.resolve();await new Promise(done=>setImmediate(done));assert.equal(f.controller.snapshot().linked,false);assert.equal(f.controller.snapshot().redeemed,false);assert.equal(f.controller.snapshot().status,'unlinked');assert.equal(await f.controller.claim(code),false);
});
test('invalid or unverifiable results cannot claim a payment and server messages remain data',async()=>{
 const f=fixture();f.controller.setSession(alice);await load(f);assert.equal(await f.controller.claim('guess'),false);assert.equal(f.calls.length,1);
 const failed=f.controller.claim(code);f.calls.at(-1).resolve(response({ok:true,credited:true,amount:999999}));assert.equal(await failed,false);assert.equal(f.controller.snapshot().redeemed,false);assert.match(f.controller.snapshot().message,/could not be verified/);
 const retry=f.controller.claim(code);f.calls.at(-1).resolve(response({message:'<img src=x onerror=alert(1)>'},409));await retry;assert.equal(f.controller.snapshot().message,'<img src=x onerror=alert(1)>');
});
test('late bootstrap cannot restore a session after an explicit session event',async()=>{
 const f=fixture(),bootstrap=f.controller.bootstrap(),initial=f.calls[0];f.controller.setSession(null);initial.resolve(response({authenticated:true,user:alice}));assert.equal(await bootstrap,false);assert.equal(f.controller.snapshot().allowed,false);assert.equal(f.calls.length,1);
});
test('game permission is mandatory and private form code is never persisted or placed in URLs',async()=>{
 const f=fixture();f.controller.setSession({username:'alice',permissions:[]});assert.equal(await f.controller.refresh(),false);assert.equal(await f.controller.claim(code),false);assert.equal(f.calls.length,0);
 const source=await readFile(new URL('../control-panel/public/quest-rewards.mjs',import.meta.url),'utf8');
 for(const unsafe of ['innerHTML','insertAdjacentHTML','localStorage','sessionStorage','postMessage','location.search','imperial-portal-eye-ra-v1'])assert(!source.includes(unsafe),unsafe);
 assert.match(source,/status\.textContent=state\.message/);assert.match(source,/input\.value=''/);assert.match(source,/aria-live/);assert.match(source,/autocomplete='off'/);assert.match(source,/input\.maxLength=44/);assert.match(source,/event\.preventDefault\(\)/);
});
test('CP source registers only exact quest routes and publishes both themed frontend files',async()=>{
 const server=await readFile(new URL('../control-panel/server.mjs',import.meta.url),'utf8'),html=await readFile(new URL('../control-panel/public/index.html',import.meta.url),'utf8'),css=await readFile(new URL('../control-panel/public/quest-rewards.css',import.meta.url),'utf8');
 assert.match(server,/createQuestRewards\(db,settings,pool\)/);assert.match(server,/'\/api\/game\/quest\/discover','\/api\/quest\/rewards','\/api\/quest\/rewards\/claim'/);assert.match(server,/'quest-rewards\.mjs','quest-rewards\.css'/);
 assert.match(html,/type="module" src="\/quest-rewards\.mjs"/);assert.match(html,/href="\/quest-rewards\.css"/);assert.match(css,/@media\(max-width:760px\)/);assert.match(css,/\.quest-reward-card\[hidden\]/);assert.match(css,/:focus-visible/);
});
