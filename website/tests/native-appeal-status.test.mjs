import test from 'node:test';
import assert from 'node:assert/strict';
import {LocalD1} from './d1-adapter.mjs';
import {effectiveAppealStatus,pendingNativeAppeal} from '../control-panel/native-appeal-status.mjs';
test('native closure and reopen project pending state without manufacturing a decision',()=>{
 for(const stored of ['pending','closed']){
  assert.equal(effectiveAppealStatus(stored,'open'),'pending');
  for(const state of ['closed','archived','deleted'])assert.equal(effectiveAppealStatus(stored,state),'closed');
  assert.equal(effectiveAppealStatus(stored,null),'unavailable');
 }
 for(const decision of ['approved','denied','approving'])for(const state of ['open','closed',null])assert.equal(effectiveAppealStatus(decision,state),decision);
});
test('closed native tickets do not block a fresh appeal; reopened undecided tickets do, with ownership preserved',async()=>{
 const db=new LocalD1(`CREATE TABLE native_ban_appeals(ticket_id INTEGER,ban_id TEXT,user_id TEXT,status TEXT,opened_at INTEGER);
 CREATE TABLE drsupport_ticket(ticket_id INTEGER,status_id INTEGER);
 CREATE TABLE drsupport_ticket_status(id INTEGER,state TEXT);
 INSERT INTO drsupport_ticket_status VALUES(1,'open'),(2,'closed');
 INSERT INTO native_ban_appeals VALUES(7,'ban-a','self','pending',1),(8,'ban-a','other','pending',2);
 INSERT INTO drsupport_ticket VALUES(7,1),(8,1);`);
 try{
  assert.equal((await pendingNativeAppeal(db,'ban-a','self')).ticket_id,7);
  await db.prepare('UPDATE drsupport_ticket SET status_id=2 WHERE ticket_id=7').run();assert.equal(await pendingNativeAppeal(db,'ban-a','self'),null);
  assert.equal((await pendingNativeAppeal(db,'ban-a','other')).ticket_id,8);
  await db.prepare("UPDATE native_ban_appeals SET status='closed' WHERE ticket_id=7").run();await db.prepare('UPDATE drsupport_ticket SET status_id=1 WHERE ticket_id=7').run();assert.equal((await pendingNativeAppeal(db,'ban-a','self')).ticket_id,7);
  for(const status of ['approved','denied']){await db.prepare('UPDATE native_ban_appeals SET status=? WHERE ticket_id=7').bind(status).run();assert.equal(await pendingNativeAppeal(db,'ban-a','self'),null);}
  await db.prepare("UPDATE native_ban_appeals SET status='approving' WHERE ticket_id=7").run();await db.prepare('UPDATE drsupport_ticket SET status_id=2 WHERE ticket_id=7').run();assert.equal((await pendingNativeAppeal(db,'ban-a','self')).ticket_id,7);
  await db.prepare('DELETE FROM drsupport_ticket WHERE ticket_id=7').run();assert.equal(await pendingNativeAppeal(db,'ban-a','self'),null);
 }finally{db.close();}
});
