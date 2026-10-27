import {test} from 'node:test';import assert from 'node:assert/strict';import {createMarketBridge} from '../market-bridge.mjs';
test('market bridge requires a private bearer before reading listings',async()=>{let calls=0;const h=createMarketBridge({readToken:'x'.repeat(40),pool:{query:async()=>{calls++;return{rows:[{id:'1',seller:'Trader',quantity:2,price:'25.00'}]};}}});
 const url='https://cp.dunerealms.com/internal/exchange/listings';assert.equal((await h(new Request(url))).status,401);assert.equal(calls,0);
 const r=await h(new Request(url,{headers:{Authorization:'Bearer '+'x'.repeat(40)}}));assert.equal((await r.json()).listings.length,1);assert.equal(calls,1);assert.match(r.headers.get('Cache-Control'),/no-store/);
});
test('market bridge refuses weak configuration',()=>assert.throws(()=>createMarketBridge({readToken:'short'})));
