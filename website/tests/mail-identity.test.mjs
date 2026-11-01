import test from 'node:test';import assert from 'node:assert/strict';
import {createMailIdentityRoute,mailSignature,mailTemplate,smartReplyTemplate} from '../node/mail-identity.mjs';
import {scryptSync} from 'node:crypto';
test('SmartTemplates reply uses canonical identity and cursor without fabricating quoted correspondence',async()=>{
 const user={id:'self',username:'self'},branding={rankLabel:'Support',seals:[{url:'https://www.dunerealms.com/assets/seals/support.png',label:'Support'}]};
 const reply=smartReplyTemplate(user,'self@example.org',branding);
 assert.equal((reply.html.match(/%cursor%/g)||[]).length,1);assert(reply.html.includes('support.png'));assert(!reply.html.includes('<blockquote'));assert(!reply.html.includes('Write your reply here.'));
 for(const mode of ['new','reply'])assert(!mailTemplate(user,'self@example.org',branding,mode).html.includes('%cursor%'));
 const db={prepare:sql=>({bind:()=>({first:async()=>sql.includes('site_sessions')?user:{mailbox_address:'self@example.org',verified_at:1}})})};
 const route=createMailIdentityRoute(db,{brandingFor:async()=>branding,broker:async()=>({active:true,address:'self@example.org'})});
 const url='https://cp.dunerealms.com/api/mail/template?mode=reply&format=smarttemplates';
 assert.equal((await route(new Request(url))).status,401);
 const response=await route(new Request(url,{headers:{Cookie:'__Host-dune_session='+'a'.repeat(43)}}));assert.match(response.headers.get('Content-Type'),/^text\/html/);assert.equal(await response.text(),reply.html);
});
test('confirmed Unimatrix mail identity overrides only that immutable account and mailbox',()=>{
 const account={id:'f3ffda37-01e4-41ea-b89d-fbeda82e453e',username:'unimatrix999',display_name:'websitetest'};
 const signature=mailSignature(account,'unimatrix@dunerealms.com');assert(signature.text.includes('\nUnimatrix\n'));assert(!signature.html.includes('websitetest'));assert(mailSignature({...account,id:'someone-else'},'unimatrix@dunerealms.com').text.includes('websitetest'));
});
test('mailbox lookup requires a session and returns only that session user mapping',async()=>{
 const token='a'.repeat(43);let bound;
 const db={prepare:sql=>({bind:(...args)=>({first:async()=>{if(sql.includes('site_sessions'))return {id:'self',username:'traveller'};bound=args;return {mailbox_address:'self@example.org',verified_at:1};}})})};
 const route=createMailIdentityRoute(db);
 assert.equal((await route(new Request('https://cp.dunerealms.com/api/mail/me'))).status,401);
 const r=await route(new Request('https://cp.dunerealms.com/api/mail/me?user=other',{headers:{Cookie:'__Host-dune_session='+token}}));
 assert.deepEqual(bound,['self']);assert.equal(r.headers.get('Cache-Control'),'no-store');assert.deepEqual(await r.json(),{linked:true,address:'self@example.org',webmailUrl:'https://www.lgbbenelux.org/webmail/',setupAvailable:false});
});
test('signature download is own active mailbox only, escapes identity and contains no credentials',async()=>{
 const calls=[];const db={prepare:sql=>({bind:(...args)=>({first:async()=>sql.includes('site_sessions')?{id:'self',username:'self',display_name:'<script>alert(1)</script>'}:{mailbox_address:'self@example.org',verified_at:1}})})};
 const route=createMailIdentityRoute(db,{brandingFor:async()=>({}),broker:async body=>{calls.push(body);return {active:true,address:'self@example.org'};}});
 const request=query=>new Request('https://cp.dunerealms.com/api/mail/signature'+query,{headers:{Cookie:'__Host-dune_session='+'a'.repeat(43)}});
 const response=await route(request('?user=victim'));assert.equal(response.status,200);assert.match(response.headers.get('Content-Disposition'),/^attachment/);assert.match(response.headers.get('Content-Security-Policy'),/sandbox/);const html=await response.text();assert(html.includes('self@example.org'));assert(!html.includes('<script>'));assert(html.includes('&lt;script&gt;'));assert(!html.includes('victim'));assert.deepEqual(calls,[{action:'status',userId:'self'}]);
 const json=await(await route(request('?format=json'))).json();assert(json.html.includes('width="220"'));assert(json.text.includes('self@example.org'));assert(!Object.hasOwn(json,'password'));
 const draft=await route(new Request('https://cp.dunerealms.com/api/mail/template',{headers:{Cookie:'__Host-dune_session='+'a'.repeat(43)}}));assert.equal(draft.headers.get('Content-Type'),'message/rfc822');const eml=await draft.text();assert(eml.includes('X-Unsent: 1\r\n'));assert(eml.includes('From: self@example.org\r\n'));assert(eml.includes('To:\r\nSubject:\r\n'));assert(eml.includes('multipart/alternative'));assert(eml.includes('Content-Transfer-Encoding: base64'));assert(!eml.includes('password'));
 assert(!/(?<!\r)\n/.test(eml));const encoded=eml.match(/Content-Type: text\/html; charset=utf-8\r\nContent-Transfer-Encoding: base64\r\n\r\n([A-Za-z0-9+/=\r\n]+?)\r\n--/)[1],decoded=Buffer.from(encoded.replace(/\s/g,''),'base64').toString('utf8');assert(decoded.includes('<body bgcolor="#0d211b"'));for(const tag of decoded.match(/<(?:table|td)\b[^>]*>/g))assert(tag.includes('bgcolor='),tag);assert(decoded.includes('bgcolor="#1b382d"'));assert(decoded.includes('style="margin:0;color:#eee6d2">Write your message here.'));assert(decoded.includes('width="220"'));
 const blocked=createMailIdentityRoute(db,{broker:async()=>({active:true,address:'other@example.org'})});assert.equal((await blocked(request(''))).status,403);
});
test('mail password change requires origin and reauthentication; broker target is always the session user',async()=>{
 const password='website test password 123',salt='test-salt',calls=[],audit=[];
 const db={prepare:sql=>({bind:(...args)=>({first:async()=>sql.includes('site_sessions')?{id:'self'}:sql.includes('site_mail_identities')?{mailbox_address:'self@example.org',verified_at:1}:{password_salt:salt,password_hash:scryptSync(password,salt,64).toString('hex')},run:async()=>{audit.push(args);return {};}})})};
 const route=createMailIdentityRoute(db,{broker:async body=>{calls.push(body);return {active:true};}});
 const req=(body,origin='https://cp.dunerealms.com')=>new Request('https://cp.dunerealms.com/api/mail/password',{method:'POST',headers:{Origin:origin,Cookie:'__Host-dune_session='+'a'.repeat(43),'Content-Type':'application/json'},body:JSON.stringify(body)});
 assert.equal((await route(req({currentPassword:password,newPassword:'new mailbox password'},'https://evil.example'))).status,403);
 assert.equal((await route(req({currentPassword:'wrong password',newPassword:'new mailbox password'}))).status,403);assert.equal(calls.length,0);
 const response=await route(req({currentPassword:password,useSamePassword:true,userId:'victim',mailbox:'victim@example.org'}));assert.equal(response.status,200);assert.deepEqual(calls,[{action:'set_password',userId:'self',password}]);assert(!JSON.stringify(audit).includes(password));assert(!(await response.text()).includes(password));
});
