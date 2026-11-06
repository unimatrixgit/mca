// Opt-in actual-script/real-DOM regression; the navigation sink makes no requests.
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const test=require('node:test'),assert=require('node:assert/strict');
if(!process.env.DUNE_SUPPORT_DOM_QA)throw Error('Set DUNE_SUPPORT_DOM_QA to a separate QA-only jsdom prefix');
const {JSDOM}=require(path.resolve(process.env.DUNE_SUPPORT_DOM_QA,'node_modules/jsdom'));
const source=fs.readFileSync(process.env.DUNE_APPEAL_ROUTING_SOURCE||path.join(__dirname,'dune-appeal-routing.js'),'utf8');
const select='<select id="topicId"><option value="1">Other</option><option value="2">Appeal</option><option value="3">Support</option></select>';
function fixture(t,html=select+'<ul id="nav"></ul>'){
 const dom=new JSDOM('<!doctype html><body>'+html,{url:'https://support-qa.invalid/'});
 t.after(()=>dom.window.close());
 const calls=[],document=dom.window.document,context=vm.createContext({document,location:{assign:url=>calls.push(url)}});
 const load=()=>vm.runInContext(source,context,{filename:'dune-appeal-routing.js'});
 const change=value=>{const n=document.getElementById('topicId');n.value=value;n.dispatchEvent(new dom.window.Event('change',{bubbles:true}));};
 return{document,calls,context,load,change};
}
test('only the existing appeal topic redirects; loading alone does not navigate',t=>{
 const f=fixture(t);f.load();assert.deepEqual(f.calls,[]);
 f.change('1');f.change('3');assert.deepEqual(f.calls,[]);
 f.change('2');assert.deepEqual(f.calls,['/appeal.php']);
 assert.equal(f.document.querySelectorAll('#nav [data-ban-appeal]').length,1);
 assert.equal(f.document.querySelector('#nav a').getAttribute('href'),'/appeal.php');
});
test('five executions share a realm without redeclaration, duplicate links or duplicate listeners',t=>{
 const f=fixture(t);for(let i=0;i<5;i++)f.load();
 f.change('2');assert.deepEqual(f.calls,['/appeal.php']);
 f.change('1');f.change('2');assert.deepEqual(f.calls,['/appeal.php','/appeal.php']);
 assert.equal(f.document.querySelectorAll('[data-ban-appeal]').length,1);
 assert.equal(vm.runInContext('typeof topic',f.context),'undefined');
 assert.equal(vm.runInContext('typeof nav',f.context),'undefined');
});
test('unrelated page lexical globals remain untouched',t=>{
 const f=fixture(t);vm.runInContext('const topic="native-topic";const nav="native-nav";',f.context);
 f.load();f.load();f.change('2');assert.deepEqual(f.calls,['/appeal.php']);
 assert.equal(vm.runInContext('topic',f.context),'native-topic');
 assert.equal(vm.runInContext('nav',f.context),'native-nav');
});
test('missing optional topic and navigation nodes are safe',t=>{
 for(const html of['','<ul id="nav"></ul>',select]){
  const f=fixture(t,html);f.load();f.load();
  if(f.document.getElementById('topicId')){f.change('2');assert.deepEqual(f.calls,['/appeal.php']);}
  else assert.deepEqual(f.calls,[]);
 }
});
test('an existing appeal navigation link is preserved',t=>{
 const f=fixture(t,select+'<ul id="nav"><li><a data-ban-appeal href="/appeal.php?from=help">Existing appeal</a></li></ul>');
 f.load();f.load();assert.equal(f.document.querySelectorAll('[data-ban-appeal]').length,1);
 assert.equal(f.document.querySelector('#nav a').getAttribute('href'),'/appeal.php?from=help');
 assert.equal(f.document.querySelector('#nav a').textContent,'Existing appeal');
});
test('a newly rendered select binds once and retains the same topic policy',t=>{
 const f=fixture(t);f.load();const holder=f.document.createElement('div');holder.innerHTML=select;
 f.document.getElementById('topicId').replaceWith(holder.firstChild);
 f.load();f.load();f.change('3');assert.deepEqual(f.calls,[]);
 f.change('2');assert.deepEqual(f.calls,['/appeal.php']);
});
