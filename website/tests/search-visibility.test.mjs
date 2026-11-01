import test from 'node:test';
import assert from 'node:assert/strict';
import {LocalD1} from './d1-adapter.mjs';
import {createSearchVisibility,newsPath} from '../node/search-visibility.mjs';
function setup(){const db=new LocalD1('CREATE TABLE website_posts(id TEXT,title TEXT,body TEXT,category TEXT,status TEXT,published_at INTEGER,updated_at INTEGER);');return db;}
const post={id:'40c67ad3-3814-4702-9b25-14edf5ca2a80',title:'Alpha update & community',body:'A published update.\n\n<script>alert(1)</script>',category:'changelogs',published_at:Date.now()-10000,updated_at:Date.now()-5000};
async function insert(db,p,status='published'){await db.prepare('INSERT INTO website_posts(id,title,body,category,status,published_at,updated_at) VALUES(?,?,?,?,?,?,?)').bind(p.id,p.title,p.body,p.category,status,p.published_at,p.updated_at).run();}
test('published news is crawlable and escaped, legacy links redirect permanently, drafts stay absent',async()=>{const db=setup();try{
 await insert(db,post);await insert(db,{...post,id:'00000000-0000-4000-8000-000000000001',title:'Private draft'},'draft');
 const seo=createSearchVisibility(db),req=p=>new Request('https://www.dunerealms.com'+p);
 const legacy=await seo.route(req('/news-archive.html?post='+post.id));assert.equal(legacy.status,301);assert.equal(legacy.headers.get('location'),'https://www.dunerealms.com'+newsPath(post));
 const page=await seo.route(req(newsPath(post))),html=await page.text();assert.equal(page.status,200);assert(html.includes('&lt;script&gt;alert(1)&lt;/script&gt;'));assert(!html.includes('<script>alert(1)</script>'));assert(html.includes('NewsArticle'));assert(html.includes('rel="canonical"'));
 const sitemap=await(await seo.route(req('/sitemap.xml'))).text();assert(sitemap.includes(newsPath(post)));assert(!sitemap.includes('Private draft'));assert(!sitemap.includes('/account'));
 assert.equal((await seo.route(req('/news/00000000-0000-4000-8000-000000000001'))).status,404);
 const home=await seo.html(req('/'),'<html><head><title>Old</title></head><body><div data-news-module="news"></div></body></html>');assert(home.includes('data-server-news'));assert(home.includes(newsPath(post)));assert.equal((home.match(/<title>/g)||[]).length,1);
 }finally{db.close();}});
