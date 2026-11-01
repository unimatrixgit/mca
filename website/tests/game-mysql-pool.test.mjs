import test from 'node:test';
import assert from 'node:assert/strict';
import {mysqlGameQuery,openGameMysqlPool} from '../node/game-mysql-pool.mjs';
test('game reader preserves identity and repeated cursor values without SQL interpolation',()=>{
 const q=mysqlGameQuery('SELECT id::text,price::text FROM dune_exchange_sales WHERE seller=$1 AND ($2::bigint IS NULL OR id<$2::bigint) ORDER BY id DESC LIMIT 21',["quoted' UUID",null]);
 assert.equal(q.sql,'SELECT CAST(id AS CHAR),CAST(price AS CHAR) FROM dune_exchange_sales WHERE seller=? AND (? IS NULL OR id<?) ORDER BY id DESC LIMIT 21');
 assert.deepEqual(q.values,["quoted' UUID",null,null]);assert.equal(q.sql.includes('UUID'),false);
});
test('MySQL aliases preserve public response shape and quest operations stay fixed',()=>{
 assert.equal(mysqlGameQuery('SELECT display_name AS "displayName" FROM dune_exchange_listings').sql,'SELECT display_name AS `displayName` FROM dune_exchange_listings');
 const q=mysqlGameQuery('SELECT outcome FROM public.dune_quest_claim($1::uuid,$2::uuid,$3::text)',['a','b','c']);assert.equal(q.sql,'CALL dune_quest_claim(?,?,?)');assert.equal(q.procedure,true);
 for(const text of ['UPDATE dune_players SET solaris=1','DELETE FROM dune_players','SELECT 1; DROP TABLE x','SELECT 1 -- comment'])assert.throws(()=>mysqlGameQuery(text));
 assert.throws(()=>mysqlGameQuery('SELECT * FROM dune_players WHERE uuid=$2',['one']));
});
test('procedure result normalization retains booleans and close releases pool',async()=>{
 let ended=false,seen;
 const fake={createPool:()=>({query:async q=>{seen=q;return [[[ {available:1,discovered:0,redeemed:0} ],{}]];},end:async()=>{ended=true;},on(){}})};
 const pool=await openGameMysqlPool('mysql://reader:secret@127.0.0.1/dunegameplay',{mysql:fake});
 assert.deepEqual((await pool.query({text:'SELECT available,discovered,redeemed FROM public.dune_quest_status($1::uuid,$2::uuid)',values:['a','b']})).rows,[{available:true,discovered:false,redeemed:false}]);
 assert.deepEqual(seen.values,['a','b']);assert.equal(seen.timeout,5000);await pool.end();assert.equal(ended,true);
});
