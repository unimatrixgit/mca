import test from 'node:test';
import assert from 'node:assert/strict';
import {mountInventory} from '../control-panel/public/inventory/inventory-view.mjs';

class Element {
 constructor(tag){this.tagName=tag;this.children=[];this.attributes={};this.dataset={};this.style={};this.hidden=false;this.text='';this.className='';this.classList={add:n=>{this.className+=' '+n;},toggle:(n,on)=>{const s=new Set(this.className.split(' '));on?s.add(n):s.delete(n);this.className=[...s].join(' ');}};}
 set textContent(v){this.text=String(v);this.children=[];}get textContent(){return this.text+this.children.map(n=>n.textContent).join(' ');}
 append(...nodes){for(const n of nodes){n.remove();n.parentElement=this;this.children.push(n);}}
 remove(){if(this.parentElement)this.parentElement.children=this.parentElement.children.filter(n=>n!==this);this.parentElement=null;}
 replaceChildren(...nodes){for(const n of this.children)n.parentElement=null;this.children=[];this.text='';this.append(...nodes);}
 setAttribute(k,v){this.attributes[k]=String(v);}removeAttribute(k){delete this.attributes[k];}
 querySelectorAll(){return [];}
}
globalThis.document={createElement:tag=>new Element(tag),createElementNS:(_,tag)=>new Element(tag)};
const settle=()=>new Promise(r=>setImmediate(r));
const nodes=(root,predicate)=>[...(predicate(root)?[root]:[]),...root.children.flatMap(n=>nodes(n,predicate))];
function fixture(request){const host=new Element('section'),root=new Element('div'),dock=new Element('div'),form=new Element('form');dock.append(form);host.append(root,dock);const view=mountInventory(root,{shopDock:dock,request});return{host,root,dock,form,view};}

test('Shop preserves the same private reward form across tab switches, refresh and clear',async()=>{
 const f=fixture(async()=>({status:'ready',snapshot:{inventory:[],hotbar:[]}}));await settle();
 assert.deepEqual(nodes(f.root,n=>n.attributes.role==='tab').map(n=>n.textContent),['Inventory','Shop','Exchange History']);
 f.view.selectTab('shop');assert.equal(f.dock.hidden,false);assert.equal(f.dock.children[0],f.form);
 f.view.selectTab('inventory');assert.equal(f.dock.parentElement,f.host);assert.equal(f.dock.hidden,true);
 await f.view.refresh();f.view.selectTab('shop');assert.equal(f.dock.children[0],f.form);
 f.view.clear();assert.equal(f.dock.parentElement,f.host);assert.equal(f.dock.hidden,true);assert.equal(f.dock.children[0],f.form);
});
test('Exchange ledger displays actual row references, prices and loaded totals',async()=>{
 const f=fixture(async path=>path==='/api/inventory'?{status:'ready',snapshot:{}}:{sales:[{id:'91',material:'DIAMOND',display_name:'Blue diamond',quantity:2,unit_price:'12.50',total_price:'25.00',origin:'arrakis',buyer_name:'Buyer',sold_at:'2026-10-29T10:00:00Z'}]});await settle();f.view.selectTab('history');await settle();
 const table=nodes(f.root,n=>n.tagName==='table')[0];assert.ok(table);assert.match(table.textContent,/Sale #91/);assert.match(table.textContent,/12[.,]50/);assert.match(table.textContent,/25[.,]00/);assert.match(f.root.textContent,/Totals cover the rows displayed, not your wallet balance/);assert.doesNotMatch(f.root.textContent,/running balance|transaction fee/i);
});
test('Late sales response cannot restore private data after account view is cleared',async()=>{
 let finish;const f=fixture(path=>path==='/api/inventory'?Promise.resolve({status:'ready',snapshot:{}}):new Promise(r=>{finish=r;}));await settle();f.view.selectTab('history');f.view.clear();finish({sales:[{display_name:'Private former account item'}]});await settle();assert.doesNotMatch(f.root.textContent,/Private former account/);
});
test('Shop remains available when the independent inventory service is unavailable',async()=>{
 const f=fixture(async()=>{throw Error('Temporarily unavailable');});await settle();f.view.selectTab('shop');assert.equal(f.dock.hidden,false);assert.equal(f.dock.children[0],f.form);
});
