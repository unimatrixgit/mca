import test from 'node:test';import assert from 'node:assert/strict';import {readFileSync} from 'node:fs';import vm from 'node:vm';
test('parent resizes only the exact trusted shop iframe and rejects forged heights',()=>{
 const events={},sent=[],attributes={};const child={postMessage:(...v)=>sent.push(v)};const frame={contentWindow:child,style:{},addEventListener(){},setAttribute:(k,v)=>attributes[k]=v};
 vm.runInNewContext(readFileSync(new URL('../web/shop-embed.js',import.meta.url),'utf8'),{document:{querySelector:()=>frame},window:{addEventListener:(n,f)=>events[n]=f},location:{href:'https://www.dunerealms.com/shop.html?category=cloud-hosting'},URL});
 assert.equal(frame.src,'https://cp.dunerealms.com/store/shop/categories/cloud-hosting');assert.equal(sent[0][1],'https://cp.dunerealms.com');
 for(const e of [{origin:'https://evil.invalid',source:child},{origin:'https://cp.dunerealms.com',source:{}}])events.message({...e,data:{type:'dune-shop-size',height:1800}});
 assert.equal(frame.style.height,undefined);
 for(const height of [NaN,Infinity,'1800',-1,999999])events.message({origin:'https://cp.dunerealms.com',source:child,data:{type:'dune-shop-size',height}});
 assert.equal(frame.style.height,undefined);
 events.message({origin:'https://cp.dunerealms.com',source:child,data:{type:'dune-shop-size',height:1800}});assert.equal(frame.style.height,'1800px');assert.equal(attributes.scrolling,'no');
 events.message({origin:'https://cp.dunerealms.com',source:child,data:{type:'dune-shop-size',height:700}});assert.equal(frame.style.height,'700px');
});
test('child measures only after authenticated parent handshake and updates changed content',()=>{
 const events={},messages=[],frames=[];let height=1200,resize;const parent={postMessage:(...v)=>messages.push(v)};
 vm.runInNewContext(readFileSync(new URL('../integrations/azuriom/dune-shop-embed-size.js',import.meta.url),'utf8'),{window:{parent,addEventListener:(n,f)=>events[n]=f},document:{body:{getBoundingClientRect:()=>({height})},head:{append(){}},createElement:()=>({}),documentElement:{classList:{add(){}}},addEventListener(){}},ResizeObserver:class{constructor(f){resize=f;}observe(){}},requestAnimationFrame:f=>frames.push(f)});
 const flush=()=>{while(frames.length)frames.shift()();};resize();flush();assert.equal(messages.length,0);
 events.message({origin:'https://evil.invalid',source:parent,data:{type:'dune-shop-measure'}});flush();assert.equal(messages.length,0);
 events.message({origin:'https://www.dunerealms.com',source:parent,data:{type:'dune-shop-measure'}});flush();assert.equal(messages[0][0].height,1200);
 resize();flush();assert.equal(messages.length,1);height=2300;resize();flush();assert.equal(messages[1][0].height,2300);assert.equal(messages[1][1],'https://www.dunerealms.com');
});
