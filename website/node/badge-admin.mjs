import {randomUUID} from 'node:crypto';
import {identity} from './auth.mjs';
import {can,accessFor} from './permissions.mjs';
import {BADGES,badgesFor,awardDedicated} from './community-badges.mjs';
import {json} from './game-link.mjs';
import {nativeSyncChange,drainNativeSyncOutbox} from './native-sync-outbox.mjs';
export function createBadgeAdmin(db,origin){return async request=>{
 const user=await identity(request,db);if(!user)return json({message:'Sign in first.'},401);
 if(!await can(db,user,'web.ranks.manage'))return json({message:'Rank management permission required.'},403);
 if(request.method==='GET'){
  const {results}=await db.prepare("SELECT setting_key,setting_value FROM website_settings WHERE setting_key IN ('alpha-badge-active','season-tavren-ii-active')").all();
  return json({catalog:BADGES,phases:Object.fromEntries(results.map(r=>[r.setting_key,r.setting_value==='1']))});
 }
 if(request.method!=='POST')return json({message:'Method not allowed'},405);
 if(request.headers.get('origin')!==origin)return json({message:'Origin rejected'},403);
 let body;try{body=await request.json();}catch{return json({message:'Invalid request'},400);}
 const audit=(action,details)=>db.prepare('INSERT INTO account_audit(id,actor_id,action,details,created_at) VALUES(?,?,?,?,?)').bind(randomUUID(),user.id,action,details,Date.now());
 if(body.phase){
  if(!['alpha-badge-active','season-tavren-ii-active'].includes(body.phase)||typeof body.active!=='boolean')return json({message:'Invalid phase'},400);
  await db.batch([db.prepare('UPDATE website_settings SET setting_value=?,updated_at=? WHERE setting_key=?').bind(body.active?'1':'0',Date.now(),body.phase),audit('badge-phase',body.phase+'='+body.active)]);
  return json({ok:true});
 }
 if(typeof body.userId!=='string'||!Array.isArray(body.badges)||body.badges.some(k=>!BADGES.includes(k)||['dedicated-tester','dunegit-owner'].includes(k)))return json({message:'Invalid badge selection'},400);
 const target=await db.prepare('SELECT id,role FROM site_accounts WHERE id=?').bind(body.userId).first();if(!target)return json({message:'Account not found'},404);
 const access=await accessFor(db,target);if(!access.ranks.every(k=>['user','vip','patron_caravan','patron_guild','patron_grand'].includes(k))&&body.badges.some(k=>k!=='season-tavren-ii'))return json({message:'Tester seals are only for regular users.'},400);
 const keys=[...new Set(body.badges)];
 await db.batch([db.prepare("DELETE FROM website_badges WHERE user_id=? AND badge_key<>'dunegit-owner'").bind(target.id),...keys.map(k=>db.prepare('INSERT INTO website_badges(user_id,badge_key,earned_at) VALUES(?,?,?)').bind(target.id,k,Date.now())),audit('account-badges',target.id+' -> '+keys.join(',')),nativeSyncChange(db)]);
 await awardDedicated(db,target.id,{statements:[nativeSyncChange(db)]});const nativeSync=await drainNativeSyncOutbox(db);
 return json({ok:true,nativeSyncPending:nativeSync.pending,badges:await badgesFor(db,target.id)});
};}
