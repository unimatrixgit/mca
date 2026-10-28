import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {openDatabase} from '../node/mysql.mjs';
import {createAuth} from '../node/auth.mjs';
import {identity} from '../node/auth.mjs';
import {createInventoryHandler,createSalesHistoryHandler} from './inventory/inventory-api.mjs';
import {createMarketBridge} from './inventory/market-bridge.mjs';
import {createGameLink,json} from '../node/game-link.mjs';
import {createCommunityRewards} from '../node/community-rewards.mjs';
import {createAccountAdmin} from '../node/account-admin.mjs';
import {createServerControl} from './server-control.mjs';
const types={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.mjs':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8'};
const sharedAssets=new Set(['ornament.webp','guild.webp','minecraft-hero.webp','menu-0.webp','menu-5.webp','menu-6.webp','menu-9.webp','menu-12.webp','item-23.webp','dunemark-icon-64.png','arrakis-lore-frieze.svg']);
export function createPanel(db,settings={},dependencies={}){
 const origin=new URL(settings.CP_ORIGIN||'https://cp.dunerealms.com').origin;
 const auth=createAuth(db,origin),links=createGameLink(db,settings,origin),rewards=createCommunityRewards(db,settings,origin);
 const admin=createAccountAdmin(db,origin);
 const servers=createServerControl(db,settings,dependencies.serverControl);
 const pool=dependencies.inventoryPool||{async query(){throw Error('Inventory database is not configured');}};
 const inventoryOptions={authenticate:request=>identity(request,db),verifiedMinecraftUuid:async accountId=>(await db.prepare('SELECT minecraft_uuid FROM site_game_links WHERE user_id=?').bind(accountId).first())?.minecraft_uuid||null,pool,logger:dependencies.logger||console};
 const inventory=createInventoryHandler(inventoryOptions),sales=createSalesHistoryHandler(inventoryOptions);
 const market=(settings.EXCHANGE_READ_TOKEN||'').length>=32?createMarketBridge({pool,readToken:settings.EXCHANGE_READ_TOKEN}):null;
 return async request=>{
  const path=new URL(request.url).pathname;
  if(path==='/api/servers'||path.startsWith('/api/servers/'))return servers(request);
  if(path==='/api/inventory')return inventory(request);
  if(path==='/api/exchange/sales-history')return sales(request);
  if(path==='/internal/exchange/listings')return market?market(request):new Response('Not configured',{status:503,headers:{'Cache-Control':'no-store'}});
  if(path.startsWith('/api/admin/'))return admin(request);
  if(path==='/healthz'){await db.prepare('SELECT 1 AS ok').first();return json({status:'ok'});}
  if(path.startsWith('/api/auth/'))return auth(request);
  if(path==='/api/game-link'||path.startsWith('/api/game-link/'))return links(request);
  if(path.startsWith('/api/community/'))return rewards(request);
  if(!['GET','HEAD'].includes(request.method))return new Response('Method not allowed',{status:405});
  if(path.startsWith('/assets/')&&sharedAssets.has(path.slice(8))){const file=path.slice(8);return new Response(request.method==='HEAD'?null:await readFile(new URL('../web/assets/'+file,import.meta.url)),{headers:{'Content-Type':file.endsWith('.svg')?'image/svg+xml':file.endsWith('.png')?'image/png':'image/webp','Cache-Control':'public, max-age=3600'}});}
  const file=path==='/'?'index.html':path==='/servers'?'servers.html':path.slice(1);
  if(!['index.html','panel.js','panel.css','servers.html','servers.js','servers.css','inventory/inventory-view.mjs','inventory/inventory-mount.mjs','inventory/inventory.css'].includes(file))return new Response('Not found',{status:404});
  return new Response(request.method==='HEAD'?null:await readFile(new URL('./public/'+file,import.meta.url)),{headers:{'Content-Type':types[file.slice(file.lastIndexOf('.'))],'Cache-Control':'no-store'}});
 };
}
export async function startPanel(settings=process.env){
 let inventoryPool=null;
 if(settings.DUNE_INVENTORY_DATABASE_URL){const {Pool}=await import('pg');inventoryPool=new Pool({connectionString:settings.DUNE_INVENTORY_DATABASE_URL,max:3,connectionTimeoutMillis:5000,idleTimeoutMillis:30000});inventoryPool.on('error',()=>console.error('Inventory database connection unavailable.'));}
 const db=await openDatabase(settings),handle=createPanel(db,settings,{inventoryPool}),origin=settings.CP_ORIGIN||'https://cp.dunerealms.com';
 const security={'X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer','Cache-Control':'no-store','Content-Security-Policy':"default-src 'self'; script-src 'self'; style-src 'self' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; connect-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'self' https://www.dunerealms.com https://dunerealms.com"};
 const server=createServer(async(req,res)=>{try{
  req.setTimeout(15000);let length=0;const chunks=[];for await(const chunk of req){length+=chunk.length;if(length>16384){res.writeHead(413,security);res.end();return;}chunks.push(chunk);}
  if(!req.url?.startsWith('/')||req.url.startsWith('//')){res.writeHead(400,security);res.end();return;}
  const result=await handle(new Request(origin+req.url,{method:req.method,headers:req.headers,...(!['GET','HEAD'].includes(req.method)?{body:Buffer.concat(chunks)}:{})}));
  res.writeHead(result.status,{...security,...Object.fromEntries(result.headers)});res.end(Buffer.from(await result.arrayBuffer()));
 }catch(error){console.error('Panel request failed:',error.code||error.name);res.writeHead(503,security);res.end('{"message":"The account panel is temporarily unavailable."}');}});
 server.headersTimeout=10000;server.requestTimeout=30000;
 await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(Number(settings.CP_PORT||3003),'127.0.0.1',resolve);});
 process.once('SIGTERM',()=>server.close(async()=>{await Promise.allSettled([db.close(),inventoryPool?.end()]);process.exit(0);}));console.log('Account panel listening on localhost');return server;
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href)await startPanel();
