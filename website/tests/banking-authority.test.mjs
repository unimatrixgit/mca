import test from 'node:test';import assert from 'node:assert/strict';
import {createBanking,projectBank} from '../control-panel/banking.mjs';import {projectClanWallet} from '../control-panel/clan-shop.mjs';
const player='11111111-1111-4111-8111-111111111111',other='22222222-2222-4222-8222-222222222222';
const bank={playerUuid:player,currency:'dunemarks',balanceDunemarks:'12.30',walletDunemarks:'45.67',ledger:[{id:'fixture',action:'deposit',amountDunemarks:'1.25',balanceDunemarks:'12.30',createdAt:1000}]};
test('bank and clan projections reject every non-string amount even when string coercion would match',()=>{
 for(const value of [12.34,['12.34'],null]){
  for(const field of ['balanceDunemarks','walletDunemarks'])assert.equal(projectBank({...bank,[field]:value},{playerUuid:player}),null,field);
  for(const field of ['amountDunemarks','balanceDunemarks'])assert.equal(projectBank({...bank,ledger:[{...bank.ledger[0],[field]:value}]},{playerUuid:player}),null,field);
  assert.equal(projectClanWallet({clanId:'clan',currency:'dunemarks',balanceDunemarks:value,ledger:[]},'clan'),null);
  for(const field of ['amountDunemarks','balanceDunemarks'])assert.equal(projectClanWallet({clanId:'clan',currency:'dunemarks',balanceDunemarks:'12.30',ledger:[{...bank.ledger[0],[field]:value}]},'clan'),null,field);
 }
 assert.equal(projectBank({...bank,balanceDunemarks:'9007199254740993.25',walletDunemarks:'-1.00'},{playerUuid:player}).balanceDunemarks,'9007199254740993.25');
});
test('expired session, revoked view, unlink and relink during a bank fetch withhold the old response',async()=>{
 for(const[mode,status]of [['session',401],['account',401],['permission',403],['unlink',409],['relink',409]]){
  let user={id:'fixture'},nodes=['web.bank.personal.view'],link=player,calls=0;
  const route=createBanking({}, {SERVER_CONTROL_TOKEN:'s'.repeat(64)},{accountNumber:async()=> 'DRPL 1010 0000 0127',authenticate:async()=>user,access:async()=>({nodes:[...nodes]}),linked:async()=>link,fetcher:async()=>{calls++;if(mode==='session')user=null;if(mode==='account')user={id:'new-fixture'};if(mode==='permission')nodes=[];if(mode==='unlink')link=null;if(mode==='relink')link=other;return Response.json(bank);}});
  const response=await route(new Request('https://cp.dunerealms.com/api/bank/personal'));assert.equal(response.status,status,mode);const body=await response.json();assert.equal(body.bank,undefined);assert.ok(!JSON.stringify(body).includes('12.30'));assert.equal(calls,1);
 }
});
test('staff-bank manage flags reflect the current grant after the upstream response',async()=>{
 let nodes=['web.bank.staff.view','web.bank.staff.manage'];const route=createBanking({}, {SERVER_CONTROL_TOKEN:'s'.repeat(64)},{accountNumber:async()=> 'DRPL 1010 0000 0127',authenticate:async()=>({id:'fixture'}),access:async()=>({nodes:[...nodes]}),linked:async()=>player,fetcher:async()=>{nodes=['web.bank.staff.view'];return Response.json({...bank,account:'server'});}});const response=await route(new Request('https://cp.dunerealms.com/api/bank/staff'));assert.equal(response.status,200);assert.equal((await response.json()).canManage,false);
});
