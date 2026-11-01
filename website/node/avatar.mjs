import {readFile} from 'node:fs/promises';
import {join,resolve} from 'node:path';
const cache=new Map(),uuid=/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
export async function avatarForAccount(db,userId){
 try{const profile=await db.prepare('SELECT avatar_key FROM site_profiles WHERE user_id=?').bind(userId).first();if(profile?.avatar_key)return{url:'/api/avatars/site/'+profile.avatar_key,mode:'image'};}catch{}
 try{const linked=await db.prepare('SELECT minecraft_uuid FROM site_game_links WHERE user_id=?').bind(userId).first();if(uuid.test(linked?.minecraft_uuid||''))return{url:'/api/avatars/minecraft/'+linked.minecraft_uuid,mode:'skin'};}catch{}
 return{url:'/api/avatars/fallback',mode:'image'};
}
const fallback='<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 8 8" shape-rendering="crispEdges"><path fill="#976e50" d="M0 0h8v8H0z"/><path fill="#493225" d="M0 0h8v3H0zM0 3h1v2H0zM7 3h1v2H7z"/><path fill="#bc916a" d="M1 3h6v3H1z"/><path fill="#eee8d8" d="M1 4h2v1H1zM5 4h2v1H5z"/><path fill="#5c77b7" d="M2 4h1v1H2zM5 4h1v1H5z"/><path fill="#79513c" d="M3 5h2v1H3zM2 7h4v1H2z"/><path fill="#432c25" d="M2 6h1v1H2zM5 6h1v1H5zM3 7h2v1H3z"/></svg>';
const fallbackSkin=fallback.replace('viewBox="0 0 8 8"','width="64" height="64" viewBox="0 0 64 64"').replace('shape-rendering="crispEdges">','shape-rendering="crispEdges"><g transform="translate(8 8)">').replace('</svg>','</g></svg>');
const fallbackResponse=skin=>new Response(skin?fallbackSkin:fallback,{headers:{'Content-Type':'image/svg+xml','Cache-Control':'public,max-age=300','X-Content-Type-Options':'nosniff'}});
export function createAvatars(db,settings,{fetcher=fetch}={}){const handle=async request=>{
 const path=new URL(request.url).pathname;if(request.method!=='GET')return new Response('Method not allowed',{status:405});
 const roster=path.match(/^\/api\/avatars\/roster\/([a-z0-9_]{3,32})$/);
 if(roster){
  const account=await db.prepare('SELECT l.minecraft_uuid FROM site_accounts a LEFT JOIN site_game_links l ON l.user_id=a.id WHERE a.username=?').bind(roster[1]).first();
  if(!account)return new Response('Not found',{status:404,headers:{'Cache-Control':'no-store'}});
  if(!uuid.test(account.minecraft_uuid||''))return fallbackResponse(false);
  const image=await handle(new Request(new URL('/api/avatars/minecraft/'+account.minecraft_uuid,new URL(request.url).origin)));
  return image.ok?image:fallbackResponse(true);
 }
 if(path==='/api/avatars/fallback')return new Response(fallback,{headers:{'Content-Type':'image/svg+xml','Cache-Control':'public,max-age=86400','X-Content-Type-Options':'nosniff'}});
 const site=path.match(/^\/api\/avatars\/site\/([0-9a-f-]{36})$/i);
 if(site){const record=await db.prepare('SELECT avatar_mime FROM site_profiles WHERE avatar_key=?').bind(site[1]).first();if(!record||!settings.AVATAR_DIR)return new Response('Not found',{status:404});try{return new Response(await readFile(join(resolve(settings.AVATAR_DIR),site[1]+'.bin')),{headers:{'Content-Type':record.avatar_mime,'Cache-Control':'public,max-age=3600','X-Content-Type-Options':'nosniff'}});}catch{return new Response('Not found',{status:404});}}
 const match=path.match(/^\/api\/avatars\/minecraft\/([0-9a-f-]{36})$/i);if(!match||!uuid.test(match[1]))return new Response('Not found',{status:404});const key=match[1].toLowerCase();let data=cache.get(key);
 if(!data||data.until<Date.now())try{const profile=await fetcher('https://sessionserver.mojang.com/session/minecraft/profile/'+key.replaceAll('-',''),{signal:AbortSignal.timeout(4000)});if(!profile.ok)throw Error();const json=await profile.json(),textures=JSON.parse(Buffer.from(json.properties.find(p=>p.name==='textures').value,'base64').toString()),url=new URL(textures.textures.SKIN.url);if(url.hostname!=='textures.minecraft.net'||!/^\/texture\/[a-f0-9]+$/i.test(url.pathname))throw Error();url.protocol='https:';const image=await fetcher(url,{signal:AbortSignal.timeout(4000)});if(!image.ok||Number(image.headers.get('content-length')||0)>1000000)throw Error();const bytes=Buffer.from(await image.arrayBuffer());if(bytes.length>1000000||!bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])))throw Error();data={bytes,until:Date.now()+3600000};if(cache.size>1000)cache.delete(cache.keys().next().value);cache.set(key,data);}catch{return new Response('Minecraft skin unavailable',{status:404,headers:{'Cache-Control':'no-store'}});}
 return new Response(data.bytes,{headers:{'Content-Type':'image/png','Cache-Control':'public,max-age=3600','X-Content-Type-Options':'nosniff'}});
};return handle;}
