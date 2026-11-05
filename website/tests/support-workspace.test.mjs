import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFileSync} from 'node:fs';
const script=readFileSync(new URL('../integrations/osticket/dune-support-workspace.js',import.meta.url),'utf8');
test('staff PJAX restores exactly one presentation hero without replacing native content',()=>{
 let callback;let hero={};let inserted=0;const form={csrf:'preserved'};
 const clone=()=>({cloneNode:clone,querySelector:()=>({textContent:''}),querySelectorAll:()=>[]});
 const content={form,querySelector:()=>hero,prepend:h=>{hero=h;inserted++;}};
 const doc={body:{classList:{contains:k=>k==='dune-support-staff'}},querySelector:q=>q==='#container'?{}:q==='#content'?content:clone()};
 vm.runInNewContext(script,{document:doc,location:{pathname:'/scp/tickets.php'},MutationObserver:class{constructor(c){callback=c}observe(){}}});
 hero=null;callback();callback();assert.equal(inserted,1);assert.equal(content.form,form);assert.equal(form.csrf,'preserved');
});
test('staff shell restores navigation and correct active state after changing PJAX route',()=>{
 let callback,heading=null;const native={csrf:'retained'},route={pathname:'/scp/settings.php'};
 const links=()=>['/scp/tickets.php','/index.php'].map(href=>({attrs:{href,'aria-current':'page'},getAttribute(k){return this.attrs[k]},setAttribute(k,v){this.attrs[k]=v},removeAttribute(k){delete this.attrs[k]}}));
 function clone(){const nav=links(),title={textContent:''},description={textContent:''};return{nav,title,description,cloneNode:clone,querySelector:q=>q==='h1'?title:description,querySelectorAll:()=>nav};}
 const content={native,querySelector:()=>heading,prepend:n=>{heading=n;}};
 vm.runInNewContext(script,{document:{body:{classList:{contains:k=>k==='dune-support-staff'}},querySelector:q=>q==='#container'?{}:q==='#content'?content:clone()},location:route,MutationObserver:class{constructor(c){callback=c}observe(){}}});
 callback();assert.equal(heading.title.textContent,'Staff workspace');assert(heading.nav.every(a=>!a.attrs['aria-current']));
 route.pathname='/scp/tickets.php';heading=null;callback();assert.equal(heading.title.textContent,'Ticket desk');assert.equal(heading.nav[0].attrs['aria-current'],'page');assert(!heading.nav[1].attrs['aria-current']);assert.equal(content.native,native);
});
test('client pages do not install staff PJAX observers',()=>{
 vm.runInNewContext(script,{document:{body:{classList:{contains:()=>false}}},MutationObserver:class{constructor(){throw Error('client observer')}}});
});
test('client ticket seals follow only the visible allowlisted department without changing rows or links',()=>{
 const makeRow=name=>{const images=[],cell={textContent:name,querySelector:()=>images[0],append:x=>images.push(x)},row={cells:[{},{},{},{},cell],classList:{add:k=>{row.marker=k}}};return{row,images};};
 const support=makeRow('Support'),unknown=makeRow('Unknown <script>'),rows=[support.row,unknown.row],table={querySelectorAll:()=>rows};
 const doc={body:{classList:{contains:k=>k==='dune-support-client'}},querySelector:q=>q==='#ticketTable'?table:null,createElement:()=>({})};
 vm.runInNewContext(script,{document:doc});vm.runInNewContext(script,{document:doc});
 assert.equal(support.images.length,1);assert.equal(support.images[0].src,'https://www.dunerealms.com/assets/seals/department-support-150.png');assert.equal(support.images[0].alt,'Support department seal');assert.equal(unknown.images.length,0);assert.equal(rows.length,2);assert.equal(support.row.cells[4].textContent,'Support');
});

