import {readFileSync} from 'node:fs';
export const png=readFileSync(new URL('./fixtures/skin64.png',import.meta.url));
export const legacy=readFileSync(new URL('./fixtures/skin-legacy.png',import.meta.url));
export const unsupported=readFileSync(new URL('./fixtures/skin-unsupported.png',import.meta.url));
export const javaUuid='12345678-1234-4234-8234-123456789abc',otherUuid='22345678-1234-4234-8234-123456789abc',textureId='a'.repeat(64);
export const textureValue=({model='default',url='http://textures.minecraft.net/texture/'+textureId,profileId}={})=>Buffer.from(JSON.stringify({...(profileId?{profileId}:{}),textures:{SKIN:{url,...(model==='default'?{}:{metadata:{model}})}}})).toString('base64');
export function javaProfile(uuid=javaUuid,options={}){return{id:uuid.replaceAll('-','').toLowerCase(),properties:[{name:'textures',value:textureValue({profileId:uuid.replaceAll('-',''),...options})}]};}
export const imageResponse=bytes=>new Response(bytes,{headers:{'Content-Type':'image/png'}});
export const syntheticSkin={bytes:png,model:'default',hash:'f'.repeat(64),width:64,height:64};
