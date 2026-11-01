import test from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {nativeGitIdentity,createGitIdentityRoute,gitProfileUrl} from '../node/git-identity.mjs';

test('ownership comes from immutable repository owner id, not collaborator access or a username',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'dune-git-')),path=join(dir,'native.db');
 const db=new DatabaseSync(path);
 db.exec('CREATE TABLE user(id INTEGER PRIMARY KEY,name TEXT,is_active INTEGER,is_admin INTEGER,prohibit_login INTEGER,type INTEGER); CREATE TABLE repository(id INTEGER PRIMARY KEY,owner_id INTEGER,name TEXT,description TEXT,is_private INTEGER); CREATE TABLE collaborator(user_id INTEGER,repo_id INTEGER);');
 db.exec("INSERT INTO user VALUES(1,'actual_owner',1,0,0,0),(2,'collaborator',1,0,0,0); INSERT INTO repository VALUES(10,1,'private-code','Private project',1); INSERT INTO collaborator VALUES(2,10);");
 try{
  assert.equal((await nativeGitIdentity(1,path)).repositoryOwner,true);
  const collaborator=await nativeGitIdentity(2,path);assert.equal(collaborator.ownedRepoCount,0);assert.deepEqual(collaborator.repos,[]);
  db.exec("UPDATE user SET name='renamed_owner' WHERE id=1");assert.equal((await nativeGitIdentity(1,path)).profileUrl,'https://www.dunerealms.com/git/renamed_owner');
  db.exec('DELETE FROM repository WHERE id=10');assert.equal((await nativeGitIdentity(1,path)).repositoryOwner,false);
  db.exec('UPDATE user SET prohibit_login=1 WHERE id=1');assert.equal(await nativeGitIdentity(1,path),null);
 }finally{db.close();await rm(dir,{recursive:true,force:true});}
});
test('self API fails closed without a central session and rejects mutations',async()=>{
 const db={prepare(){throw Error('No database access should occur without session');}};
 const route=createGitIdentityRoute(db,{});
 assert.equal((await route(new Request('https://www.dunerealms.com/api/git/me'))).status,401);
 assert.equal((await route(new Request('https://www.dunerealms.com/api/git/me',{method:'POST'}))).status,405);
 assert.equal((await route(new Request('https://www.dunerealms.com/git/sso/callback?state=forged&code=forged'))).status,403);
 assert.equal(gitProfileUrl('bad/name'),'https://www.dunerealms.com/git/bad%2Fname');
});
