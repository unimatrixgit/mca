import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
test('authored homepage retains one public ban module after its existing portal sections',()=>{
 const html=readFileSync(new URL('../web/index.html',import.meta.url),'utf8');
 for(const id of ['home-news','home-forum','home-bans','portal-ban-cards'])assert.equal((html.match(new RegExp('id="'+id+'"','g'))||[]).length,1,id);
 const news=html.indexOf('id="home-news"'),hero=html.indexOf('class="hero"'),forum=html.indexOf('id="home-forum"'),menu=html.indexOf('id="realm-menu"'),bans=html.indexOf('id="home-bans"');
 assert(news<hero&&hero<forum&&forum<menu&&menu<bans&&bans<html.indexOf('</main>'));
 const block=html.slice(bans,html.indexOf('</section>',bans));assert(block.includes('Latest bans &amp; appeals'));assert(block.includes('LATEST 9'));assert(block.includes('Loading moderation records…'));assert(!block.includes('DemoPlayer'));assert(!block.includes('minecraft_uuid'));assert(!block.includes('data-ban-id'));
 const script=readFileSync(new URL('../web/home-news.js',import.meta.url),'utf8');assert(script.includes('portal-ban-cards'));
});
