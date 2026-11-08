import {randomUUID} from 'node:crypto';
import {validateCandidature} from './public/election-rules.mjs';

export const applicationSchema = [
 `CREATE TABLE IF NOT EXISTS political_application_counters (scope VARCHAR(24) PRIMARY KEY, value INTEGER NOT NULL) ENGINE=InnoDB`,
 `CREATE TABLE IF NOT EXISTS political_applications (
 id CHAR(36) PRIMARY KEY, public_id VARCHAR(32) NOT NULL UNIQUE,
 user_id CHAR(36) NOT NULL, player_uuid CHAR(36) NOT NULL,
 kind VARCHAR(16) NOT NULL, faction VARCHAR(32) NOT NULL,
 election_start BIGINT NOT NULL, motivation TEXT NOT NULL,
 status VARCHAR(16) NOT NULL, created_at BIGINT NOT NULL,
 UNIQUE KEY one_application (player_uuid,kind,election_start)) ENGINE=InnoDB`,
 `CREATE TABLE IF NOT EXISTS political_application_events (
 id CHAR(36) PRIMARY KEY, application_id CHAR(36) NOT NULL,
 actor_id CHAR(36) NOT NULL, event VARCHAR(32) NOT NULL,
 created_at BIGINT NOT NULL, UNIQUE KEY application_event (application_id,event)) ENGINE=InnoDB`,
];
export async function initializeApplications(db) {
 for(const sql of applicationSchema)await db.prepare(sql).run();
}
export function applicationPrefix(kind,faction){return kind==='imperial'?'IMP':faction==='veiled_concord'?'CON':'HSE';}
export function applicationNumber(prefix,year,value){
 if(!['IMP','CON','HSE'].includes(prefix)||!Number.isInteger(year)||year<2026||!Number.isInteger(value)||value<1||value>999999)throw Error('Application number range exhausted or invalid');
 return `APP-${prefix}-${year}-${String(value).padStart(6,'0')}`;
}
// Transactional repository, not a publicly mounted route. Call only with an
// authenticated principal and freshly verified server-side game membership.
export async function submitApplication(db,{actor,context,input,now=Date.now(),clock=Date.now,makeId=randomUUID}) {
 if(!actor?.id||!context?.playerUuid||!/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(context.playerUuid))throw Error('Verified applicant identity required');
 const candidate=validateCandidature(input,context,now),start=Date.parse(candidate.start),player=context.playerUuid.toLowerCase();
 const prefix=applicationPrefix(candidate.kind,candidate.faction),year=new Date(now).getUTCFullYear(),scope=`${prefix}-${year}`;
 const [result]=await db.batch([{async run(connection){
  const query=async(sql,args=[])=>(await connection.execute(sql,args))[0];
  // Shared counter serializes duplicate submissions as well as allocation.
  await query('INSERT INTO political_application_counters(scope,value) VALUES(?,0) ON DUPLICATE KEY UPDATE scope=VALUES(scope)',[scope]);
  const counters=await query('SELECT value FROM political_application_counters WHERE scope=? FOR UPDATE',[scope]);
  const links=await query('SELECT minecraft_uuid FROM site_game_links WHERE user_id=? FOR UPDATE',[actor.id]);
  if(links[0]?.minecraft_uuid?.toLowerCase()!==player)throw Error('Linked account changed');
  const existing=await query('SELECT id,public_id,user_id,motivation,faction FROM political_applications WHERE player_uuid=? AND kind=? AND election_start=? FOR UPDATE',[player,candidate.kind,start]);
  if(existing.length){
   const row=existing[0];if(row.user_id!==actor.id)throw Error('An application already exists for this player');
   if(row.motivation!==candidate.motivation||row.faction!==candidate.faction)throw Error('Application already submitted; refresh your register');
   return {id:row.id,publicId:row.public_id,created:false};
  }
  // A request waiting for a database lock must not slip past the deadline.
  validateCandidature(input,context,clock());
  const value=Number(counters[0].value)+1,publicId=applicationNumber(prefix,year,value),id=makeId();
  await query('UPDATE political_application_counters SET value=? WHERE scope=?',[value,scope]);
  await query('INSERT INTO political_applications(id,public_id,user_id,player_uuid,kind,faction,election_start,motivation,status,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)',[id,publicId,actor.id,player,candidate.kind,candidate.faction,start,candidate.motivation,'submitted',now]);
  await query('INSERT INTO political_application_events(id,application_id,actor_id,event,created_at) VALUES(?,?,?,?,?)',[makeId(),id,actor.id,'submitted',now]);
  return {id,publicId,created:true};
 }}]);
 return result;
}
export async function ownApplications(db,userId){
 if(!userId)throw Error('Applicant required');
 const rows=await db.prepare('SELECT public_id,kind,faction,election_start,motivation,status,created_at FROM political_applications WHERE user_id=? ORDER BY created_at DESC LIMIT 100').bind(userId).all();
 return rows.results;
}
