import {randomUUID} from 'node:crypto';
import {accessFor,isOwnerRank} from './permissions.mjs';
export const CRYSKNIFE_SEAL='https://www.dunerealms.com/assets/seals/order-crysknife-v1.png';
export async function initializeHonours(db){
 await db.prepare('CREATE TABLE IF NOT EXISTS site_honours(user_id CHAR(36) NOT NULL,rank_key VARCHAR(40) NOT NULL,citation TEXT NOT NULL,awarded_by CHAR(36) NOT NULL,updated_at BIGINT NOT NULL,version INTEGER NOT NULL,PRIMARY KEY(user_id,rank_key))').run();
}
export function validCitation(value){return typeof value==='string'&&Array.from(value).length<=2000&&value.split(/\r?\n/).length<=20&&!/[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]/.test(value);}
export async function honoursFor(db,user){
 if(!(await accessFor(db,user)).ranks.includes('crysknife'))return [];
 let row=null;try{row=await db.prepare("SELECT citation,updated_at,version FROM site_honours WHERE user_id=? AND rank_key='crysknife'").bind(user.id).first();}catch(e){if(!/no such table|doesn't exist/i.test(e.message))throw e;}
 return [{rank:'crysknife',title:'Holy Order of The Crysknife',seal:CRYSKNIFE_SEAL,citation:row?.citation||'',version:Number(row?.version||0),updatedAt:Number(row?.updated_at||0)}];
}
export async function honourAction(db,actor,request,origin){
 const access=await accessFor(db,actor);
 if(!access.nodes.includes('web.accounts.assign'))return Response.json({message:'Rank assignment permission required.'},{status:403});
 const url=new URL(request.url);let body;
 if(request.method==='GET'){if([...url.searchParams.keys()].some(k=>k!=='userId'))return Response.json({message:'Invalid query.'},{status:400});body={userId:url.searchParams.get('userId')};}
 else {if(request.method!=='POST'||request.headers.get('origin')!==origin)return Response.json({message:'Origin rejected.'},{status:403});try{body=await request.json();}catch{return Response.json({message:'Invalid request.'},{status:400});}}
 if(typeof body?.userId!=='string'||!/^[a-f0-9-]{36}$/i.test(body.userId))return Response.json({message:'Invalid account.'},{status:400});
 const target=await db.prepare('SELECT id,role FROM site_accounts WHERE id=?').bind(body.userId).first();
 if(!target)return Response.json({message:'Account not found.'},{status:404});
 const rights=await accessFor(db,target);
 if(!isOwnerRank(access.rank)&&rights.level>=access.level)return Response.json({message:'This award requires a higher rank.'},{status:403});
 if(!rights.ranks.includes('crysknife'))return Response.json({message:'Assign the Crysknife rank first.'},{status:409});
 const honour=(await honoursFor(db,target))[0];
 if(request.method==='GET')return Response.json({honour},{headers:{'Cache-Control':'no-store'}});
 if(!validCitation(body.citation)||!Number.isSafeInteger(body.version)||body.version<0)return Response.json({message:'Use up to 20 lines and 2,000 characters.'},{status:400});
 if(body.version!==honour.version)return Response.json({message:'The citation changed. Refresh before saving.'},{status:409});
 const text=body.citation.replaceAll('\r\n','\n').trim();
 try{
 const statement=honour.version===0?db.prepare("INSERT INTO site_honours(user_id,rank_key,citation,awarded_by,updated_at,version) VALUES(?,'crysknife',?,?,?,1)").bind(target.id,text,actor.id,Date.now()):db.prepare("UPDATE site_honours SET citation=?,awarded_by=?,updated_at=?,version=version+1 WHERE user_id=? AND rank_key='crysknife' AND version=?").bind(text,actor.id,Date.now(),target.id,honour.version);
 const result=await db.batch([statement,db.prepare('INSERT INTO account_audit(id,actor_id,action,details,created_at) VALUES(?,?,?,?,?)').bind(randomUUID(),actor.id,'honour-citation',target.id+' crysknife',Date.now())]);
 if(!result[0].meta.changes)return Response.json({message:'The citation changed. Refresh.'},{status:409});
 }catch(e){if(e.code==='ER_DUP_ENTRY'||String(e.message).includes('UNIQUE'))return Response.json({message:'The citation changed. Refresh.'},{status:409});throw e;}
 return Response.json({ok:true,version:honour.version+1});
}
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export function honourMarkup(honours=[]){return honours.map(h=>'<section class="honour-panel" aria-label="'+esc(h.title)+'"><img src="'+esc(h.seal)+'" alt="'+esc(h.title)+' seal" width="130" height="130"><div><small>DUNEREALMS · HONORARY ORDER</small><h3>'+esc(h.title)+'</h3><p class="honour-citation">'+esc(h.citation||'Honoured member of the Order.')+'</p></div></section>').join('');}
export const HONOUR_CSS='.honour-panel{display:flex;align-items:center;gap:24px;padding:24px;margin:24px 0;border:1px solid #bc9c4d;border-radius:16px;background:linear-gradient(135deg,#102e29,#173f4e);box-shadow:inset 0 0 0 5px #c7a65115}.honour-panel img{width:130px;height:130px;object-fit:contain;flex-shrink:0}.honour-panel small{letter-spacing:.13em;color:#cfb874;font-size:.7rem}.honour-panel h3{color:#ead293;font-family:Georgia,serif;margin:8px 0}.honour-citation{white-space:pre-wrap;overflow-wrap:anywhere;color:#eee4cb}.honour-panel p{margin-bottom:0}@media(max-width:560px){.honour-panel{flex-direction:column;align-items:flex-start;padding:20px;gap:12px}}';
