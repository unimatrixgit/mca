import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createInventoryHandler} from '../inventory-api.mjs';
const uuid='00000000-0000-4000-8000-000000000001';
function setup({user={id:'account-a'},link=uuid,rows=[],fail=false}={}){
 const queries=[],links=[];
 return {queries,links,handler:createInventoryHandler({authenticate:async()=>user,
 verifiedMinecraftUuid:async id=>{links.push(id);return link;},
 pool:{query:async q=>{queries.push(q);if(fail)throw Error('password=SECRET');return {rows};}},logger:{error(){}}})};
}
const req=(suffix='',method='GET')=>new Request('https://cp.dunerealms.com/api/inventory'+suffix,{method});
test('requires authentication before resolving a link or querying SQL',async()=>{
 const s=setup({user:null}),r=await s.handler(req());assert.equal(r.status,401);assert.equal(s.links.length,0);assert.equal(s.queries.length,0);
});
test('unlinked accounts do not query game state',async()=>{
 const s=setup({link:null}),r=await s.handler(req());assert.equal((await r.json()).status,'unlinked');assert.equal(s.queries.length,0);
});
test('only the authenticated verified UUID is queried, with private no-store response',async()=>{
 const snapshot={version:1,hotbar:[],inventory:[]};const s=setup({rows:[{inventory_view:snapshot,session_open:true,inventory_updated_at:'2026-10-27T10:00:00Z'}]});
 const r=await s.handler(req());assert.equal(r.status,200);assert.deepEqual(s.links,['account-a']);assert.deepEqual(s.queries[0].values,[uuid]);assert.match(r.headers.get('Cache-Control'),/no-store/);assert.equal(r.headers.get('Vary'),'Cookie');assert.deepEqual((await r.json()).snapshot,snapshot);
});
test('cannot request another player by UUID or arbitrary parameters',async()=>{
 const s=setup();assert.equal((await s.handler(req('?uuid='+uuid))).status,400);assert.equal(s.queries.length,0);
});
test('rejects writes and ignores unrelated endpoints',async()=>{
 const s=setup();assert.equal((await s.handler(req('','POST'))).status,405);assert.equal(await s.handler(new Request('https://cp.dunerealms.com/api/other')),null);assert.equal(s.queries.length,0);
});
test('missing snapshots have an honest empty state',async()=>{
 const s=setup();assert.equal((await (await s.handler(req())).json()).status,'empty');
});
test('outage errors do not disclose SQL credentials',async()=>{
 const s=setup({fail:true}),r=await s.handler(req());assert.equal(r.status,503);assert.doesNotMatch(await r.text(),/SECRET|password/);
});
test('invalid verified link and unsupported version fail closed',async()=>{
 const s=setup({link:'not-uuid'});assert.equal((await s.handler(req())).status,503);assert.equal(s.queries.length,0);
 const t=setup({rows:[{inventory_view:{version:2}}]});assert.equal((await t.handler(req())).status,503);
});

import {createSalesHistoryHandler} from '../inventory-api.mjs';
test('sales history is paginated and belongs only to the verified seller',async()=>{
 const queries=[];const rows=Array.from({length:21},(_,i)=>({id:String(100-i),display_name:'Spice',quantity:1}));
 const handler=createSalesHistoryHandler({authenticate:async()=>({id:'owner'}),verifiedMinecraftUuid:async id=>{assert.equal(id,'owner');return uuid;},pool:{query:async q=>{queries.push(q);return{rows};}}});
 const r=await handler(new Request('https://cp.dunerealms.com/api/exchange/sales-history?before=200'));
 const data=await r.json();assert.equal(data.sales.length,20);assert.equal(data.nextCursor,'81');assert.deepEqual(queries[0].values,[uuid,'200']);assert.match(queries[0].text,/WHERE seller=\$1/);assert.match(r.headers.get('Cache-Control'),/no-store/);
 for(const suffix of ['?uuid='+uuid,'?before=-1','?before=1&before=2','?before=9999999999999999999'])assert.equal((await handler(new Request('https://cp.dunerealms.com/api/exchange/sales-history'+suffix))).status,400);
});
test('sales history refuses unauthenticated and unlinked account SQL reads',async()=>{
 for(const user of [null,{id:'owner'}]){let calls=0;const handler=createSalesHistoryHandler({authenticate:async()=>user,verifiedMinecraftUuid:async()=>null,pool:{query:async()=>{calls++;}}});
 const r=await handler(new Request('https://cp.dunerealms.com/api/exchange/sales-history'));assert.equal(r.status,user?200:401);assert.equal(calls,0);}
});
