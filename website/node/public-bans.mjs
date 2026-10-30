export async function publicBanCards(db){
 const {results}=await db.prepare('SELECT id,source_key,player_name,banned_by,reason,scope,created_text,expires_text FROM moderation_bans WHERE active=1 ORDER BY first_seen DESC,id DESC LIMIT 9').all();
 return Response.json({bans:results.map(({source_key,...ban})=>({...ban,demo:source_key.startsWith('demo:')}))},{headers:{'Cache-Control':'no-store'}});
}
