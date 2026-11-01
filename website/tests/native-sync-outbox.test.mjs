import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {LocalD1} from './d1-adapter.mjs';
import {initializeNativeSyncOutbox,nativeSyncChange,drainNativeSyncOutbox} from '../node/native-sync-outbox.mjs';
const quiet={warn(){}};
const job=db=>db.prepare('SELECT * FROM native_sync_outbox').first();
test('rank mutation and event roll back together when the transaction fails',async()=>{
 const db=new LocalD1('CREATE TABLE rank_state (value TEXT PRIMARY KEY)');try{
  await initializeNativeSyncOutbox(db);
  await assert.rejects(db.batch([db.prepare("INSERT INTO rank_state VALUES('changed')"),nativeSyncChange(db),db.prepare("INSERT INTO rank_state VALUES('changed')")]));
  assert.equal(await job(db),null);assert.equal(await db.prepare('SELECT * FROM rank_state').first(),null);
 }finally{db.close();}
});
test('failed delivery survives database reopen and retries after durable backoff',async()=>{
 const directory=mkdtempSync(join(tmpdir(),'dune-native-outbox-')),filename=join(directory,'queue.sqlite');let db=new LocalD1('',filename),clock=100;
 try{
  await initializeNativeSyncOutbox(db);await db.batch([nativeSyncChange(db,clock)]);
  const failed=await drainNativeSyncOutbox(db,{now:()=>clock,sync:async()=>{throw Error('private adapter output must not persist');},logger:quiet});assert(failed.pending);
  const persisted=await job(db);assert.equal(persisted.attempts,1);assert.equal(persisted.next_attempt_at,5100);assert.equal(persisted.last_error,'Native synchronization failed');db.close();db=new LocalD1('',filename);
  let calls=0;const options={now:()=>clock,sync:async()=>{calls++;},logger:quiet};await drainNativeSyncOutbox(db,options);assert.equal(calls,0);
  clock=5100;assert.deepEqual(await drainNativeSyncOutbox(db,options),{pending:false});assert.equal(calls,1);assert.equal(await job(db),null);
 }finally{db.close();rmSync(directory,{recursive:true,force:true});}
});
test('change committed during delivery is retained and competing workers do not overlap',async()=>{
 const db=new LocalD1('');try{
  await initializeNativeSyncOutbox(db);await nativeSyncChange(db,100).run();let release,entered;
  const gate=new Promise(resolve=>release=resolve),start=new Promise(resolve=>entered=resolve);let calls=0;
  const first=drainNativeSyncOutbox(db,{now:()=>100,logger:quiet,sync:async()=>{calls++;entered();await gate;}});await start;
  assert((await drainNativeSyncOutbox(db,{now:()=>101,logger:quiet,sync:async()=>{calls++;}})).pending);assert.equal(calls,1);
  await nativeSyncChange(db,102).run();release();assert((await first).pending);assert.equal((await job(db)).lease_until,0);
  assert.deepEqual(await drainNativeSyncOutbox(db,{now:()=>103,logger:quiet,sync:async()=>{calls++;}}),{pending:false});assert.equal(calls,2);
 }finally{db.close();}
});
test('empty queue never loads native state; a crashed worker lease is recoverable',async()=>{
 const db=new LocalD1('');try{
  await initializeNativeSyncOutbox(db);let calls=0;const options={now:()=>100,sync:async()=>{calls++;},logger:quiet};assert.deepEqual(await drainNativeSyncOutbox(db,options),{pending:false});assert.equal(calls,0);
  await nativeSyncChange(db,1).run();await db.prepare("UPDATE native_sync_outbox SET lease_token='dead-process',lease_until=200").run();await drainNativeSyncOutbox(db,options);assert.equal(calls,0);
  await drainNativeSyncOutbox(db,{...options,now:()=>201});assert.equal(calls,1);assert.equal(await job(db),null);
 }finally{db.close();}
});
