import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
const code=await readFile(new URL('../worker/community-api.mjs',import.meta.url),'utf8');
async function fresh(){return import('data:text/javascript;base64,'+Buffer.from(code+'\n//'+Math.random()).toString('base64'));}
test('community verifies the new guild and exposes only public aggregate counts',async()=>{
 const original=globalThis.fetch;try{globalThis.fetch=async url=>{assert.equal(String(url),'https://discord.com/api/v10/invites/tRgcyZAxzt?with_counts=true');return Response.json({guild:{id:'1567229433033531462',name:'DuneRealms',private:'hidden'},approximate_member_count:2,approximate_presence_count:1});};const {handleCommunity}=await fresh();const body=await(await handleCommunity(new Request('https://www.dunerealms.com/api/community'))).json();assert.deepEqual(body,{status:'connected',name:'DuneRealms',inviteUrl:'https://discord.gg/tRgcyZAxzt',memberCount:2,onlineCount:1});}finally{globalThis.fetch=original;}
});
test('wrong guild and Discord failure retain the correct invitation without fabricated counts',async()=>{
 const original=globalThis.fetch;try{for(const response of [Response.json({guild:{id:'old-guild'}}),new Response('',{status:429})]){globalThis.fetch=async()=>response;const{handleCommunity}=await fresh();const r=await handleCommunity(new Request('https://www.dunerealms.com/api/community'));assert.deepEqual(await r.json(),{status:'unavailable',name:'DuneRealms',inviteUrl:'https://discord.gg/tRgcyZAxzt'});assert.equal(r.headers.get('cache-control'),'no-store');}}finally{globalThis.fetch=original;}
});
test('Discord page uses DuneRealms branding and the new invite',async()=>{const html=await readFile(new URL('../web/discord.html',import.meta.url),'utf8');assert(html.includes('https://discord.gg/tRgcyZAxzt'));assert(!/yousmp|jS4cx7eSGh/i.test(html));});
