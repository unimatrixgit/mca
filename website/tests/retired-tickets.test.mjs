import test from 'node:test';import assert from 'node:assert/strict';
import {retiredTicketResponse} from '../node/retired-tickets.mjs';
test('legacy ticket reads and mutations are retired without forwarding identity or private data',async()=>{
 for(const path of ['/api/tickets','/api/tickets/private/replies','/api/cp/tickets?view=staff','/api/cp/tickets/private/status','/api/cp/support-profile'])for(const method of ['GET','POST','HEAD']){
  const response=retiredTicketResponse(new Request('https://cp.dunerealms.com'+path,{method,headers:{'oai-authenticated-user-email':'forged@example.org'}}));assert.equal(response.status,410);assert.equal(response.headers.get('Cache-Control'),'no-store');const text=await response.text();assert(!text.includes('forged'));if(method==='HEAD')assert.equal(text,'');else assert.equal(JSON.parse(text).supportUrl,'https://support.dunerealms.com/');
 }
 for(const path of ['/api/native/tickets','/api/tickets-other','/api/mail/me','/api/cp/posts'])assert.equal(retiredTicketResponse(new Request('https://cp.dunerealms.com'+path)),null);
});
