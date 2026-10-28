import {randomUUID} from 'node:crypto';
import {identity} from './auth.mjs';
import {json,gameAuthorized} from './game-link.mjs';
import {NODES,DEFAULT_RANKS,accessFor,rankFor,rankKey,can} from './permissions.mjs';
const validKey=value=>typeof value==='string'&&/^[a-z][a-z0-9_]{2,39}$/.test(value);
export function createRankAdmin(db,settings,origin){return async request=>{
 const url=new URL(request.url);
 if(url.pathname==='/api/game/permissions'){
  if(request.method!=='GET'||url.search||!gameAuthorized(request,settings))return json({message:'Unauthorized'},401);
  const {results:ranks}=await db.prepare('SELECT rank_key,label,rank_level,kind,permissions_json,version FROM cp_ranks ORDER BY rank_level,rank_key').all();
  const {results:members}=await db.prepare('SELECT a.role,l.minecraft_uuid FROM site_accounts a JOIN site_game_links l ON l.user_id=a.id').all();
  return json({version:1,catalog:Object.keys(NODES).filter(n=>!n.startsWith('cp.')),ranks:ranks.map(r=>({key:r.rank_key,label:r.label,level:Number(r.rank_level),kind:r.kind,nodes:JSON.parse(r.permissions_json).filter(n=>!n.startsWith('cp.'))})),members:members.map(m=>({uuid:m.minecraft_uuid,rank:rankKey(m.role)}))});
 }
 const user=await identity(request,db);if(!user)return json({message:'Sign in first.'},401);
 const access=await accessFor(db,user);
 if(!access.nodes.includes('cp.ranks.manage'))return json({message:'Rank-management permission required.'},403);
 if(request.method==='GET'&&url.pathname==='/api/ranks'&&!url.search){
  const {results}=await db.prepare('SELECT rank_key,label,rank_level,kind,permissions_json,version FROM cp_ranks ORDER BY rank_level,rank_key').all();
  return json({catalog:Object.entries(NODES).map(([node,label])=>({node,label,scope:node.startsWith('cp.')?'Website':'Minecraft'})),ranks:results.map(r=>({key:r.rank_key,label:r.label,level:Number(r.rank_level),kind:r.kind,nodes:JSON.parse(r.permissions_json),version:Number(r.version),editable:r.rank_key!=='owners'&&(access.rank==='owners'||Number(r.rank_level)<access.level)})),actor:{rank:access.rank,nodes:access.nodes}});
 }
 if(request.method!=='POST'||url.search)return json({message:'Invalid rank request.'},400);
 if(request.headers.get('origin')!==origin)return json({message:'Origin rejected.'},403);
 let body;try{body=await request.json();}catch{return json({message:'Invalid request.'},400);}
 const audit=(action,details)=>db.prepare('INSERT INTO account_audit(id,actor_id,action,details,created_at) VALUES(?,?,?,?,?)').bind(randomUUID(),user.id,action,details,Date.now());
 if(url.pathname==='/api/ranks/create'){
  if(!validKey(body.key)||body.key.length>16||DEFAULT_RANKS.some(r=>r.key===body.key)||typeof body.label!=='string'||!body.label.trim()||body.label.length>40||!['user','vip','staff','corestaff'].includes(body.base))return json({message:'Use a unique rank ID of 3–16 lowercase letters, digits or underscores, a label, and a base rank.'},400);
  const base=await rankFor(db,body.base);
  if(access.rank!=='owners'&&base.level>=access.level)return json({message:'You can only create ranks below your own.'},403);
  const nodes=base.nodes.filter(n=>access.nodes.includes(n));
  try{await db.batch([db.prepare('INSERT INTO cp_ranks(rank_key,label,rank_level,kind,permissions_json,version) VALUES(?,?,?,?,?,1)').bind(body.key,body.label.trim(),base.level,'access',JSON.stringify(nodes)),audit('rank-created',body.key)]);}catch(error){if(error.code==='ER_DUP_ENTRY'||String(error.message).includes('UNIQUE'))return json({message:'That rank already exists.'},409);throw error;}
  return json({ok:true},201);
 }
 if(url.pathname==='/api/ranks/permissions'){
  if(!validKey(body.rank)||!Array.isArray(body.nodes)||body.nodes.length>Object.keys(NODES).length||!Number.isInteger(body.version))return json({message:'Invalid permission matrix.'},400);
  const rank=await rankFor(db,body.rank),nodes=[...new Set(body.nodes)];
  if(rank.key==='owners'||rank.level===0||access.rank!=='owners'&&rank.level>=access.level)return json({message:'This rank is protected.'},403);
  if(nodes.some(n=>!Object.hasOwn(NODES,n)||access.rank!=='owners'&&!access.nodes.includes(n)||rank.kind!=='access'&&n.startsWith('cp.')))return json({message:'You cannot grant these permissions.'},403);
  const result=await db.batch([db.prepare('UPDATE cp_ranks SET permissions_json=?,version=version+1 WHERE rank_key=? AND version=?').bind(JSON.stringify(nodes),rank.key,body.version),audit('rank-permissions',rank.key+': matrix update attempted')]);
  return result[0].meta.changes?json({ok:true}):json({message:'This matrix changed in another session. Refresh before saving.'},409);
 }
 if(url.pathname==='/api/ranks/assign'){
  if(!await can(db,user,'cp.accounts.assign'))return json({message:'Account rank assignment permission required.'},403);
  if(typeof body.userId!=='string'||body.userId.length!==36||!validKey(body.rank))return json({message:'Invalid assignment.'},400);
  const target=await db.prepare('SELECT id,role FROM site_accounts WHERE id=?').bind(body.userId).first();if(!target)return json({message:'Account not found.'},404);
  const rank=await rankFor(db,body.rank),previous=await rankFor(db,target.role);
  if(rank.kind!=='access'||rank.level===0||previous.key==='owners'||access.rank!=='owners'&&(rank.level>=access.level||previous.level>=access.level||target.id===user.id))return json({message:'That assignment is protected. Owner accounts cannot be demoted here.'},403);
  await db.batch([db.prepare('UPDATE site_accounts SET role=? WHERE id=? AND role=?').bind(rank.key,target.id,target.role),db.prepare('DELETE FROM site_sessions WHERE user_id=?').bind(target.id),audit('account-rank',target.id+' -> '+rank.key)]);
  return json({ok:true});
 }
 return json({message:'Not found.'},404);
};}
