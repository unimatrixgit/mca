import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,readdirSync} from 'node:fs';
import worker from '../dist/server/index.js';
import {runInNewContext} from 'node:vm';
// Requested commemorative notes are an explicit, disclosed fan-art exception.
const withoutFanNotes=text=>text.replace(/<section class="dunemark-art"[\s\S]*?<\/section>/g,'').replace(/<section><h2>Commemorative note artwork<\/h2>[\s\S]*?<\/section>/g,'');

test('Retired journal routes and data are no longer served',async()=>{
  for(const file of ['prophecy.html','prophecy-data.json','prophecy-graphics.json','prophecy.js','prophecy.css','prophecy-the-heir-of-caladan.html','prophecy-the-making-of-muad-dib.html','prophecy-the-price-of-an-imperium.html','prophecy-prescience-and-the-desert.html','prophecy-the-preacher-returns.html','prophecy-the-path-paul-refused.html']){
    for(const method of ['GET','HEAD'])assert.equal((await worker.fetch(new Request('https://example.test/'+file,{method}),{})).status,404,file);
  }
});

test('Every public page has one visible notice and no retired story content',async()=>{
  const web=new URL('../web/',import.meta.url);
  for(const file of readdirSync(web).filter(file=>/\.(html|json|js|css)$/.test(file))){
    const text=readFileSync(new URL(file,web),'utf8');
    assert.doesNotMatch(text,/\bprophe\w*\b/i,file);
    const story=file==='manual-data.json'?JSON.parse(text).map(chapter=>withoutFanNotes(chapter.body)).join(' '):withoutFanNotes(text);
    assert.doesNotMatch(story,/\b(?:Leto|Ghanima|Irulan)\b/i,file);
    if(!file.endsWith('.html'))continue;
    const response=await worker.fetch(new Request('https://example.test/'+file),{}),html=await response.text();
    assert.equal(response.status,200,file);
    assert.equal((html.match(/class="legal-notice"/g)||[]).length,1,file);
    assert.match(html,/NOT AN OFFICIAL MINECRAFT SERVER OR PRODUCT/,file);
    assert.match(html,/not affiliated with, endorsed, sponsored or approved/,file);
    assert.match(html,/Show contact email/,file);
    assert.doesNotMatch(html,/unimatrix@dunerealms\.com|mailto:/,file);
    assert.doesNotMatch(html,/Project Dune|dunerealms\.com\.com/i,file);
    for(const [,command,body] of html.matchAll(/<button\b[^>]*data-copy="([^"]+)"[^>]*>([\s\S]*?)<\/button>/g)){
      assert.ok(body.replace(/<[^>]+>/g,'').trim().startsWith(command),file+' must display the command it copies');
    }
    if(file!=='wiki.html'){
      const prose=withoutFanNotes(html).replace(/<(code|pre|script)\b[^>]*>[\s\S]*?<\/\1>/gi,'').replace(/<[^>]+>/g,' ');
      assert.doesNotMatch(prose,/\b(?:Atreides|Harkonnen|Corrino|Ordos|Fremen|Arrakis|Caladan|Giedi Prime|Kaitain|Sigma Draconis IV|CHOAM|Solaris|melange|Shai-Hulud|Spacing Guild|stillsuits?|sietch(?:es)?)\b/i,file);
    }
  }
  assert.match(readFileSync(new URL('../node/public/login.html',import.meta.url),'utf8'),/class="legal-notice"/);
});

test('Live legacy feed labels display original names without mutating identifiers',()=>{
  const context={window:{}};
  runInNewContext(readFileSync(new URL('../web/public-names.js',import.meta.url),'utf8'),context);
  const labels=context.window.realmNames;
  assert.equal(labels.text('House Atreides on Caladan'),'House Aerdrecht on Kaladam');
  assert.equal(labels.material('fremen_stillsuit'),'Vrijduin reclaimer suit');
  assert.equal(labels.material('spice_melange'),'spice');
  assert.equal(labels.text('Project Dune'),'dunerealms.com');
  assert.equal(labels.text('dunerealms.com'),'dunerealms.com');
  assert.equal(labels.text('arrakis_world_id'),'arrakis_world_id');
  assert.equal(labels.text('spice'),'spice');
});
