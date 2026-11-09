import {randomUUID} from 'node:crypto';
import {identity} from './auth.mjs';
import {accessFor} from './permissions.mjs';
import {createWorldStatus} from './world-status.mjs';

const paths=new Set(['/api/v1/me','/api/v1/me/minecraft','/api/v1/me/permissions','/api/v1/servers','/api/v1/catalogue']);
const uuid=/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;
const integer=/^[1-9][0-9]{0,18}$/;
const maxId=9223372036854775807n;
const decimal=/^(?:0|[1-9][0-9]{0,17})(?:\.[0-9]{1,2})?$/;
const unavailable=()=>{throw Error('upstream unavailable');};

export function createApiV1(db,settings={},deps={}){
 const status=createWorldStatus(settings,deps.fetcher);
 const fetcher=deps.fetcher||fetch, now=deps.now||Date.now;
 const audit=deps.audit||((event)=>console.info(JSON.stringify(event)));
 const buckets=new Map();
 const rate=(key)=>{
  const time=now();for(const [k,b] of buckets)if(b.until<=time)buckets.delete(k);
  let b=buckets.get(key);
  if(!b){if(buckets.size>=5000)return false;b={until:time+60000,count:0};buckets.set(key,b);}
  return ++b.count<=120;
 };
 async function catalogue(){
  if(!settings.EXCHANGE_LISTINGS_URL||typeof settings.EXCHANGE_READ_TOKEN!=='string'||settings.EXCHANGE_READ_TOKEN.length<32)unavailable();
  const url=new URL(settings.EXCHANGE_LISTINGS_URL);
  if(url.protocol!=='https:'||url.username||url.password||url.search||url.hash)unavailable();
  const response=await fetcher(url.href,{headers:{Authorization:'Bearer '+settings.EXCHANGE_READ_TOKEN},redirect:'error',signal:AbortSignal.timeout(7000)});
  if(!response.ok||!response.body)unavailable();
  const reader=response.body.getReader();let length=0;const chunks=[];
  try{
   while(true){const {done,value}=await reader.read();if(done)break;length+=value.byteLength;if(length>1048576)unavailable();chunks.push(value);}
  }finally{await reader.cancel().catch(()=>{});}
  const parsed=JSON.parse(Buffer.concat(chunks).toString('utf8'));
  if(!Array.isArray(parsed.listings)||parsed.listings.length>500)unavailable();
  const seen=new Set();
  return parsed.listings.map(r=>{
   if(typeof r.id!=='string'||!integer.test(r.id)||BigInt(r.id)>maxId||seen.has(r.id)||
      typeof r.price!=='string'||!decimal.test(r.price)||!/[1-9]/.test(r.price)||
      !Number.isSafeInteger(r.quantity)||r.quantity<1||
      ['material','category','origin','seller'].some(k=>typeof r[k]!=='string'||r[k].length>({material:80,category:24,origin:48,seller:32}[k])))unavailable();
   seen.add(r.id);
   return {id:r.id,material:r.material,category:r.category,origin:r.origin,seller:r.seller,quantity:r.quantity,unitPrice:r.price};
  });
 }
 return async request=>{
  const url=new URL(request.url);
  if(url.pathname!=='/api/v1'&&!url.pathname.startsWith('/api/v1/'))return null;
  const requestId=randomUUID();let actor=null;
  const reply=async(code,data,kind,message,headers={})=>{
   const base={'Cache-Control':'private, no-store','Vary':'Cookie','X-Request-ID':requestId,'X-Content-Type-Options':'nosniff'};
   try{await audit({event:'api-v1-request',requestId,actorId:actor?.id||null,route:paths.has(url.pathname)?url.pathname:'unmatched',method:request.method,status:code});}
   catch{return Response.json({error:{code:'audit_unavailable',message:'API temporarily unavailable',requestId}},{status:503,headers:base});}
   return Response.json(code<400?{data,meta:{requestId}}:{error:{code:kind,message,requestId}},{status:code,headers:{...base,...headers}});
  };
  try{
   if(settings.API_V1_ENABLED!=='true')return reply(404,null,'not_found','API v1 is disabled');
   if(!rate('global'))return reply(429,null,'rate_limited','Try again later',{'Retry-After':'60'});
   actor=await identity(request,db);
   if(!actor)return reply(401,null,'unauthenticated','Sign in first');
   if(!rate('account:'+actor.id))return reply(429,null,'rate_limited','Try again later',{'Retry-After':'60'});
   if(request.headers.has('authorization')||request.headers.get('sec-fetch-site')==='cross-site'||
      (request.headers.has('origin')&&request.headers.get('origin')!==url.origin))return reply(403,null,'forbidden','Unsupported credential or origin');
   if(!paths.has(url.pathname))return reply(404,null,'not_found','Unknown endpoint');
   if(request.method!=='GET')return reply(405,null,'method_not_allowed','GET only',{Allow:'GET'});
   const keys=[...url.searchParams.keys()],paged=url.pathname==='/api/v1/catalogue';
   if(keys.some(k=>!paged||!['limit','before'].includes(k))||new Set(keys).size!==keys.length)return reply(400,null,'invalid_query','Unexpected query parameter');
   if(url.pathname==='/api/v1/me')return reply(200,{id:actor.id,username:actor.username,displayName:actor.display_name});
   if(url.pathname==='/api/v1/me/minecraft'){
    const row=await db.prepare('SELECT l.minecraft_uuid,l.minecraft_name,l.linked_at,i.first_linked_at FROM site_game_links l JOIN site_game_identities i ON i.minecraft_uuid=l.minecraft_uuid WHERE l.user_id=?').bind(actor.id).first();
    if(row&&(!uuid.test(row.minecraft_uuid)||!Number.isSafeInteger(Number(row.linked_at))||!Number.isSafeInteger(Number(row.first_linked_at))))unavailable();
    return reply(200,{linked:!!row,identity:row?{uuid:row.minecraft_uuid,name:row.minecraft_name,linkedAt:Number(row.linked_at),firstLinkedAt:Number(row.first_linked_at)}:null});
   }
   if(url.pathname==='/api/v1/me/permissions'){
    const access=await accessFor(db,actor);
    return reply(200,{rank:access.rank,label:access.label,permissions:[...new Set(access.nodes)].sort(),scopeModel:'legacy-effective-nodes'});
   }
   if(url.pathname==='/api/v1/servers'){
    const r=await status();if(!r.ok)unavailable();return reply(200,await r.json());
   }
   const limit=url.searchParams.get('limit')||'20',before=url.searchParams.get('before');
   if(!/^(?:[1-9]|[1-9][0-9]|100)$/.test(limit)||(before!==null&&(!integer.test(before)||BigInt(before)>maxId)))return reply(400,null,'invalid_query','Invalid pagination');
   const rows=(await catalogue()).sort((a,b)=>BigInt(a.id)>BigInt(b.id)?-1:BigInt(a.id)<BigInt(b.id)?1:0);
   const eligible=before===null?rows:rows.filter(r=>BigInt(r.id)<BigInt(before));
   const items=eligible.slice(0,Number(limit));
   return reply(200,{kind:'game-exchange',items,pagination:{limit:Number(limit),nextCursor:eligible.length>items.length?items.at(-1).id:null,hasMore:eligible.length>items.length,sourceWindow:500,sourceLimited:rows.length===500}});
  }catch{return reply(503,null,'service_unavailable','API temporarily unavailable');}
 };
}
