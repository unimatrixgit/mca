import test from 'node:test';
import assert from 'node:assert/strict';
import {createHmac,createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {LocalD1} from './d1-adapter.mjs';
import {createQuestRewards,QUEST_ID} from '../control-panel/quest-rewards.mjs';

const accountA='11111111-1111-4111-8111-111111111111',accountB='22222222-2222-4222-8222-222222222222';
const uuidA='aaaaaaaa-0000-4000-8000-000000000001',uuidB='bbbbbbbb-0000-4000-8000-000000000002';
const secret='quest-test-secret-'.padEnd(64,'x'),clock=1800000000000,origin='https://cp.dunerealms.com';
const sha=value=>createHash('sha256').update(value).digest('hex');
test('Java and Node discovery signatures share the exact UTF-8 canonical test vector',()=>{
 const proof=createHmac('sha256','x'.repeat(32)).update('quest-discovery-v1\nimperial-portal-eye-ra-v1\n00000000-0000-4000-8000-000000000001\n'+'a'.repeat(64)+'\n1790000000000').digest('hex');
 assert.equal(proof,'557583f29f884a9211cc734b05a671822d933d65d27ac5d672e3d07310ca8621');
});
function discovery(uuid=uuidA,nonce='a'.repeat(64),expires=clock+30000){
 const body={uuid,quest:QUEST_ID,nonce,expires};
 body.proof=createHmac('sha256',secret).update(`quest-discovery-v1\n${body.quest}\n${body.uuid}\n${body.nonce}\n${body.expires}`).digest('hex');return body;
}
function request(path='/api/game/quest/discover',body=discovery(),options={}){
 const method=options.method||(path==='/api/quest/rewards'?'GET':'POST');
 return new Request(origin+path,{method,headers:{authorization:'Bearer '+(options.token??secret),origin:options.origin??origin,'content-type':'application/json',...(options.headers||{})},...(method==='GET'?{}:{body:options.raw??JSON.stringify(body)})});
}
// A serialized model for exercising route concurrency and retry contracts. The
// production PostgreSQL functions, not this model, arbitrate the money transaction.
function modelPool(){
 let row=null,balance=0,fail=false;const calls=[];
 return {calls,get row(){return row;},get balance(){return balance;},set fail(value){fail=value;},
  async query({text,values}){calls.push({text,values});if(fail)throw Error('private-db-secret');
   const [account,uuid,hash,proof]=values;
   if(text.includes('dune_quest_register')){
    if(!row)row={account,uuid,hash,proof,redeemed:false};
    if(row.account!==account||row.uuid!==uuid)return {rows:[{outcome:'taken'}]};
    if(row.redeemed)return {rows:[{outcome:'redeemed'}]};
    const repeated=row.hash===hash&&row.proof===proof;row.hash=hash;row.proof=proof;
    return {rows:[{outcome:repeated?'issued':'reissued'}]};
   }
   if(text.includes('dune_quest_claim')){
    if(!row||row.account!==account||row.uuid!==uuid||row.hash!==hash)return {rows:[{outcome:'invalid'}]};
    if(row.redeemed)return {rows:[{outcome:'already_redeemed'}]};row.redeemed=true;balance+=1000;return {rows:[{outcome:'credited'}]};
   }
   if(text.includes('dune_quest_status'))return {rows:[{available:!row,discovered:!!row&&row.account===account&&row.uuid===uuid,redeemed:!!row&&row.account===account&&row.uuid===uuid&&row.redeemed}]};
   throw Error('Unexpected SQL');
  }};
}
function fixture({user=accountA,nodes=['web.menu.game'],pool=modelPool()}={}){
 const db=new LocalD1(`CREATE TABLE site_accounts(id TEXT,username TEXT,role TEXT);CREATE TABLE site_game_links(user_id TEXT,minecraft_uuid TEXT);INSERT INTO site_accounts VALUES('${accountA}','A','user'),('${accountB}','B','user');INSERT INTO site_game_links VALUES('${accountA}','${uuidA}'),('${accountB}','${uuidB}');`);
 const route=createQuestRewards(db,{GAME_LINK_SECRET:secret,CP_ORIGIN:origin},pool,{now:()=>clock,authenticate:async()=>user?{id:user}:null,access:async()=>({nodes})});
 return {db,pool,route};
}

test('discovery requires game capability and a fresh domain-separated HMAC, not website identity headers',async()=>{
 const f=fixture();try{
  assert.equal((await f.route(request(undefined,undefined,{token:'wrong',headers:{'oai-authenticated-user-id':accountA}}))).status,401);
  assert.equal((await f.route(request(undefined,undefined,{method:'GET'}))).status,405);
  const body=discovery();body.proof='b'.repeat(64);assert.equal((await f.route(request(undefined,body))).status,403);
  for(const body of [discovery(uuidA,'a'.repeat(64),clock),discovery(uuidA,'a'.repeat(64),clock+45001),{...discovery(),uuid:uuidA.toUpperCase()},{...discovery(),quest:'different'},{...discovery(),nonce:'guess'},null])assert.equal((await f.route(request(undefined,body))).status,400);
  assert.equal((await f.route(request(undefined,null,{raw:'not-json'}))).status,400);
  assert.equal((await f.route(request(undefined,null,{raw:'x'.repeat(5000)}))).status,400);
  assert.equal(f.pool.calls.length,0);
 }finally{f.db.close();}
});
test('discovery binds only the verified UUID to immutable account and returns an opaque code without a wallet write',async()=>{
 const f=fixture();try{
  const response=await f.route(request(undefined,{...discovery(),accountId:accountB,amount:999999,code:'guess'}));
  assert.equal(response.status,200);assert.match(response.headers.get('cache-control'),/private, no-store/);
  const result=await response.json();assert.match(result.code,/^DRQ-[A-F0-9]{40}$/);assert.equal(result.amount,1000);assert.equal(f.pool.balance,0);
  assert.equal(f.pool.calls[0].values[0],accountA);assert.equal(f.pool.calls[0].values[1],uuidA);assert.equal(f.pool.calls[0].values[2],sha(result.code));
  assert(!JSON.stringify(result).includes(uuidA));assert(!JSON.stringify(result).includes(accountA));assert(!JSON.stringify(f.pool.calls).includes(result.code));
 }finally{f.db.close();}
});
test('a repeated discovery proof returns the same code; another verified account cannot become a second first finder',async()=>{
 const f=fixture();try{
  const first=await(await f.route(request())).json(),retry=await(await f.route(request())).json();assert.equal(retry.code,first.code);
  assert.equal((await f.route(request(undefined,discovery(uuidB)))).status,409);
  assert.equal(f.pool.row.account,accountA);assert.equal(f.pool.balance,0);
 }finally{f.db.close();}
});
test('the winner can reissue a lost code and the previous code becomes invalid',async()=>{
 const f=fixture();try{
  const first=await(await f.route(request())).json();const second=await(await f.route(request(undefined,discovery(uuidA,'b'.repeat(64))))).json();assert.notEqual(second.code,first.code);
  assert.equal((await f.route(request('/api/quest/rewards/claim',{code:first.code}))).status,409);
  assert.equal((await f.route(request('/api/quest/rewards/claim',{code:second.code}))).status,200);assert.equal(f.pool.balance,1000);
 }finally{f.db.close();}
});
test('unlinked or disallowed discoveries cannot reserve the global reward',async()=>{
 const f=fixture();try{await f.db.prepare('DELETE FROM site_game_links WHERE user_id=?').bind(accountA).run();assert.equal((await f.route(request())).status,403);assert.equal(f.pool.calls.length,0);}finally{f.db.close();}
 const denied=fixture({nodes:[]});try{assert.equal((await denied.route(request())).status,403);assert.equal(denied.pool.calls.length,0);}finally{denied.db.close();}
});
test('website redemption requires the real session, same origin and current verified Minecraft identity',async()=>{
 const f=fixture();try{
  assert.equal((await f.route(request('/api/quest/rewards/claim',{code:'DRQ-'+'a'.repeat(40)},{origin:'https://attacker.invalid'}))).status,403);
  await f.db.prepare('DELETE FROM site_game_links WHERE user_id=?').bind(accountA).run();assert.equal((await f.route(request('/api/quest/rewards/claim',{code:'DRQ-'+'a'.repeat(40)}))).status,403);assert.equal(f.pool.calls.length,0);
 }finally{f.db.close();}
 const anonymous=fixture({user:null});try{assert.equal((await anonymous.route(request('/api/quest/rewards'))).status,401);}finally{anonymous.db.close();}
});
test('another account, transferred UUID or changed current link cannot redeem a bound code',async()=>{
 const pool=modelPool(),a=fixture({pool}),b=fixture({pool,user:accountB});try{
  const {code}=await(await a.route(request())).json();assert.equal((await b.route(request('/api/quest/rewards/claim',{code,uuid:uuidA,userId:accountA}))).status,409);
  await a.db.prepare('UPDATE site_game_links SET minecraft_uuid=? WHERE user_id=?').bind(uuidB,accountA).run();assert.equal((await a.route(request('/api/quest/rewards/claim',{code}))).status,409);assert.equal(pool.balance,0);
 }finally{a.db.close();b.db.close();}
});
test('simultaneous claim retries credit exactly once and only pass server-derived account/UUID/hash to PostgreSQL',async()=>{
 const f=fixture();try{
  const {code}=await(await f.route(request())).json();const responses=await Promise.all([f.route(request('/api/quest/rewards/claim',{code,amount:999999,uuid:uuidB})),f.route(request('/api/quest/rewards/claim',{code:code.toLowerCase()}))]);
  const results=await Promise.all(responses.map(r=>r.json()));assert.deepEqual(results.map(r=>r.credited).sort(),[false,true]);assert.equal(f.pool.balance,1000);
  const calls=f.pool.calls.filter(c=>c.text.includes('dune_quest_claim'));assert(calls.every(c=>JSON.stringify(c.values)===JSON.stringify([accountA,uuidA,sha(code)])));
  const again=await(await f.route(request())).json();assert.equal(again.redeemed,true);assert(!again.code);
 }finally{f.db.close();}
});
test('private status exposes only this account flags and no code, winner or proof',async()=>{
 const pool=modelPool(),a=fixture({pool}),b=fixture({pool,user:accountB});try{
  assert.equal((await(await a.route(request('/api/quest/rewards'))).json()).available,true);await a.route(request());
  const own=await(await a.route(request('/api/quest/rewards'))).json(),other=await(await b.route(request('/api/quest/rewards'))).json();assert.equal(own.discovered,true);assert.equal(other.discovered,false);assert.equal(other.available,false);
  assert.deepEqual(Object.keys(own).sort(),['amount','available','discovered','linked','redeemed']);
 }finally{a.db.close();b.db.close();}
});
test('claim attempts are bounded and database outages do not expose secrets or create a fallback credit',async()=>{
 const f=fixture();try{
  for(let n=0;n<10;n++)assert.equal((await f.route(request('/api/quest/rewards/claim',{code:'bad'}))).status,400);
  assert.equal((await f.route(request('/api/quest/rewards/claim',{code:'bad'}))).status,429);
  f.pool.fail=true;const result=await f.route(request());assert.equal(result.status,503);assert(!(await result.text()).includes('private-db-secret'));assert.equal(f.pool.balance,0);
 }finally{f.db.close();}
});
test('SQL restricts credit to a fixed 1000-Dunemark globally unique claim and grants no broad CP wallet write',async()=>{
 const sql=await readFile(new URL('../control-panel/quest-rewards.sql',import.meta.url),'utf8');
 assert.match(sql,/quest_id TEXT PRIMARY KEY CHECK \(quest_id='imperial-portal-eye-ra-v1'\)/);
 assert.match(sql,/FOR UPDATE/g);assert.match(sql,/balance=wallet\.balance\+1000\.00/);assert.match(sql,/redeemed_at IS NOT NULL/);
 assert.match(sql,/SECURITY DEFINER SET search_path=pg_catalog/g);assert.match(sql,/REVOKE ALL ON FUNCTION[\s\S]+FROM PUBLIC/);
 assert(!/GRANT[^;]*(?:INSERT|UPDATE|DELETE)[^;]*TO dune_cp/.test(sql));
 assert(!/EXECUTE\s+['"]/.test(sql));assert(!/p_amount/.test(sql));
});
