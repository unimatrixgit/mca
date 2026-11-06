import test from 'node:test';
import assert from 'node:assert/strict';
import {createBanEvidence,evidenceInput} from '../control-panel/ban-evidence.mjs';
const origin='https://cp.dunerealms.com',id='00000000-0000-4000-8000-000000000001',banId='00000000-0000-4000-8000-000000000002';
const input=()=>({id,banId,title:'Witness statement',notes:'Observed incident and relevant context.',sourceUrl:'',file:null});
function rig({user={id:'staff',username:'Moderator'},view=true,manage=true,existing=null,fail=false}={}){
 const writes=[];const ban={id:banId,player_name:'Fixture player',scope:'caladan',reason:'Fixture',active:0};
 const db={prepare(sql){let values;return{bind(...v){values=v;return this;},async first(){if(sql.includes('FROM moderation_bans'))return ban;if(sql.includes('WHERE id=?'))return existing;return null;},async all(){return{results:[]};},async run(){writes.push({sql,values});}};},async batch(rows){if(fail)throw Error('audit unavailable');for(const r of rows)await r.run();}};
 const route=createBanEvidence(db,{CP_ORIGIN:origin},{authenticate:async()=>user,can:async(u,n)=>n==='web.bans.evidence'?manage:view});
 const request=(body=input(),requestOrigin=origin)=>route(new Request(origin+'/api/ban-evidence',{method:'POST',headers:{Origin:requestOrigin,'Content-Type':'application/json'},body:JSON.stringify(body)}));
 return{route,request,writes};
}
test('anonymous, missing viewer permission and revoked writer cannot add evidence',async()=>{
 for(const [options,status]of [[{user:null},401],[{view:false},403],[{manage:false},403]]){const r=rig(options);assert.equal((await r.request()).status,status);assert.equal(r.writes.length,0);}
});
test('cross-origin request is rejected before any mutation',async()=>{const r=rig();assert.equal((await r.request(input(),'https://other.test')).status,403);assert.equal(r.writes.length,0);});
test('retained bans accept append-only evidence with server-derived actor and audit',async()=>{const r=rig();assert.equal((await r.request({...input(),actor_id:'attacker',active:1})).status,201);assert.equal(r.writes.length,2);assert.equal(r.writes[0].values[2],'staff');assert.equal(r.writes[0].values[3],'Moderator');assert.equal(r.writes[1].values[3],'evidence-added');assert.ok(r.writes.every(w=>!/^UPDATE|^DELETE/.test(w.sql)));});
test('a retried identical submission does not add duplicate evidence; changed payload conflicts',async()=>{const r=rig({existing:{ban_id:banId,actor_id:'staff',title:input().title,notes:input().notes,source_url:'',file_sha256:null}});assert.equal((await r.request()).status,200);assert.equal((await r.request({...input(),notes:'Different statement'})).status,409);assert.equal(r.writes.length,0);});
test('unsafe links, empty evidence, HTML/SVG and oversized attachments are refused',()=>{
 for(const b of [{...input(),sourceUrl:'javascript:alert(1)'},{...input(),sourceUrl:'https://user:password@example.com/'},{...input(),notes:''},{...input(),file:{name:'bad.svg',base64:Buffer.from('<svg/>').toString('base64')}},{...input(),file:{name:'big.png',base64:'A'.repeat(7*1024*1024)}}])assert.throws(()=>evidenceInput(b));
});
test('attachment signature determines type and hash; filename cannot inject headers',()=>{const b=evidenceInput({...input(),file:{name:'../../proof\r\n.png',base64:Buffer.from('89504e470d0a1a0a','hex').toString('base64')}});assert.equal(b.mime,'image/png');assert.equal(b.name,'proof.png');assert.match(b.hash,/^[a-f0-9]{64}$/);});
test('read-only staff can see record but cannot add; invalid selectors are refused',async()=>{const r=rig({manage:false});const response=await r.route(new Request(origin+'/api/ban-evidence?banId='+banId));assert.equal(response.status,200);assert.equal((await response.json()).canAdd,false);assert.equal((await r.route(new Request(origin+'/api/ban-evidence?banId='+banId+'&actor=other'))).status,400);});
test('download always requires current staff access',async()=>{const r=rig({view:false});assert.equal((await r.route(new Request(origin+'/api/ban-evidence/file?id='+id))).status,403);});
test('audit failure cannot report a successful save',async()=>{const r=rig({fail:true});await assert.rejects(r.request(),/audit unavailable/);assert.equal(r.writes.length,0);});

 test('full 5 MB attachment validates without regex stack exhaustion',()=>{const bytes=Buffer.alloc(5*1024*1024);Buffer.from('89504e470d0a1a0a','hex').copy(bytes);const input=evidenceInput({id:'00000000-0000-4000-8000-000000000001',banId:'00000000-0000-4000-8000-000000000001',title:'Large attachment',notes:'',sourceUrl:'',file:{name:'proof.png',base64:bytes.toString('base64')}});assert.equal(input.bytes.length,bytes.length);assert.equal(input.mime,'image/png');});

test('evidence required, boundary and Unicode fields match the form contract',()=>{assert.equal(evidenceInput({...input(),title:'Witness ✓',notes:'沙漠\nContext'}).title,'Witness ✓');assert.equal(evidenceInput({...input(),title:'x'.repeat(120),notes:'x'.repeat(10000)}).notes.length,10000);for(const patch of [{title:'  '},{title:'xx'},{title:'x'.repeat(121)},{notes:'x'.repeat(10001)},{notes:'\u0000'},{id:'invalid'},{banId:42},{sourceUrl:'http://example.test'},{sourceUrl:'not a URL'}])assert.throws(()=>evidenceInput({...input(),...patch}));});
test('unreadable and oversized evidence requests cannot write or report success',async()=>{const f=rig();let r=await f.route(new Request(origin+'/api/ban-evidence',{method:'POST',headers:{Origin:origin,'Content-Type':'application/json'},body:'not JSON'}));assert.equal(r.status,400);assert.equal(f.writes.length,0);r=await f.route(new Request(origin+'/api/ban-evidence',{method:'POST',headers:{Origin:origin,'Content-Length':String(8*1024*1024)},body:'{}'}));assert.equal(r.status,413);assert.equal(f.writes.length,0);});
