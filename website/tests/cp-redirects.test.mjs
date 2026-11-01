import {test} from 'node:test';
import assert from 'node:assert/strict';
import {controlPanelRedirect} from '../node/cp-redirects.mjs';
test('account and support use CP; standalone commerce stays public',()=>{
 for(const [page,section]of [['account.html','game'],['portal.html','game'],['profile.html','game'],['login.html','game'],['tickets.html','tickets']]){
  const response=controlPanelRedirect(new Request('https://www.dunerealms.com/'+page+'?banId=case-1'));
  assert.equal(response.status,302);assert.equal(response.headers.get('Location'),'https://cp.dunerealms.com/?banId=case-1#'+section);
 }
 for(const path of ['forums.html','exchange.html','shop.html'])assert.equal(controlPanelRedirect(new Request('https://www.dunerealms.com/'+path)),null);
 assert.equal(controlPanelRedirect(new Request('https://www.dunerealms.com/account.html',{method:'POST'})),null);
});
