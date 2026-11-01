export async function publicBanCards(db){
 try{const response=await fetch('http://127.0.0.1:3003/api/public/bans',{signal:AbortSignal.timeout(4000)});if(response.ok){const data=await response.json();if(Array.isArray(data.bans))return Response.json(data,{headers:{'Cache-Control':'no-store'}});}}catch{}
 const {results}=await db.prepare('SELECT id,source_key,player_name,banned_by,reason,scope,created_text,expires_text FROM moderation_bans WHERE active=1 ORDER BY first_seen DESC,id DESC LIMIT 9').all();
 return Response.json({bans:results.map(({source_key,...ban})=>({...ban,demo:source_key.startsWith('demo:')}))},{headers:{'Cache-Control':'no-store'}});
}
