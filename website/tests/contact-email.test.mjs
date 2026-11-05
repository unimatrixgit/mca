import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash,scryptSync} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {LocalD1} from './d1-adapter.mjs';
import {contactSchema,createContactEmail,normalizeContactEmail} from '../node/contact-email.mjs';
const origin='https://cp.dunerealms.com',userId='11111111-1111-4111-8111-111111111111',token='x'.repeat(43),password='A private testing password',salt='a'.repeat(32);
function fixture(){
 const db=new LocalD1(`CREATE TABLE site_accounts(id TEXT,username TEXT,display_name TEXT,role TEXT,password_salt TEXT,password_hash TEXT);CREATE TABLE site_sessions(token_hash TEXT,user_id TEXT,expires_at INTEGER);CREATE TABLE cp_account_ranks(user_id TEXT,rank_key TEXT);${contactSchema}`);
 db.sqlite.prepare('INSERT INTO site_accounts VALUES(?,?,?,?,?,?)').run(userId,'traveller','Traveller','user',salt,scryptSync(password,salt,64).toString('hex'));
 db.sqlite.prepare('INSERT INTO site_sessions VALUES(?,?,?)').run(createHash('sha256').update(token).digest('hex'),userId,Date.now()+3600000);
 const calls=[],state={time:Date.now(),code:'a'.repeat(36),fail:null};
 const app=createContactEmail(db,{now:()=>state.time,makeCode:()=>state.code,broker:async b=>{calls.push(b);if(b.action===state.fail)throw Error('Transport unavailable');return{ok:true};}});
 const req=(body,headers={})=>app(new Request(origin+'/api/account/email',{method:body===undefined?'GET':'POST',headers:{Cookie:'__Host-dune_session='+token,Origin:origin,'Content-Type':'application/json',...headers},...(body===undefined?{}:{body:JSON.stringify(body)})}));
 return{db,calls,state,req,request:email=>req({action:'request',email,currentPassword:password}),confirm:()=>req({action:'confirm',code:state.code})};
}
test('email parsing rejects header injection, internal aliases and malformed addresses',()=>{
 assert.equal(normalizeContactEmail(' Traveller@Example.COM '),'traveller@example.com');
 for(const email of ['x\r\nBcc: y@example.com','a@accounts.dunerealms.invalid','a@localhost','a..b@example.org','a@example..org','<a@example.com>','a'.repeat(65)+'@example.com',null,{},'x@-example.com'])assert.throws(()=>normalizeContactEmail(email));
});
test('requires an authenticated account, correct origin and current password',async()=>{
 const f=fixture();try{
  assert.equal((await f.req(undefined,{Cookie:''})).status,401);
  assert.equal((await f.req({action:'request',email:'a@example.org',currentPassword:password},{Origin:'https://evil.example'})).status,403);
  assert.equal((await f.req({action:'request',email:'a@example.org',currentPassword:'wrong'})).status,403);
  assert.equal(f.calls.length,0);
 }finally{f.db.close();}
});
test('confirmation alone activates notification email; raw code is not stored or returned',async()=>{
 const f=fixture();try{
  const response=await f.request('traveller@example.org');assert.equal(response.status,200);assert(!(await response.text()).includes(f.state.code));
  let row=await f.db.prepare('SELECT * FROM site_contact_emails').first();assert.equal(row.address,null);assert.notEqual(row.code_hash,f.state.code);assert.equal(f.calls[0].action,'verify');
  assert.equal((await f.confirm()).status,200);row=await f.db.prepare('SELECT * FROM site_contact_emails').first();assert.equal(row.address,'traveller@example.org');assert.equal(row.code_hash,null);assert.equal(row.sync_pending,0);
  assert.deepEqual(f.calls[1],{action:'sync',userId,username:'traveller',email:'traveller@example.org',version:2});
  assert.equal((await f.confirm()).status,400);
 }finally{f.db.close();}
});
test('wrong-code attempts are bounded even when the sixth attempt uses the correct code',async()=>{
 const f=fixture();try{await f.request('traveller@example.org');for(let i=0;i<5;i++)assert.equal((await f.req({action:'confirm',code:'b'.repeat(36)})).status,400);assert.equal((await f.confirm()).status,400);assert.equal(f.calls.length,1);}finally{f.db.close();}
});
test('expired and superseded codes cannot activate an address',async()=>{
 const f=fixture();try{await f.request('first@example.org');f.state.time+=900001;assert.equal((await f.confirm()).status,400);f.state.code='b'.repeat(36);await f.request('second@example.org');assert.equal((await f.req({action:'confirm',code:'a'.repeat(36)})).status,400);assert.equal((await f.confirm()).status,200);assert.equal((await(await f.req()).json()).email,'second@example.org');}finally{f.db.close();}
});
test('resend cooldown and persistent hourly budget apply across handler instances',async()=>{
 const f=fixture();try{
  await f.request('first@example.org');assert.equal((await f.request('other@example.org')).status,429);
  async function requestFresh(){const app=createContactEmail(f.db,{now:()=>f.state.time,broker:async()=>({ok:true})});return app(new Request(origin+'/api/account/email',{method:'POST',headers:{Cookie:'__Host-dune_session='+token,Origin:origin,'Content-Type':'application/json'},body:JSON.stringify({action:'request',email:'first@example.org',currentPassword:password})}));}
  for(let i=0;i<4;i++){f.state.time+=60001;assert.equal((await requestFresh()).status,200);}
  f.state.time+=60001;assert.equal((await requestFresh()).status,429);
 }finally{f.db.close();}
});test('mail failure invalidates pending code and preserves the former verified address',async()=>{
 const f=fixture();try{await f.request('first@example.org');await f.confirm();f.state.time+=60001;f.state.fail='verify';f.state.code='c'.repeat(36);assert.equal((await f.request('other@example.org')).status,503);assert.equal((await f.confirm()).status,400);assert.equal((await(await f.req()).json()).email,'first@example.org');}finally{f.db.close();}
});
test('native sync failure is honest and retryable without reusing the verification code',async()=>{
 const f=fixture();try{await f.request('first@example.org');f.state.fail='sync';assert.equal((await f.confirm()).status,202);assert.equal((await(await f.req()).json()).syncPending,true);assert.equal((await f.confirm()).status,400);f.state.fail=null;assert.equal((await f.req({action:'sync'})).status,200);assert.equal((await(await f.req()).json()).syncPending,false);}finally{f.db.close();}
});
test('remove is password-protected, clears pending verification and restores native alias',async()=>{
 const f=fixture();try{await f.request('first@example.org');await f.confirm();assert.equal((await f.req({action:'remove',currentPassword:'wrong'})).status,403);assert.equal((await f.req({action:'remove',currentPassword:password})).status,200);const state=await(await f.req()).json();assert.equal(state.email,null);assert.equal(state.verified,false);assert.equal(f.calls.at(-1).email,null);}finally{f.db.close();}
});
test('concurrent confirmation grants at most one current revision',async()=>{
 const f=fixture();try{await f.request('first@example.org');const responses=await Promise.all([f.confirm(),f.confirm()]);assert.equal(responses.filter(r=>r.status===200).length,1);assert.equal(f.calls.filter(b=>b.action==='sync').length,1);}finally{f.db.close();}
});
test('memorial and disabled identities cannot manage contact email',async()=>{
 const f=fixture();try{await f.db.prepare("UPDATE site_accounts SET role='memorial'").run();assert.equal((await f.req()).status,401);await f.db.prepare("UPDATE site_accounts SET role='user',password_hash='!disabled'").run();assert.equal((await f.req()).status,401);}finally{f.db.close();}
});
test('public profile has no contact address; CP and native identity integrations are explicit',()=>{
 const profile=readFileSync(new URL('../node/profile.mjs',import.meta.url),'utf8');assert(!profile.includes('site_contact_emails'));
 const server=readFileSync(new URL('../control-panel/server.mjs',import.meta.url),'utf8');assert(server.includes("if(path==='/api/account/email')return contactEmail(request)"));assert(server.includes('await initializeContactEmails(db)'));
 const ui=readFileSync(new URL('../control-panel/public/panel.js',import.meta.url),'utf8');assert(ui.includes('const work=[contactEmailSettings()]'));assert(ui.includes("document.getElementById('password-form').after(host)"));
 const auth=readFileSync(new URL('../integrations/osticket/authenticate.php',import.meta.url),'utf8');assert(auth.includes("$mapped=User::lookupByEmail($identity['id'].'@accounts.dunerealms.invalid')"));assert(auth.includes('$mapped->getId()!==$user->getId()'));
});

