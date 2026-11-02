export async function publicBanCards(db){
 try{const response=await fetch('http://127.0.0.1:3003/api/public/bans',{signal:AbortSignal.timeout(4000)});if(response.ok){const data=await response.json();if(Array.isArray(data.bans))return Response.json(data,{headers:{'Cache-Control':'no-store'}});}}catch{}
 const {results}=await db.prepare('SELECT id,source_key,player_name,banned_by,reason,scope,created_text,expires_text FROM moderation_bans WHERE active=1 ORDER BY first_seen DESC,id DESC LIMIT 12').all();
 return Response.json({bans:results.map(({source_key,...ban})=>({...ban,demo:source_key.startsWith('demo:')}))},{headers:{'Cache-Control':'no-store'}});
}

export async function publicBanList(db,request){
 const url=new URL(request.url),raw=Number(url.searchParams.get('page')||1),q=(url.searchParams.get('q')||'').trim().slice(0,80);
 const requested=Number.isSafeInteger(raw)&&raw>0?Math.min(raw,100000):1;
 const where="active=1 AND scope<>'yousmp-legacy' AND source_key NOT LIKE 'demo:%'"+(q?' AND (player_name LIKE ? OR reason LIKE ? OR scope LIKE ?)':'');
 const values=q?Array(3).fill('%'+q+'%'):[];
 const count=await db.prepare('SELECT COUNT(*) AS total FROM moderation_bans WHERE '+where).bind(...values).first();
 const total=Number(count?.total||0),pages=Math.max(1,Math.ceil(total/50)),page=Math.min(requested,pages);
 const {results}=await db.prepare('SELECT id,player_name,banned_by,reason,scope,created_text,expires_text FROM moderation_bans WHERE '+where+' ORDER BY REPLACE(created_text,"T"," ") DESC,id DESC LIMIT 50 OFFSET '+((page-1)*50)).bind(...values).all();
 const bans=results.map(b=>({id:b.id,player:b.player_name,issuedBy:/^(console|0)$/i.test(String(b.banned_by).trim())?'Network':b.banned_by,reason:b.reason,scope:b.scope==='network'?'Network':b.scope,issued:b.created_text,expires:b.expires_text}));
 return Response.json({bans,page,pages,total,pageSize:50},{headers:{'Cache-Control':'no-store'}});
}
