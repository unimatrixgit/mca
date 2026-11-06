import test from 'node:test';
import assert from 'node:assert/strict';
import {createAvatars} from '../node/avatar.mjs';
import {png,textureId,javaProfile} from './skin-fixtures.mjs';
const origin='https://cp.dunerealms.com';
function database(accounts){return{prepare(sql){assert(!sql.includes('site_profiles'),'roster must ignore uploaded avatar overrides');return{bind(name){this.name=name;return this},async first(){return Object.hasOwn(accounts,this.name)?{minecraft_uuid:accounts[this.name]}:null;}}}};}
test('roster username resolves Minecraft skin without exposing IDs or using overrides',async()=>{
 const uuid='99d2fe76-7977-4d49-bd39-e48fc8c648b8';const calls=[];
 const fetcher=async value=>{const url=String(value);calls.push(url);if(url.startsWith('https://sessionserver.mojang.com/'))return Response.json(javaProfile(uuid));assert.equal(url,'https://textures.minecraft.net/texture/'+textureId);return new Response(png,{headers:{'Content-Type':'image/png'}});};
 const avatar=createAvatars(database({staffmember:uuid}),{}, {fetcher});const response=await avatar(new Request(origin+'/api/avatars/roster/staffmember'));
 assert.equal(response.status,200);assert.equal(response.headers.get('content-type'),'image/png');assert.deepEqual(Buffer.from(await response.arrayBuffer()),png);assert.equal(calls.length,2);assert(!response.headers.has('location'));
});
test('unlinked staff receive face fallback; failed linked skin receives 64px crop-compatible Steve',async()=>{
 const avatar=createAvatars(database({unlinked:null,linked:'88d2fe76-7977-4d49-bd39-e48fc8c648b8'}),{}, {fetcher:async()=>new Response(null,{status:503})});
 const unlinked=await avatar(new Request(origin+'/api/avatars/roster/unlinked'));assert.equal(unlinked.status,200);assert.match(await unlinked.text(),/viewBox="0 0 8 8"/);
 const linked=await avatar(new Request(origin+'/api/avatars/roster/linked'));assert.equal(linked.status,200);assert.equal(linked.headers.get('content-type'),'image/svg+xml');const svg=await linked.text();assert.match(svg,/width="64" height="64" viewBox="0 0 64 64"/);assert.match(svg,/transform="translate\(8 8\)"/);
});
test('roster rejects malformed paths, unknown accounts and non-GET requests',async()=>{
 const avatar=createAvatars(database({}),{}, {fetcher:async()=>{throw Error('must not fetch')}});
 for(const path of ['/api/avatars/roster/UPPERCASE','/api/avatars/roster/ab','/api/avatars/roster/%27or1','/api/avatars/roster/name/extra','/api/avatars/roster/unknown'])assert.equal((await avatar(new Request(origin+path))).status,404);
 assert.equal((await avatar(new Request(origin+'/api/avatars/roster/name',{method:'POST'}))).status,405);
});
