import test from 'node:test';import assert from 'node:assert/strict';
import {LocalD1} from './d1-adapter.mjs';
import {initializeForumNews,drainForumNews,forumNewsRevision} from '../node/forum-news.mjs';
const fixture=()=>new LocalD1('CREATE TABLE website_posts(id TEXT PRIMARY KEY,title TEXT,body TEXT,category TEXT,published_at INT,status TEXT,author_id TEXT);CREATE TABLE site_accounts(id TEXT,display_name TEXT,username TEXT);');
const success=post=>({state:post.status==='archived'?'archived':'sent',thread_id:42,revision:post.revision});
async function seed(db){await initializeForumNews(db);await db.prepare("INSERT INTO website_posts VALUES('one','Title','Body','news',100,'published','a')").run();}
test('published news backfills once with original author/date; drafts and future news never send',async()=>{
 const db=fixture();try{await initializeForumNews(db);await db.prepare("INSERT INTO site_accounts VALUES('a','Writer','writer')").run();for(const[id,status,time]of[['one','published',100],['draft','draft',100],['future','published',5000]])await db.prepare('INSERT INTO website_posts VALUES(?,?,?,?,?,?,?)').bind(id,'Title','Full body','news',time,status,'a').run();const sent=[];const send=async post=>{sent.push(post);return success(post);};await Promise.all([drainForumNews(db,{send,now:()=>1000}),drainForumNews(db,{send,now:()=>1000})]);await drainForumNews(db,{send,now:()=>1000});assert.equal(sent.length,1);assert.equal(sent[0].author_name,'Writer');assert.equal(sent[0].published_at,100);assert.equal(sent[0].body,'Full body');assert.equal((await db.prepare("SELECT thread_id FROM forum_news_delivery WHERE post_id='one'").first()).thread_id,42);}finally{db.close();}
});
test('unknown native outcome waits for reconciliation and does not blindly retry',async()=>{
 const db=fixture();try{await seed(db);let calls=0;const send=async()=>{calls++;throw Error('Timeout');};await drainForumNews(db,{send,now:()=>1000});await drainForumNews(db,{send,now:()=>1000000});assert.equal(calls,1);assert.equal((await db.prepare("SELECT state FROM forum_news_delivery WHERE post_id='one'").first()).state,'uncertain');}finally{db.close();}
});
test('published edits update the existing mapped topic once, retaining source identity and author',async()=>{
 const db=fixture();try{await seed(db);const sent=[];const send=async post=>{sent.push(post);return success(post);};await drainForumNews(db,{send,now:()=>1000});await db.prepare("UPDATE website_posts SET title='Corrected title',body='Corrected body',category='maintenance' WHERE id='one'").run();await drainForumNews(db,{send,now:()=>1000});await drainForumNews(db,{send,now:()=>1000});assert.equal(sent.length,2);assert.equal(sent[1].id,sent[0].id);assert.equal(sent[1].category,'maintenance');assert.notEqual(sent[1].revision,sent[0].revision);assert.equal((await db.prepare("SELECT thread_id FROM forum_news_delivery WHERE post_id='one'").first()).thread_id,42);}finally{db.close();}
});
test('withdrawal, draft, deletion and rescheduling send no unpublished article content; republish reuses topic',async()=>{
 for(const status of ['archived','draft','deleted','future','missing']){const db=fixture();try{
  await seed(db);const sent=[];const send=async post=>{sent.push(post);return success(post);};await drainForumNews(db,{send,now:()=>1000});
  if(status==='missing')await db.prepare("DELETE FROM website_posts WHERE id='one'").run();else await db.prepare("UPDATE website_posts SET status=?,published_at=?,body='PRIVATE DRAFT',title='SECRET TITLE' WHERE id='one'").bind(status==='future'?'published':status,status==='future'?5000:100).run();
  await drainForumNews(db,{send,now:()=>1000});await drainForumNews(db,{send,now:()=>1000});assert.equal(sent.length,2);assert.equal(sent[1].status,'archived');assert.deepEqual(Object.keys(sent[1]).sort(),['id','revision','status']);assert.equal((await db.prepare("SELECT state FROM forum_news_delivery WHERE post_id='one'").first()).state,'archived');
  if(status==='missing')continue;
  await db.prepare("UPDATE website_posts SET status='published',published_at=100,body='Republished body' WHERE id='one'").run();await drainForumNews(db,{send,now:()=>1000});assert.equal(sent.length,3);assert.equal(sent[2].status,'published');assert.equal((await db.prepare("SELECT thread_id FROM forum_news_delivery WHERE post_id='one'").first()).thread_id,42);
 }finally{db.close();}}
});
test('editing during native delivery is picked up next tick rather than marking the new content delivered',async()=>{
 const db=fixture();try{await seed(db);const sent=[];const send=async post=>{sent.push(post);if(sent.length===1)await db.prepare("UPDATE website_posts SET body='New revision' WHERE id='one'").run();return success(post);};await drainForumNews(db,{send,now:()=>1000});await drainForumNews(db,{send,now:()=>1000});assert.deepEqual(sent.map(p=>p.body),['Body','New revision']);assert.equal((await db.prepare("SELECT delivered_hash FROM forum_news_revision WHERE post_id='one'").first()).delivered_hash,sent[1].revision);}finally{db.close();}
});
test('wrong native revision never confirms the website link or delivery',async()=>{
 const db=fixture();try{await seed(db);await drainForumNews(db,{send:async p=>({...success(p),revision:'wrong'}),now:()=>1000});assert.equal((await db.prepare("SELECT state,thread_id FROM forum_news_delivery WHERE post_id='one'").first()).state,'uncertain');assert.equal(await db.prepare("SELECT * FROM forum_news_revision WHERE post_id='one'").first(),null);}finally{db.close();}
});
test('archiving while the first job is queued never creates a new withdrawn topic',async()=>{
 const db=fixture();try{await seed(db);await db.prepare("INSERT INTO forum_news_delivery(post_id,state) VALUES('one','queued')").run();await db.prepare("UPDATE website_posts SET status='draft',body='SECRET' WHERE id='one'").run();let calls=0;await drainForumNews(db,{send:async p=>{calls++;return success(p);},now:()=>1000});assert.equal(calls,0);assert.equal((await db.prepare("SELECT state FROM forum_news_delivery WHERE post_id='one'").first()).state,'withdrawn');await db.prepare("UPDATE website_posts SET status='published' WHERE id='one'").run();await drainForumNews(db,{send:async p=>{calls++;return success(p);},now:()=>1000});assert.equal(calls,1);}finally{db.close();}
});
test('category and author changes revise publication, while draft fields do not revise the withdrawal payload',()=>{
 const base={id:'one',status:'published',title:'A',body:'B',category:'news',published_at:100,author_name:'Writer'};assert.notEqual(forumNewsRevision(base,1000).revision,forumNewsRevision({...base,author_name:'Other'},1000).revision);assert.deepEqual(forumNewsRevision({...base,status:'draft'},1000),forumNewsRevision({...base,status:'draft',body:'PRIVATE'},1000));
 for(const published_at of [null,undefined,NaN,Infinity,'bad'])assert.equal(forumNewsRevision({...base,published_at},1000).status,'archived');
});
