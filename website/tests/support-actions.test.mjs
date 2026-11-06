import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

const script=readFileSync(new URL('../integrations/osticket/dune-support-actions.js',import.meta.url),'utf8');
function element({href='',danger=false,form=null}={}) {
 const classes=new Set();return {dataset:{duneAutoAction:'true'},classList:{contains:x=>classes.has(x),add:x=>classes.add(x)},
  getAttribute:name=>name==='href'?href:null,
  closest:selector=>selector==='#confirm-form'||selector==='form'?form:selector==='li.danger'&&danger?{}:null};
}
function run(elements,workspace=true) {
 let observer,options;const body={classList:{contains:()=>workspace}};
 vm.runInNewContext(script,{document:{body,documentElement:{},querySelectorAll:()=>elements},queueMicrotask,
  MutationObserver:class {constructor(callback){observer=callback;}observe(_root,config){options=config;}}});
 return {refresh:async()=>{observer();await Promise.resolve();},get options(){return options;}};
}
test('native destructive links retain distinct danger semantics and unban is approval',()=>{
 const remove=element({href:'#tickets/7/status/delete'}),menu=element({danger:true}),ban=element({href:'#banemail'}),unban=element({href:'#unbanemail'}),other=element({href:'#assign'});
 run([remove,menu,ban,unban,other]);
 for(const el of [remove,menu,ban])assert.equal(el.dataset.duneAction,'danger');
 assert.equal(unban.dataset.duneAction,'approve');assert.equal(other.dataset.duneAction,'admin');
 assert.equal(remove.dataset.duneAutoAction,undefined);assert.equal(remove.classList.contains('dune-button'),true);
});
test('a reused native confirmation follows the current hidden action instead of stale button text',async()=>{
 const action={value:'delete'},form={querySelector:()=>action},confirm=element({form});const runtime=run([confirm]);
 assert.equal(confirm.dataset.duneAction,'danger');action.value='changeuser';await runtime.refresh();assert.equal(confirm.dataset.duneAction,'admin');
 action.value='delete';await runtime.refresh();assert.equal(confirm.dataset.duneAction,'danger');
 assert.deepEqual(Array.from(runtime.options.attributeFilter),['style','hidden','value']);
});
test('native popup/PJAX controls added later are included; unrelated pages are untouched',async()=>{
 const elements=[];const runtime=run(elements);const added=element({danger:true});elements.push(added);await runtime.refresh();assert.equal(added.dataset.duneAction,'danger');
 const untouched=element({danger:true});const other=run([untouched],false);assert.equal(untouched.dataset.duneAction,undefined);assert.equal(other.options,undefined);
});
