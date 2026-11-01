import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {PORTAL_SCRIPT} from '../scripts/portal-navigation.mjs';
function fixture({embedded=false,missing=false}={}) {
 const handlers={},classes=new Set();let focused=false;
 const attrs={}; const toggle={setAttribute:(k,v)=>attrs[k]=v,addEventListener:(k,v)=>handlers[k]=v,focus:()=>focused=true};
 const nav={id:'',classList:{contains:k=>classes.has(k),toggle:(k,v)=>v?classes.add(k):classes.delete(k)},querySelectorAll:()=>[]};
 const header={querySelector:s=>s==='nav'?nav:missing?null:toggle,addEventListener:(k,v)=>handlers[k]=v};
 const context={document:{querySelectorAll:()=>[header],getElementById:()=>nav},window:{top:1,self:embedded?2:1},URL};vm.runInNewContext(PORTAL_SCRIPT,context);
 return {header,nav,attrs,handlers,focused:()=>focused};
}
test('control identifies its menu and initial collapsed state',()=>{const f=fixture();assert.equal(f.attrs['aria-controls'],f.nav.id);assert.equal(f.attrs['aria-expanded'],'false');});
test('click opens, Escape closes and restores keyboard focus',()=>{const f=fixture();f.handlers.click({stopImmediatePropagation(){}});assert.equal(f.attrs['aria-expanded'],'true');let prevented=false;f.handlers.keydown({key:'Escape',preventDefault(){prevented=true}});assert.equal(f.attrs['aria-expanded'],'false');assert.equal(f.attrs['aria-label'],'Open navigation');assert.ok(f.focused()&&prevented);});
test('other keys and Escape on closed menu do not consume input',()=>{const f=fixture();f.handlers.keydown({key:'Enter',preventDefault(){throw Error('consumed')}});f.handlers.keydown({key:'Escape',preventDefault(){throw Error('consumed')}});assert.equal(f.focused(),false);});
test('embedded and incomplete headers are safe',()=>{assert.equal(fixture({embedded:true}).header.hidden,true);assert.equal(fixture({missing:true}).handlers.click,undefined);});
