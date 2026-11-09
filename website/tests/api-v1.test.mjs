import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {createApiV1} from '../node/api-v1.mjs';

const origin='https://cp.dunerealms.com';
const token='a'.repeat(43),hash=createHash('sha256').update(token).digest('hex');
const actor={id:'account-a',username:'alice',display_name:'Alice',role:'user'};
function fixture(settings={},deps={}){
 const events=[];
 const db={prepare(sql){
  return {bind(...args){return {async first(){
   if(sql.includes('FROM site_sessions'))return args[0]===hash?actor:null;
   if(sql.includes('FROM site_game_links'))return null;
   throw Error('Unexpected database access');
  }};}};
 }};
 const app=createApiV1(db,{API_V1_ENABLED:'true',...settings},{audit:event=>events.push(event),...deps});
 const req=(path,options={})=>app(new Request(origin+'/api/v1'+path,{method:options.method||'GET',headers:{...(options.auth===false?{}:{Cookie:'__Host-dune_session='+token}),...options.headers}}));
 return {req,events};
}
test('disabled API responds 404 and does not expose account data',async()=>{
 const f=fixture({API_V1_ENABLED:undefined});
 assert.equal((await f.req('/me')).status,404);
});
test('anonymous and forged authorization cannot access account',async()=>{
 const f=fixture();
 assert.equal((await f.req('/me',{auth:false})).status,401);
 assert.equal((await f.req('/me',{headers:{Authorization:'Bearer forged'}})).status,403);
 assert.equal((await f.req('/me',{headers:{Origin:'https://evil.example'}})).status,403);
});
test('own account read, correlation and no-store response',async()=>{
 const f=fixture();
 const r=await f.req('/me'),body=await r.json();
 assert.equal(r.status,200);
 assert.deepEqual(body.data,{id:'account-a',username:'alice',displayName:'Alice'});
 assert.equal(r.headers.get('cache-control'),'private, no-store');
 assert.equal(r.headers.get('x-request-id'),body.meta.requestId);
 assert.equal(f.events[0].actorId,'account-a');
});
test('identity is not accepted from URL or request body',async()=>{
 const f=fixture();
 assert.equal((await f.req('/me?uuid=other')).status,400);
 assert.equal((await f.req('/me',{method:'POST'})).status,405);
 assert.equal((await f.req('/admin/accounts')).status,404);
});
test('unlinked Minecraft account and unavailable catalogue fail safely',async()=>{
 const f=fixture();
 const linked=await (await f.req('/me/minecraft')).json();
 assert.deepEqual(linked.data,{linked:false,identity:null});
 const response=await f.req('/catalogue');
 assert.equal(response.status,503);
 assert.equal((await response.json()).error.code,'service_unavailable');
});
test('catalogue validates query before contacting bridge',async()=>{
 const f=fixture();
 for(const path of ['/catalogue?limit=0','/catalogue?limit=101','/catalogue?limit=2&limit=3','/catalogue?before=0','/catalogue?url=https://evil.example']){
  assert.equal((await f.req(path)).status,400,path);
 }
});
