import fs from 'node:fs';import vm from 'node:vm';import {randomUUID} from 'node:crypto';
import {readModerationResponse,unbanReasonError,evidenceDraftErrors} from '../control-panel/public/moderation-form-response.mjs';
export const banId='11111111-1111-4111-8111-111111111111';
export class Node{
 constructor(tag){this.tagName=tag.toUpperCase();this.children=[];this.dataset={};this.attributes={};this.listeners={};this.files=[];this._value='';this._text='';this.disabled=false;this.hidden=false;this.validationMessage='';}
 set value(value){this._value=value;if(this.type==='file'&&value==='')this.files=[];}get value(){return this._value;}
 set textContent(value){this._text=String(value);for(const node of this.children)node.parent=null;this.children=[];}get textContent(){return this._text+this.children.map(n=>n.textContent).join('');}
 get childNodes(){return this.children;}get isConnected(){return this.connected===true||!!this.parent?.isConnected;}
 append(...nodes){for(const node of nodes){if(node.parent)node.parent.children.splice(node.parent.children.indexOf(node),1);node.parent=this;this.children.push(node);}}
 replaceChildren(...nodes){this.textContent='';this.append(...nodes);}setAttribute(key,value){this.attributes[key]=String(value);}getAttribute(key){return this.attributes[key]??null;}
 addEventListener(key,fn){(this.listeners[key]??=[]).push(fn);}input(value){this.value=value;for(const fn of this.listeners.input||[])fn({target:this});}
 setCustomValidity(message){this.validationMessage=message;}reportValidity(){return !walk(this).some(n=>n.validationMessage||(n.required&&!n.disabled&&!n.value));}
 querySelector(selector){return walk(this).slice(1).find(n=>selector==='nav'?n.tagName==='NAV':selector==='button[type=submit]'?n.tagName==='BUTTON'&&n.type==='submit':false)||null;}
 scrollIntoView(){}focus(){}
}
export const walk=node=>[node,...node.children.flatMap(walk)];
export async function moderationFixture(kind,{mode='accepted',deferBootstrap=false,deferPost=false,deferReader=false,sourceOverride}={}){
 const dashboard=new Node('div');dashboard.connected=true;dashboard.append(new Node('nav'));const head=new Node('head');head.connected=true;const events={},readers=[],writes=[];let resolveBootstrap,resolvePost;
 const user={username:'Fixture staff',permissions:['web.menu.bans','web.bans.view','web.bans.manage','web.bans.evidence','web.tickets.staff','web.tickets.appeals']};
 const ban={id:banId,player_name:'Fixture player',minecraft_uuid:'22222222-2222-4222-8222-222222222222',reason:'Fixture only',banned_by:'Fixture staff',scope:'arrakis',active:1,created_text:'2026-11-06',expires_text:'forever',appeals:[]};
 const responseFor=body=>{if(mode==='network')throw Error('Fixture offline');if(mode==='html')return new Response('<html>Unavailable</html>',{status:502});if(mode==='rejected')return Response.json({message:'Permission revoked.'},{status:403});if(mode==='unknown')return Response.json({ok:true});return Response.json(kind==='register'?{ok:true,confirmed:true,banId,message:'Fixture removal confirmed.'}:{ok:true,id:body.id},{status:kind==='register'?200:201});};
 const fetcher=async(url,options={})=>{
  if(url==='/api/auth/session')return deferBootstrap?await new Promise(resolve=>{resolveBootstrap=()=>resolve(Response.json({user}));}):Response.json({user});
  if(options.method==='POST'){const body=JSON.parse(options.body);writes.push(body);return deferPost?await new Promise(resolve=>{resolvePost=()=>resolve(responseFor(body));}):responseFor(body);}
  if(url.startsWith('/api/ban-register'))return Response.json({bans:[ban],connected:true,globalConnected:false});
  if(url.startsWith('/api/ban-evidence'))return Response.json({ban,canAdd:true,evidence:[],ticketEvidence:[],page:0,hasMore:false});
  throw Error('Unexpected fixture route '+url);
 };
 const source=sourceOverride??fs.readFileSync(new URL('../control-panel/public/ban-'+(kind==='register'?'register':'evidence')+'.mjs',import.meta.url),'utf8');
 const code=source.replace(/^import .*;\r?\n/gm,'').replace('export async function renderEvidencePreview','async function renderEvidencePreview');
 const context={document:{getElementById:id=>id==='dashboard'?dashboard:null,createElement:tag=>new Node(tag),head,hidden:false,addEventListener(){}},window:{addEventListener:(key,fn)=>(events[key]??=[]).push(fn)},location:{hash:kind==='register'?'#bans':'#ban-evidence',href:'https://fixture.invalid/?banId='+banId+(kind==='register'?'#bans':'#ban-evidence')},URL,Date,Number,String,JSON,Error,crypto:{randomUUID},fetch:fetcher,readModerationResponse,unbanReasonError,evidenceDraftErrors,renderEvidencePreview(){},FileReader:class{constructor(){readers.push(this);}readAsDataURL(file){this.result='data:image/png;base64,iVBORw0KGgo=';if(!deferReader)queueMicrotask(()=>this.onload());}}};
 const initialization=vm.runInNewContext('(async()=>{'+code+'\n})()',context);
 if(!deferBootstrap)await initialization;await new Promise(resolve=>setImmediate(resolve));
 return{dashboard,head,user,writes,readers,initialization,all:predicate=>walk(dashboard).filter(predicate),emitSession:async detail=>{for(const listener of events['dune:session']||[])listener({detail});await new Promise(resolve=>setImmediate(resolve));},finishBootstrap:async()=>{resolveBootstrap();await initialization;await new Promise(resolve=>setImmediate(resolve));},finishPost:()=>resolvePost(),flush:()=>new Promise(resolve=>setImmediate(resolve))};
}
