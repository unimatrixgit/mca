import test from 'node:test';
import assert from 'node:assert/strict';
import {createCommunityAdmin} from '../control-panel/community-admin.mjs';
const id='11111111-1111-4111-8111-111111111111',origin='https://cp.dunerealms.com';
function rig(user={id:'owner',role:'owners'},post={id,status:'published',version:2}){
 const writes=[];const db={prepare(sql){return {bind(...args){return {sql,args,async all(){assert.match(sql,/^SELECT rank_key FROM cp_account_ranks WHERE user_id=\?$/);return{results:[]};},async first(){if(sql.includes('FROM cp_ranks'))return null;if(sql.includes('FROM site_sessions'))return user;if(sql.includes('FROM website_posts'))return post;return null;}};}};},async batch(statements){writes.push(...statements);return statements.map(()=>({meta:{changes:1}}));}};
 const handle=createCommunityAdmin(db,{});return {writes,request:(version=2,requestOrigin=origin)=>handle(new Request(origin+'/api/cp/posts/delete',{method:'POST',headers:{Origin:requestOrigin,Cookie:'__Host-dune_session='+'a'.repeat(43),'Content-Type':'application/json'},body:JSON.stringify({id,version})}))};
}
test('post deletion rejects unauthenticated, unprivileged and cross-origin requests',async()=>{
 for(const [user,status]of [[null,401],[{id:'reader',role:'user'},403]]){const r=rig(user);assert.equal((await r.request()).status,status);assert.equal(r.writes.length,0);}
 const r=rig();assert.equal((await r.request(2,'https://other.example')).status,403);assert.equal(r.writes.length,0);
});
test('a stale editor cannot delete a changed post',async()=>{const r=rig();assert.equal((await r.request(1)).status,409);assert.equal(r.writes.length,0);});
test('deletion retains the post and records an audit event',async()=>{const r=rig();assert.equal((await r.request()).status,200);assert.match(r.writes[0].sql,/status='deleted'/);assert.match(r.writes[0].sql,/AND version=\?/);assert.equal(r.writes[1].args[2],'post-delete');assert.equal(r.writes.some(x=>/DELETE FROM/.test(x.sql)),false);});
