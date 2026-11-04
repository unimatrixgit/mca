import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFileSync} from 'node:fs';
const script=readFileSync(new URL('../integrations/osticket/dune-support-workspace.js',import.meta.url),'utf8');
test('staff PJAX restores exactly one presentation hero without replacing native content',()=>{
 let callback;let hero={};let inserted=0;const form={csrf:'preserved'};
 const clone=()=>({cloneNode:clone,querySelector:()=>({textContent:''})});
 const content={form,querySelector:()=>hero,prepend:h=>{hero=h;inserted++;}};
 const doc={body:{classList:{contains:()=>true}},querySelector:q=>q==='#container'?{}:q==='#content'?content:clone()};
 vm.runInNewContext(script,{document:doc,location:{pathname:'/scp/tickets.php'},MutationObserver:class{constructor(c){callback=c}observe(){}}});
 hero=null;callback();callback();assert.equal(inserted,1);assert.equal(content.form,form);assert.equal(form.csrf,'preserved');
});
test('client pages do not install staff PJAX observers',()=>{
 vm.runInNewContext(script,{document:{body:{classList:{contains:()=>false}}},MutationObserver:class{constructor(){throw Error('client observer')}}});
});
