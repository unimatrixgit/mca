import {randomUUID} from 'node:crypto';
import {gameAuthorized,validUuid} from '../node/game-link.mjs';

const path='/api/game/ticket-context';
const reply=(body,status=200)=>Response.json(body,{status,headers:{'Cache-Control':'private, no-store','X-Content-Type-Options':'nosniff'}});
const uuid=value=>validUuid(value)&&value!=='00000000-0000-0000-0000-000000000000';
const exact=(body,keys)=>body&&typeof body==='object'&&!Array.isArray(body)&&Object.keys(body).length===keys.length&&keys.every(key=>Object.hasOwn(body,key));
const revision=value=>Number.isSafeInteger(value)&&value>0;
export function validateTicketContextRequest(body){
 const keys=body?.action==='read'?['actorUuid','ticketId','action']:body?.action==='take'?['actorUuid','ticketId','action','version']:body?.action==='set-suspect'?['actorUuid','ticketId','action','version','suspectUuid']:[];
 return keys.length&&exact(body,keys)&&uuid(body.actorUuid)&&typeof body.ticketId==='string'&&/^[1-9][0-9]{0,17}$/.test(body.ticketId)
  &&(body.action==='read'||revision(body.version))&&(body.action!=='set-suspect'||uuid(body.suspectUuid))?body:null;
}
function fail(code){throw Object.assign(new Error(code),{caseCode:code});}
function cleanVersion(value){const number=Number(value);if(!Number.isSafeInteger(number)||number<0)fail('unavailable');return number;}
function optionalUuid(value){if(value==null)return null;if(!uuid(value))fail('unavailable');return value;}
function dto(row){
 return {ticketId:String(row.ticket_id),contextVersion:cleanVersion(row.context_version),
  assignedAgentUuid:optionalUuid(row.assigned_agent_uuid),creatorUuid:optionalUuid(row.creator_uuid),suspectUuid:optionalUuid(row.suspect_uuid),
  canTake:Number(row.can_take)===1,canSetSuspect:Number(row.can_set_suspect)===1,
  actions:{teleport:Number(row.can_teleport)===1,inspectInventory:Number(row.can_inspect_inventory)===1,
   editInventory:Number(row.can_edit_inventory)===1,adjustEconomy:Number(row.can_adjust_economy)===1,editFaction:Number(row.can_edit_faction)===1}};
}

