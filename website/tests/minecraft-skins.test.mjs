import test from 'node:test';import assert from 'node:assert/strict';
import {crc32} from 'node:zlib';
import {createSkinResolver,skinIdentity,floodgateUuid,validateSkinPng,textureDescriptor} from '../node/minecraft-skins.mjs';
import {png,legacy,unsupported,javaUuid,otherUuid,textureId,textureValue,javaProfile,imageResponse} from './skin-fixtures.mjs';

test('Floodgate UUID and decimal XUID preserve all64bits and never infer platform from a name',()=>{
 for(const xuid of ['1','9007199254740993','18446744073709551615'])assert.equal(skinIdentity(floodgateUuid(xuid)).xuid,xuid);
 assert.equal(floodgateUuid('18446744073709551616'),null);assert.equal(floodgateUuid(9007199254740993),null);assert.equal(floodgateUuid('0'),null);assert.equal(floodgateUuid('01'),null);
 assert.equal(skinIdentity(javaUuid.toUpperCase()).type,'java');assert.equal(skinIdentity('00000000-0000-0000-0000-000000000000'),null);assert.equal(skinIdentity('.Player'),null);
});
test('Java regular, slim and legacy textures retain model metadata; HTTPS is forced without credentials or redirects',async()=>{
 for(const[model,bytes]of[['default',png],['slim',png],['default',legacy]]){
  const calls=[];const resolver=createSkinResolver({fetcher:async(url,options)=>{calls.push(String(url));assert.equal(options.redirect,'error');assert.equal(options.credentials,'omit');assert(options.signal);assert(!Object.keys(options.headers).some(k=>/cookie|authorization/i.test(k)));return String(url).includes('sessionserver')?Response.json(javaProfile(javaUuid,{model})):imageResponse(bytes);}});
  const skin=await resolver.resolve(javaUuid);assert.equal(skin.model,model);assert.deepEqual(skin.bytes,bytes);assert.equal(skin.height,bytes===legacy?32:64);assert.equal(calls[1],'https://textures.minecraft.net/texture/'+textureId);
  await resolver.resolve(javaUuid.toUpperCase());assert.equal(calls.length,2);
 }
});
test('Bedrock uses the precise XUID endpoint, converted model and matching texture ID; empty responses give honest fallback',async()=>{
 const xuid='9007199254740993',uuid=floodgateUuid(xuid);
 for(const model of ['default','slim']){
  const calls=[];const resolver=createSkinResolver({fetcher:async url=>{calls.push(String(url));return String(url).includes('api.geysermc.org')?Response.json({is_steve:model==='default',texture_id:textureId,value:textureValue({model})}):imageResponse(png);}});
  const skin=await resolver.resolve(uuid);assert.equal(skin.model,model);assert.equal(calls[0],'https://api.geysermc.org/v2/skin/'+xuid);assert(!calls.some(u=>u.includes('sessionserver')));
 }
 let count=0;const unavailable=createSkinResolver({fetcher:async()=>{count++;return Response.json({});}});assert.equal(await unavailable.resolve(uuid),null);assert.equal(await unavailable.resolve(uuid),null);assert.equal(count,1);
});
test('wrong Java profile, malformed base64, invalid models and inconsistent converted skins are rejected',async()=>{
 const wrong=[{...javaProfile(),id:otherUuid.replaceAll('-','')},javaProfile(javaUuid,{profileId:otherUuid.replaceAll('-','')}),{...javaProfile(),properties:[{name:'textures',value:'!invalid'}]},javaProfile(javaUuid,{model:'unknown'})];
 for(const profile of wrong){let calls=0;const resolver=createSkinResolver({fetcher:async()=>{calls++;return Response.json(profile);}});assert.equal(await resolver.resolve(javaUuid),null);assert.equal(calls,1);}
 for(const converted of [{is_steve:true,texture_id:'b'.repeat(64),value:textureValue()},{is_steve:true,texture_id:textureId,value:textureValue({model:'slim'})},{is_steve:'false',texture_id:textureId,value:textureValue()}]){
  const resolver=createSkinResolver({fetcher:async()=>Response.json(converted)});assert.equal(await resolver.resolve(floodgateUuid('9007199254740993')),null);
 }
});
test('unsafe texture targets, ports, userinfo, queries and fragments never cause an image fetch',async()=>{
 for(const url of ['https://127.0.0.1/texture/'+textureId,'https://textures.minecraft.net.evil/texture/'+textureId,'https://textures.minecraft.net:444/texture/'+textureId,'https://u:p@textures.minecraft.net/texture/'+textureId,'https://textures.minecraft.net/texture/'+textureId+'?next=x','https://textures.minecraft.net/texture/'+textureId+'#x','ftp://textures.minecraft.net/texture/'+textureId]){
  let calls=0;const resolver=createSkinResolver({fetcher:async()=>{calls++;return Response.json(javaProfile(javaUuid,{url}));}});assert.equal(await resolver.resolve(javaUuid),null);assert.equal(calls,1);
 }
 assert.throws(()=>textureDescriptor('a'.repeat(60001)));
});
test('PNG validation rejects damaged chunks, wrong dimensions, truncated or fake image data and legacy slim combinations',async()=>{
 assert.deepEqual(validateSkinPng(png),{width:64,height:64});assert.deepEqual(validateSkinPng(legacy),{width:64,height:32});
 const corrupted=Buffer.from(png);corrupted[40]^=1;
 for(const bytes of [unsupported,png.subarray(0,20),Buffer.concat([png,Buffer.from('extra')]),corrupted,Buffer.from([137,80,78,71,13,10,26,10,1])])assert.throws(()=>validateSkinPng(bytes));
 const resolver=createSkinResolver({fetcher:async url=>String(url).includes('sessionserver')?Response.json(javaProfile(javaUuid,{model:'slim'})):imageResponse(legacy)});assert.equal(await resolver.resolve(javaUuid),null);
});
test('animated PNG control chunks are rejected and model-only changes invalidate the opaque content version',async()=>{
 const data=Buffer.alloc(8);data.writeUInt32BE(1);const type=Buffer.from('acTL'),chunk=Buffer.alloc(20);chunk.writeUInt32BE(8);type.copy(chunk,4);data.copy(chunk,8);chunk.writeUInt32BE(crc32(Buffer.concat([type,data])),16);assert.throws(()=>validateSkinPng(Buffer.concat([png.subarray(0,33),chunk,png.subarray(33)])));
 let time=0,model='default';const resolver=createSkinResolver({now:()=>time,fetcher:async url=>String(url).includes('sessionserver')?Response.json(javaProfile(javaUuid,{model})):imageResponse(png)});
 const classic=await resolver.resolve(javaUuid);model='slim';time=300001;const slim=await resolver.resolve(javaUuid);assert.deepEqual(classic.bytes,slim.bytes);assert.notEqual(classic.hash,slim.hash);
});
test('oversized advertised and streaming bodies are cancelled before unbounded buffering',async()=>{
 for(const advertised of [true,false]){let cancelled=false,pulls=0;const body=new ReadableStream({pull(c){pulls++;c.enqueue(new Uint8Array(200000));},cancel(){cancelled=true;}});
  const resolver=createSkinResolver({fetcher:async url=>String(url).includes('sessionserver')?Response.json(javaProfile()):new Response(body,{headers:{'Content-Type':'image/png',...(advertised?{'Content-Length':'1000001'}:{})}})});
  assert.equal(await resolver.resolve(javaUuid),null);assert(cancelled);assert(pulls<10);
 }
});
test('timeouts, HTTP failures, wrong content types and followed redirects never yield a skin',async()=>{
 for(const make of [()=>{throw new DOMException('Timeout','TimeoutError');},()=>Response.json({},{status:503}),()=>new Response('{}',{headers:{'Content-Type':'text/html'}}),()=>{const r=Response.json(javaProfile());Object.defineProperty(r,'redirected',{value:true});return r;}]){
  const resolver=createSkinResolver({fetcher:async()=>make()});assert.equal(await resolver.resolve(javaUuid),null);
 }
});
test('concurrent same-identity lookups share work and bounded pending capacity does not start another provider request',async()=>{
 let release;const gate=new Promise(r=>release=r);let calls=0;
 const resolver=createSkinResolver({maxPending:1,fetcher:async url=>{calls++;if(String(url).includes('sessionserver')){await gate;return Response.json(javaProfile());}return imageResponse(png);}});
 const a=resolver.resolve(javaUuid),b=resolver.resolve(javaUuid);assert.equal(await resolver.resolve(otherUuid),null);release();const results=await Promise.all([a,b]);assert.strictEqual(results[0],results[1]);assert.equal(calls,2);
});
test('a normal eleven-member roster can resolve its cold-cache skins together',async()=>{
 const ids=Array.from({length:11},(_,i)=>'11111111-1111-4111-8111-'+String(i+1).padStart(12,'0')),byCompact=new Map(ids.map(id=>[id.replaceAll('-',''),id]));
 let release,calls=0;const gate=new Promise(r=>release=r);const resolver=createSkinResolver({fetcher:async url=>{if(String(url).includes('sessionserver')){calls++;await gate;return Response.json(javaProfile(byCompact.get(String(url).split('/').at(-1))));}return imageResponse(png);}});
 const requests=ids.map(id=>resolver.resolve(id)),started=calls;release();const skins=await Promise.all(requests);assert.equal(started,11);assert(skins.every(Boolean));
});
test('negative caching expires; lookup budget and entry/byte bounds remain effective',async()=>{
 let time=0,calls=0;const missing=createSkinResolver({now:()=>time,lookupsPerMinute:2,fetcher:async()=>{calls++;return Response.json({},{status:404});}});
 await missing.resolve(javaUuid);await missing.resolve(javaUuid);assert.equal(calls,1);time=30001;await missing.resolve(javaUuid);assert.equal(calls,2);await missing.resolve(otherUuid);assert.equal(calls,2);time=60001;await missing.resolve(otherUuid);assert.equal(calls,3);
 for(const limits of [{maxEntries:1},{maxBytes:png.length-1}]){
  let count=0;const resolver=createSkinResolver({...limits,fetcher:async url=>{count++;return String(url).includes('sessionserver')?Response.json(javaProfile(String(url).endsWith(otherUuid.replaceAll('-',''))?otherUuid:javaUuid)):imageResponse(png);}});
  await resolver.resolve(javaUuid);if(limits.maxEntries)await resolver.resolve(otherUuid);await resolver.resolve(javaUuid);assert.equal(count,limits.maxEntries?6:4);
 }
});
