import test from 'node:test';
import assert from 'node:assert/strict';
import {publicGitFor} from '../node/git-public.mjs';
const db=row=>({prepare:()=>({bind:()=>({first:async()=>row})})});
const valid={forgejo_login:'unimatrix',profile_url:'https://www.dunerealms.com/git/unimatrix',owned_repo_count:2,verified_at:123};
test('public Git identity contains verified public fields only',async()=>{
 assert.deepEqual(await publicGitFor(db(valid),'private-id'),{login:'unimatrix',profileUrl:valid.profile_url,ownedRepoCount:2,repositoryOwner:true});
 assert.equal((await publicGitFor(db({...valid,owned_repo_count:0}),'id')).repositoryOwner,false);
});
test('unverified or mismatched destinations never become public verified links',async()=>{
 for(const change of [{verified_at:0},{profile_url:'https://evil.example/git/unimatrix'},{profile_url:'https://www.dunerealms.com/git/other'},{profile_url:'https://www.dunerealms.com/git/unimatrix?token=secret'},{forgejo_login:'../admin'}])assert.equal(await publicGitFor(db({...valid,...change}),'id'),null);
});
