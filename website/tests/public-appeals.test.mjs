import test from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {publicAppealCards,publicBanList} from '../node/public-bans.mjs';
test('actual queries paginate decisions, track new appeals and protect private fields',async()=>{
 const sql=new DatabaseSync(':memory:');
 sql.exec('CREATE TABLE moderation_bans(id TEXT,source_key TEXT,player_name TEXT,banned_by TEXT,reason TEXT,scope TEXT,created_text TEXT,expires_text TEXT,active INTEGER); CREATE TABLE native_ban_appeals(id TEXT,ban_id TEXT,status TEXT,opened_at INTEGER,decided_at INTEGER,decision_reason TEXT,ticket_id INTEGER DEFAULT 1); CREATE TABLE drsupport_ticket(ticket_id INTEGER,status_id INTEGER);CREATE TABLE drsupport_ticket_status(id INTEGER,state TEXT);INSERT INTO drsupport_ticket VALUES(1,1);INSERT INTO drsupport_ticket_status VALUES(1,\'open\');');
 const ban=sql.prepare('INSERT INTO moderation_bans VALUES(?,?,?,?,?,?,?,?,?)'),appeal=sql.prepare('INSERT INTO native_ban_appeals(id,ban_id,status,opened_at,decided_at,decision_reason) VALUES(?,?,?,?,?,?)');
 for(let i=0;i<8;i++){ban.run(String(i),'network:'+i,'Player'+i,'0','Public reason','network','2026-11-02',null,i===1?1:0);appeal.run('a'+i,String(i),i===1?'denied':'approved',i,i,'PRIVATE');}
 ban.run('pending','network:p','Waiting','Mod','Reason','network','2026-11-02',null,1);appeal.run('p','pending','pending',20,null,'PRIVATE');
 for(const [id,source,scope] of [['demo','demo:x','network'],['legacy','legacy:x','yousmp-legacy']]){ban.run(id,source,id,'Mod','Reason',scope,'2026-11-02',null,0);appeal.run(id,id,'approved',30,30,'PRIVATE');}
 const db={prepare(query){let args=[];return{bind(...v){args=v;return this;},async first(){return sql.prepare(query).get(...args);},async all(){return{results:sql.prepare(query).all(...args)};}};}};
 const first=await (await publicAppealCards(db,new Request('https://example.org/?page=1'))).json();assert.equal(first.total,8);assert.equal(first.bans.length,6);assert.equal(first.pages,2);assert(!JSON.stringify(first).includes('PRIVATE'));assert(!first.bans.some(b=>b.appealStatus==='pending'));
 const last=await (await publicAppealCards(db,new Request('https://example.org/?page=999'))).json();assert.equal(last.page,2);assert.equal(last.bans.length,2);assert.equal(last.bans.find(b=>b.id==='1').status,'Active');
 const list=await (await publicBanList(db,new Request('https://example.org/'))).json();assert.equal(list.total,9);assert.equal(list.bans.find(b=>b.id==='pending').appealStatus,'pending');assert.equal(list.bans.find(b=>b.id==='0').status,'Unbanned');assert.equal(list.bans.find(b=>b.id==='1').appealStatus,'denied');assert(!list.bans.some(b=>['demo','legacy'].includes(b.id)));
 // A fresh appeal replaces the old public decision without revealing its private text.
 appeal.run('new','0','pending',100,null,'PRIVATE');const refreshed=await (await publicBanList(db,new Request('https://example.org/'))).json();assert.equal(refreshed.bans.find(b=>b.id==='0').appealStatus,'pending');
 sql.exec("UPDATE drsupport_ticket_status SET state='closed'");const closed=await (await publicBanList(db,new Request('https://example.org/'))).json();assert.equal(closed.bans.find(b=>b.id==='pending').appealStatus,null);assert.equal(closed.bans.find(b=>b.id==='1').appealStatus,'denied');sql.close();
});
