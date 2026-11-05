import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFileSync} from 'node:fs';
const source=readFileSync(new URL('../control-panel/public/panel.js',import.meta.url),'utf8');
class Element{
 constructor(tag,doc){this.tagName=tag;this.doc=doc;this.children=[];this.attrs={};this.textContent='';}
 append(...nodes){for(const n of nodes){if(n.parent)n.parent.children=n.parent.children.filter(x=>x!==n);n.parent=this;this.children.push(n);}}
 replaceChildren(...nodes){for(const n of this.children)n.parent=null;this.children=[];this.append(...nodes);}
 setAttribute(k,v){this.attrs[k]=String(v);}getAttribute(k){return this.attrs[k]??null;}
 after(node){this.parent.append(node);}focus(){this.doc.focused=this;}
}
function fixture(){
 const doc={getElementById(id){const find=n=>n.id===id?n:n.children.map(find).find(Boolean);return find(root);}},root=new Element('section',doc),passwordForm=new Element('form',doc);passwordForm.id='password-form';root.append(passwordForm);
 const el=(tag,text)=>{const e=new Element(tag,doc);if(text!==undefined)e.textContent=text;return e;};
 const state={data:{email:null,pendingEmail:null,syncPending:false},calls:[],post:async()=>({message:'Verification email sent. Paste its code below within 15 minutes.'}),failRefresh:false};
 const api=async(path,body)=>{assert.equal(path,'account/email');if(body===undefined){if(state.failRefresh)throw Error('Refresh unavailable');return state.data;}state.calls.push(body);return state.post(body);};
 const context={document:doc,el,api};vm.runInNewContext(source.slice(source.indexOf('function contactEmailFeedback'),source.indexOf("let mode='login'")),context);
 const all=(tag,n=root)=>n.children.flatMap(c=>[...(c.tagName===tag?[c]:[]),...all(tag,c)]);
 const setup=async()=>{await context.contactEmailSettings();const form=all('form')[1],inputs=all('input',form);inputs[0].value='traveller@example.org';inputs[1].value='test current password';return{form,password:inputs[1],button:all('button',form)[0],host:doc.getElementById('contact-email-settings')};};
 return{state,doc,context,all,setup};
}
test('send immediately shows local progress, prevents duplicate clicks and preserves visible success after refresh',async()=>{
 const f=fixture(),{form,button,host,password}=await f.setup();let finish;f.state.post=()=>new Promise(r=>{finish=r;});
 const task=form.onsubmit({preventDefault(){},submitter:button});assert.equal(button.disabled,true);assert.equal(button.textContent,'Sending verification email…');assert.equal(host._feedback.textContent,'Sending verification email…');assert.equal(host.getAttribute('aria-busy'),'true');
 f.state.data.pendingEmail='traveller@example.org';finish({message:'Verification email sent. Paste its code below within 15 minutes.'});await task;
 assert.equal(host._feedback.hidden,false);assert.equal(host._feedback.getAttribute('role'),'status');assert.match(host._feedback.textContent,/Verification email sent/);assert.equal(f.doc.focused,host._feedback);assert.equal(password.value,'');assert.equal(button.disabled,false);assert.equal(host.getAttribute('aria-busy'),'false');assert(f.doc.getElementById('contact-verification-code'));
});
test('server and transport errors are announced beside the form without a global notice',async()=>{
 const f=fixture(),{form,button,host,password}=await f.setup();f.state.post=async()=>{throw Error('Verification email could not be sent. Try again later.');};await form.onsubmit({preventDefault(){},submitter:button});assert.equal(host._feedback.getAttribute('role'),'alert');assert.match(host._feedback.textContent,/could not be sent/);assert.equal(host._feedback.hidden,false);assert.equal(f.doc.focused,host._feedback);assert.equal(password.value,'');assert.equal(button.textContent,'Send verification email');
});
test('accepted-send confirmation survives a failed settings refresh',async()=>{
 const f=fixture(),{form,button,host}=await f.setup();f.state.post=async()=>{f.state.failRefresh=true;return{message:'Verification email sent.'};};await form.onsubmit({preventDefault(){},submitter:button});assert.match(host._feedback.textContent,/Verification email sent/);assert.match(host._feedback.textContent,/could not refresh/);assert.equal(host._feedback.getAttribute('role'),'alert');
});
test('confirmation and retry keep native-sync warnings visible locally',async()=>{
 const f=fixture();f.state.data.pendingEmail='traveller@example.org';await f.context.contactEmailSettings();const verify=f.all('form')[2],confirm=f.all('button',verify)[0];f.doc.getElementById('contact-verification-code').value='a'.repeat(36);f.state.post=async()=>{f.state.data.syncPending=true;return{message:'Saved, but Support could not update its notification address.',syncPending:true};};await verify.onsubmit({preventDefault(){},submitter:confirm});const host=f.doc.getElementById('contact-email-settings');assert.match(host._feedback.textContent,/could not update/);assert.equal(host._feedback.getAttribute('role'),'alert');const retry=f.all('button').find(b=>b.textContent==='Retry Support sync');assert(retry);f.state.post=async()=>{f.state.data.syncPending=false;return{message:'Email verified. Support ticket notifications will use this address.'};};await retry.onclick();assert.match(host._feedback.textContent,/Email verified/);assert.equal(host._feedback.getAttribute('role'),'status');
});
