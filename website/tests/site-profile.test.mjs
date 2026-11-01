import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {LocalD1} from './d1-adapter.mjs';
import {createPanel} from '../control-panel/server.mjs';
import {normalizeSocials,publicProfileFor} from '../node/profile.mjs';
const origin='https://cp.dunerealms.com';
const schema=`CREATE TABLE site_accounts(id TEXT PRIMARY KEY,username TEXT UNIQUE,display_name TEXT,password_salt TEXT,password_hash TEXT,role TEXT,created_at INTEGER);CREATE TABLE site_sessions(token_hash TEXT PRIMARY KEY,user_id TEXT,expires_at INTEGER);CREATE TABLE player_profiles(user_id TEXT PRIMARY KEY,display_name TEXT,minecraft_name TEXT,discord_name TEXT,created_at INTEGER,updated_at INTEGER);`+readFileSync(new URL('../node/schema.sql',import.meta.url),'utf8').split('CREATE TABLE IF NOT EXISTS site_game_links')[1].replace(/^/,'CREATE TABLE IF NOT EXISTS site_game_links').replace(/ ENGINE=InnoDB DEFAULT CHARSET=utf8mb4/g,'');
test('public social links use HTTPS and matching platform hosts',()=>{
 assert.equal(normalizeSocials({twitter:'https://x.com/traveller',twitch:'https://www.twitch.tv/traveller'}).twitter,'https://x.com/traveller');
 for(const value of ['javascript:alert(1)','http://twitch.tv/name','https://twitch.tv.evil.test/name','https://name:password@twitch.tv/name','https://example.com/channel'])assert.throws(()=>normalizeSocials({twitch:value}));
 assert.equal(normalizeSocials({website:'https://example.org/stream'}).website,'https://example.org/stream');
});
test('CP profile updates preserve private birthday and export only public fields',async()=>{
 const db=new LocalD1(schema),app=createPanel(db);try{
  const req=(path,body,cookie,requestOrigin=origin)=>app(new Request(origin+path,{method:body?'POST':'GET',headers:{Origin:requestOrigin,...(cookie?{Cookie:cookie}:{}),'Content-Type':'application/json'},...(body?{body:JSON.stringify(body)}:{})}));
  assert.equal((await req('/api/account/profile')).status,401);
  const registered=await req('/api/auth/register',{username:'streamer',password:'long test password 123',ageBand:'15plus'}),cookie=registered.headers.get('set-cookie').split(';')[0];
  const body={displayName:'Caravan Streamer',bio:'Live desert adventures',publicInfo:'Streams on weekends',birthday:'2000-02-02',publicBirthday:false,socials:{twitch:'https://twitch.tv/caravan',youtube:'https://youtube.com/@caravan',twitter:'https://x.com/caravan'}};
  assert.equal((await req('/api/account/profile',body,cookie,'https://evil.test')).status,403);
  assert.equal((await req('/api/account/profile',body,cookie)).status,200);
  const own=(await(await req('/api/account/profile',undefined,cookie)).json()).profile;assert.equal(own.bio,body.bio);assert.equal(own.birthday,body.birthday);assert.equal(own.socials.youtube,body.socials.youtube);
  const user=await db.prepare("SELECT id,username FROM site_accounts WHERE username='streamer'").first(),publicProfile=await publicProfileFor(db,user.id);
  assert.equal(publicProfile.bio,body.bio);assert.equal(publicProfile.socials.twitch,body.socials.twitch);assert(!Object.hasOwn(publicProfile,'birthday'));assert(!Object.hasOwn(publicProfile,'email'));assert.equal(user.username,'streamer');
  assert.equal((await req('/api/account/profile',{...body,socials:{twitter:'https://evil.test/pretend'}},cookie)).status,400);
 }finally{db.close();}
});
