import test from 'node:test';
import assert from 'node:assert/strict';
import {createElectionApplications} from '../control-panel/election-application-route.mjs';
const origin='https://cp.dunerealms.com',player='12345678-1234-1234-1234-123456789abc';
const input={kind:'house',start:'2026-11-15T09:00:00Z',accepted:true,motivation:'I will serve the House and its members.',faction:'harkonnen',user_id:'victim'};
const request=(body=input)=>new Request(origin+'/api/politics/applications',{method:'POST',headers:{origin,'content-type':'application/json'},body:JSON.stringify(body)});
function setup(extra={}){
 const saves=[];
 const handler=createElectionApplications(null,{}, {authenticate:async()=>({id:'owner'}),access:async()=>({nodes:['web.menu.game']}),excluded:async()=>false,linked:async()=>player,preview:async()=>Response.json({linked:true,factionStatus:'verified',faction:'atreides'}),clock:()=>Date.parse('2026-11-08T00:00:00Z'),submit:async(db,args)=>{saves.push(args);return{publicId:'APP-HSE-2026-000001',created:true};},list:async(db,id)=>[{owner:id}],...extra});
 return {handler,saves};
}
test('saved application identity and House come from verified context',async()=>{const {handler,saves}=setup();assert.equal((await handler(request())).status,201);assert.equal(saves[0].actor.id,'owner');assert.equal(saves[0].context.faction,'atreides');});
test('signed-out, service and unauthorized accounts cannot submit',async()=>{
 for(const extra of [{authenticate:async()=>null},{excluded:async()=>true},{access:async()=>({nodes:[]})}]){const {handler,saves}=setup(extra);assert([401,403].includes((await handler(request())).status));assert.equal(saves.length,0);}
});
test('cross-origin requests and account override queries fail',async()=>{const {handler,saves}=setup();assert.equal((await handler(new Request(origin+'/api/politics/applications',{method:'POST',headers:{origin:'https://evil.example'}}))).status,403);assert.equal((await handler(new Request(origin+'/api/politics/applications?user=other'))).status,400);assert.equal(saves.length,0);});
test('expired deadline and changed link never reach storage',async()=>{
 let count=0;
 for(const extra of [{clock:()=>Date.parse(input.start)},{linked:async()=>++count===1?player:'other'}]){const {handler,saves}=setup(extra);assert([400,409].includes((await handler(request())).status));assert.equal(saves.length,0);}
});
test('read register is scoped to authenticated user',async()=>{const {handler}=setup();assert.deepEqual(await(await handler(new Request(origin+'/api/politics/applications'))).json(),{applications:[{owner:'owner'}]});});
test('unavailable House blocks House applications',async()=>{const {handler,saves}=setup({preview:async()=>Response.json({linked:true,factionStatus:'unavailable',faction:null})});assert.equal((await handler(request())).status,400);assert.equal(saves.length,0);});
