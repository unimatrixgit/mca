import {identity} from '../node/auth.mjs';
import {accessFor} from '../node/permissions.mjs';
const reply=(body,status=200)=>Response.json(body,{status,headers:{'Cache-Control':'private, no-store',Vary:'Cookie'}});
const houses=new Set(['fremen','atreides','harkonnen','corrino','ordos']);
export function createPoliticsPreview(db,settings={},deps={}){
 const auth=deps.authenticate||(r=>identity(r,db)),access=deps.access||(u=>accessFor(db,u));
 const linked=deps.linked||(async u=>(await db.prepare('SELECT minecraft_uuid FROM site_game_links WHERE user_id=?').bind(u.id).first())?.minecraft_uuid);
 return async request=>{
  if(request.method!=='GET')return reply({message:'This website preview does not accept votes or grant rights.'},405);
  if(new URL(request.url).search)return reply({message:'Player and faction overrides are not accepted.'},400);
  const user=await auth(request);if(!user)return reply({message:'Sign in first.'},401);
  if(!(await access(user)).nodes.includes('web.menu.game'))return reply({message:'Game account access required.'},403);
  const player=await linked(user);if(!player)return reply({preview:true,linked:false,faction:null,factionStatus:'unlinked',emperor:false,houseLeader:false,officeStatus:'unavailable'});
  if(!/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(player))return reply({message:'Verified identity unavailable.'},503);
  if((settings.SERVER_CONTROL_TOKEN||'').length<64)return reply({preview:true,linked:true,faction:null,factionStatus:'unavailable',emperor:false,houseLeader:false,officeStatus:'unavailable'});
  const read=async path=>{const r=await(deps.fetcher||fetch)('http://127.0.0.1:15433'+path+'?uuid='+encodeURIComponent(player),{headers:{Authorization:'Bearer '+settings.SERVER_CONTROL_TOKEN},signal:AbortSignal.timeout(8000)});if(!r.ok)throw Error('Unavailable');return r.json();};
  const [bank,office]=await Promise.allSettled([read('/bank/faction'),read('/kits/player')]);
  const current=await auth(request);if(!current||current.id!==user.id)return reply({message:'Sign in again.'},401);
  if(!(await access(current)).nodes.includes('web.menu.game'))return reply({message:'Access changed.'},403);
  if((await linked(current))?.toLowerCase()!==player.toLowerCase())return reply({message:'Your linked account changed. Refresh.'},409);
  const b=bank.status==='fulfilled'?bank.value:null,o=office.status==='fulfilled'?office.value:null;
  const faction=b?.playerUuid?.toLowerCase()===player.toLowerCase()&&houses.has(b.factionId)?b.factionId:null;
  const emperor=o?.playerUuid?.toLowerCase()===player.toLowerCase()&&Array.isArray(o.offices)&&o.offices.some(x=>x.office==='emperor');
  const houseLeader=o?.playerUuid?.toLowerCase()===player.toLowerCase()&&Array.isArray(o.offices)&&o.offices.some(x=>x.office==='house_leader');
  return reply({preview:true,linked:true,faction,factionStatus:faction?'verified':'unavailable',emperor:!!emperor,houseLeader:!!houseLeader,officeStatus:o?.playerUuid?.toLowerCase()===player.toLowerCase()&&Array.isArray(o.offices)?'verified':'unavailable'});
 };
}
