import{readFile}from'node:fs/promises';import{createRequire}from'node:module';import assert from'node:assert/strict';
import{mysqlSql}from'/home/dunerealms/apps/dunerealms/current/node/mysql.mjs';
import{initializeForumNews,drainForumNews}from'/tmp/forum-news372-candidate.mjs';
const require=createRequire('/home/dunerealms/apps/dunerealms/current/node/mysql.mjs'),mysql=require('mysql2/promise');
const config=JSON.parse(await readFile('/home/dunerealms/.config/integrations.json','utf8'));
const connection=await mysql.createConnection({host:config.host,user:config.username,password:config.password,database:config.database});
const db={prepare(sql){let values=[];return{bind(...v){values=v;return this},async first(){return(await connection.execute(mysqlSql(sql),values))[0][0]||null},async all(){return{results:(await connection.execute(mysqlSql(sql),values))[0]}},async run(){const[r]=await connection.execute(mysqlSql(sql).replace(/^CREATE TABLE /,'CREATE TEMPORARY TABLE '),values);return{meta:{changes:r.affectedRows}}}}},async batch(items){await connection.beginTransaction();try{let out=[];for(const item of items)out.push(await item.run());await connection.commit();return out;}catch(error){await connection.rollback();throw error;}}};
try{
 await connection.query('CREATE TEMPORARY TABLE website_posts(id CHAR(36) PRIMARY KEY,title TEXT,body TEXT,category VARCHAR(40),published_at BIGINT,status VARCHAR(16),author_id VARCHAR(40))');
 await connection.query('CREATE TEMPORARY TABLE site_accounts(id VARCHAR(40),display_name VARCHAR(80),username VARCHAR(80))');
 await initializeForumNews(db);await db.prepare("INSERT INTO website_posts VALUES('one','Title','Body','news',100,'published','a')").run();
 const sent=[],send=async post=>{sent.push(post);return{state:post.status==='archived'?'archived':'sent',thread_id:42,revision:post.revision}};
 await drainForumNews(db,{send,now:()=>1000});await drainForumNews(db,{send,now:()=>1000});assert.equal(sent.length,1);
 await db.prepare("UPDATE website_posts SET body='Correction',category='maintenance' WHERE id='one'").run();await drainForumNews(db,{send,now:()=>1000});assert.equal(sent.length,2);
 await db.prepare("UPDATE website_posts SET status='draft',body='PRIVATE' WHERE id='one'").run();await drainForumNews(db,{send,now:()=>1000});assert.deepEqual(Object.keys(sent[2]).sort(),['id','revision','status']);assert.equal(sent[2].status,'archived');
 await db.prepare("UPDATE website_posts SET status='published' WHERE id='one'").run();await drainForumNews(db,{send,now:()=>1000});assert.equal(sent.length,4);assert.equal((await db.prepare("SELECT thread_id FROM forum_news_delivery WHERE post_id='one'").first()).thread_id,42);
 console.log(JSON.stringify({actualMysqlStatements:true,temporaryTablesOnly:true,publishEditArchiveRepublish:true,noRepeatedDelivery:true,privatePayloadExcluded:true}));
}finally{await connection.end();}