/** All authority comes from the same locking SQL procedure used inside native Core transactions. */
export function createGameTicketContext(db,settings={},dependencies={}){
 const now=dependencies.now||Date.now,id=dependencies.id||randomUUID;
 return async request=>{
  const url=new URL(request.url);if(url.pathname!==path)return reply({message:'Not found.'},404);
  if(request.method!=='POST')return reply({message:'Method not allowed.'},405);
  if(!gameAuthorized(request,settings))return reply({message:'Unauthorized.'},401);
  if(url.search)return reply({message:'Identity and ticket query overrides are not accepted.'},400);
  let body;
  try{
   const raw=await request.text();if(Buffer.byteLength(raw)>2048)throw Error();
   body=validateTicketContextRequest(JSON.parse(raw));
   // This API is a flat, primitive-only object. Reject duplicate/escaped property aliases.
   const keys=[...raw.matchAll(/"([A-Za-z][A-Za-z0-9]*)"\s*:/g)].map(match=>match[1]);
   if(!body||keys.length!==Object.keys(body).length||new Set(keys).size!==keys.length)throw Error();
  }catch{return reply({message:'Use a valid actor UUID, ticket, action and current version.'},400);}
  try{
   const results=await db.batch([{async run(connection){
    const query=async(sql,values=[])=>(await connection.execute(sql,values))[0];
    const authority=async()=>{
     const sets=await query('CALL dunegameplay.dune_case_action_lock(?,?,NULL,?)',[body.actorUuid,body.ticketId,'read']);
     const rows=Array.isArray(sets?.[0])?sets[0]:sets;
     if(!Array.isArray(rows)||rows.length!==1)fail('unavailable');
     const row=rows[0];if(row.actor_uuid!==body.actorUuid||String(row.ticket_id)!==body.ticketId)fail('unavailable');
     cleanVersion(row.context_version);return row;
    };
    let row=await authority();
    if(cleanVersion(row.context_version)===0){
     await query('INSERT IGNORE INTO dune_game_ticket_context(ticket_id,version,updated_at) VALUES(?,1,?)',[body.ticketId,now()]);
     row=await authority();if(cleanVersion(row.context_version)!==1)fail('conflict');
    }
    if(body.action==='read')return {context:dto(row)};
    const version=cleanVersion(row.context_version),assigned=String(row.assigned_staff_id)===String(row.agent_id);
    if(body.action==='take'&&!Number(row.can_take)||body.action==='set-suspect'&&!Number(row.can_set_suspect))fail('denied');
    const replay=version===body.version+1&&row.last_actor_uuid===body.actorUuid&&Number(row.last_from_version)===body.version
     &&row.last_action===body.action&&(body.action==='take'?assigned:row.suspect_uuid===body.suspectUuid);
    if(version!==body.version&&!replay)fail('conflict');
    if(replay)return {context:dto(row)};
    const stamp=now();let details;
    if(body.action==='take'){
     if(assigned)return {context:dto(row)};
     if(Number(row.assigned_staff_id)!==0)fail('conflict');
     const events=await query("SELECT th.id AS thread_id,e.id AS event_id FROM drsupport_thread th JOIN drsupport_event e ON e.name='assigned' WHERE th.object_id=? AND th.object_type='T' FOR SHARE",[body.ticketId]);
     if(events.length!==1)fail('unavailable');
     const updated=await query('UPDATE drsupport_ticket SET staff_id=?,updated=UTC_TIMESTAMP(),lastupdate=UTC_TIMESTAMP() WHERE ticket_id=? AND staff_id=0',[row.agent_id,body.ticketId]);
     if(Number(updated.affectedRows)!==1)fail('conflict');
     await query("INSERT INTO drsupport_thread_event(thread_id,thread_type,event_id,staff_id,team_id,dept_id,topic_id,data,username,uid,uid_type,annulled,timestamp) SELECT ?,'T',?,t.staff_id,t.team_id,t.dept_id,t.topic_id,?,?,?,'S',0,UTC_TIMESTAMP() FROM drsupport_ticket t WHERE t.ticket_id=?",[events[0].thread_id,events[0].event_id,JSON.stringify({claim:true,source:'in-game'}),String(row.actor_account_id).replaceAll('-',''),row.agent_id,body.ticketId]);
     details={assignedAgentUuid:body.actorUuid};
    }else{
     if(row.suspect_uuid===body.suspectUuid)return {context:dto(row)};
     const known=await query('SELECT minecraft_uuid FROM site_game_identities WHERE BINARY minecraft_uuid=BINARY ? AND first_linked_at>0 FOR SHARE',[body.suspectUuid]);
     if(known.length!==1||known[0].minecraft_uuid!==body.suspectUuid)fail('unverified');
     const changed=await query('UPDATE dune_game_ticket_context SET suspect_uuid=?,version=version+1,updated_at=? WHERE ticket_id=? AND version=?',[body.suspectUuid,stamp,body.ticketId,version]);
     if(Number(changed.affectedRows)!==1)fail('conflict');
     details={previousSuspectUuid:row.suspect_uuid,newSuspectUuid:body.suspectUuid};
    }
    // A missing native assignment trigger must roll back, never acknowledge a stale version.
    const after=await authority();if(cleanVersion(after.context_version)!==version+1)fail('unavailable');
    await query('UPDATE dune_game_ticket_context SET last_action=?,last_actor_uuid=?,last_from_version=?,updated_at=? WHERE ticket_id=? AND version=?',[body.action,body.actorUuid,version,stamp,body.ticketId,version+1]);
    await query('INSERT INTO dune_game_ticket_context_events(id,ticket_id,actor_user_id,actor_uuid,action,from_version,to_version,details_json,created_at) VALUES(?,?,?,?,?,?,?,?,?)',[id(),body.ticketId,row.actor_account_id,body.actorUuid,body.action,version,version+1,JSON.stringify(details),stamp]);
    await query('INSERT INTO account_audit(id,actor_id,action,details,created_at) VALUES(?,?,?,?,?)',[id(),row.actor_account_id,'ticket-context-'+body.action,JSON.stringify({ticketId:body.ticketId,version:version+1,...details}),stamp]);
    return {context:dto(after)};
   }}]);
   return reply(results[0].context);
  }catch(error){
   const code=error.caseCode||({'case_denied':'denied','case_conflict':'conflict','case_invalid':'invalid'}[error.sqlMessage]);
   if(code==='denied')return reply({message:'Current ticket assignment or department permissions do not allow this action.'},403);
   if(code==='conflict'||['ER_LOCK_DEADLOCK','ER_LOCK_WAIT_TIMEOUT','ER_DUP_ENTRY'].includes(error.code))return reply({message:'The ticket, account or case context changed. Refresh before trying again.'},409);
   if(code==='unverified')return reply({message:'Select an online player with a known verified Minecraft identity.'},409);
   if(code==='invalid')return reply({message:'Invalid case action.'},400);
   return reply({message:'Authoritative ticket context is temporarily unavailable. No action was confirmed.'},503);
  }
 };
}
