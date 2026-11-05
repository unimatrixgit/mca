import test from 'node:test';import assert from 'node:assert/strict';import{createKitStudio,validateStudioRequest}from'../control-panel/kit-studio.mjs';
const origin='https://cp.dunerealms.com',uuid='11111111-1111-1111-1111-111111111111';
const save={action:'save',quality:'common',version:0,name:'Desert weaver',color:'#385b49',lore:['A desert commission.']};
function fixture({user={id:'account'},nodes=['web.menu.inventory'],linked=uuid,arcUuid=uuid,unlocked=true,unlockAll=false,transactionLink=uuid,current=null}={}){const writes=[];let transactions=0;const db={prepare(sql){return{bind(){return this},async first(){return linked?{minecraft_uuid:linked}:null},async all(){return{results:[]}}}},async batch(statements){transactions++;const c={async execute(sql,values){if(sql.startsWith('SELECT minecraft_uuid'))return[[{minecraft_uuid:transactionLink,linked_at:1}]];if(sql.startsWith('SELECT id,version,upgrade_json,active FROM site_kit_blueprints'))return[current?[current]:[]];writes.push({sql,values});return[{affectedRows:1}]}};for(const s of statements)await s.run(c)}};const route=createKitStudio(db,{SERVER_CONTROL_TOKEN:'t'.repeat(64)},{authenticate:async()=>user,access:async()=>({nodes}),fetcher:async()=>Response.json({playerUuid:arcUuid,chapters:['common','uncommon','rare','very_rare','legendary'].map((quality,index)=>({quality,index,unlocked:unlockAll||index===0&&unlocked}))})});return{writes,transactions:()=>transactions,post:(body,requestOrigin=origin)=>route(new Request(origin+'/api/kits/studio',{method:'POST',headers:{origin:requestOrigin},body:JSON.stringify(body)}))}};
test('studio accepts cosmetic fields only and enforces tier prices',()=>{assert.equal(validateStudioRequest(save).color,'#385B49');for(const b of [{...save,owner_user_id:'victim'},{...save,quality:'admin'},{...save,lore:['§kHidden']},{...save,name:'bad\nname'},{action:'publish',quality:'common',blueprintVersion:1,listingVersion:0,shopType:'player',priceCents:9999}])assert.equal(validateStudioRequest(b),null)});
test('ordinary account must have inventory access, verified identity and same-origin writes',async()=>{for(const [options,status]of [[{user:null},401],[{nodes:[]},403],[{linked:null},409]]){const f=fixture(options);assert.equal((await f.post(save)).status,status);assert.equal(f.transactions(),0)}const f=fixture();assert.equal((await f.post(save,'https://other.invalid')).status,403);assert.equal(f.transactions(),0)});
test('locked tier or cross-player arc cannot authorize a design',async()=>{for(const [options,status]of [[{unlocked:false},403],[{arcUuid:'22222222-2222-2222-2222-222222222222'},503]]){const f=fixture(options);assert.equal((await f.post(save)).status,status);assert.equal(f.transactions(),0)}});
test('UUID unlink or relink during save prevents writes under the new identity',async()=>{const f=fixture({transactionLink:null});assert.equal((await f.post(save)).status,409);assert.equal(f.writes.length,0)});
test('stale design revision is rejected before changing a published blueprint',async()=>{const f=fixture({current:{id:'design',version:2}});assert.equal((await f.post({...save,version:1})).status,409);assert.equal(f.writes.length,0)});
test('saved design is attributed only to authenticated account and audited',async()=>{const f=fixture();assert.equal((await f.post(save)).status,200);const insert=f.writes.find(w=>w.sql.startsWith('INSERT INTO site_kit_blueprints'));assert.equal(insert.values[1],'account');assert.equal(insert.values[2],'common');assert.ok(f.writes.some(w=>w.sql.startsWith('INSERT INTO account_audit')))});
test('official/faction/server kinds have distinct shop types and do not accept client-selected beneficiaries',()=>{
 const base={action:'publish',quality:'legendary',blueprintVersion:1,listingVersion:0,priceCents:100,kind:'official_sale',shopType:'official',officeKey:'emperor'};
 assert.equal(validateStudioRequest(base).kind,'official_sale');assert.equal(validateStudioRequest({...base,shopType:'player'}),null);assert.equal(validateStudioRequest({...base,sellerUuid:'victim'}),null);assert.equal(validateStudioRequest({...base,kind:'server_sale',shopType:'server',officeKey:undefined}).kind,'server_sale');
});
test('ordinary player cannot save an official or faction design without a current native office',async()=>{
 for(const kind of ['official_sale','faction_sale']){const f=fixture();assert.equal((await f.post({...save,kind})).status,403);assert.equal(f.transactions(),0);}
});

test('upgraded Player Kit cannot publish before receipt delivery supports the bonus',async()=>{const f=fixture({unlockAll:true,current:{id:'design',version:2,upgrade_json:'{"policy":"legendary-player-v1"}'}});const r=await f.post({action:'publish',kind:'personal_sale',quality:'legendary',blueprintVersion:2,listingVersion:0,shopType:'player',priceCents:200000});assert.equal(r.status,409);assert.match((await r.json()).message,/game delivery rollout/);assert.equal(f.writes.length,0);});


test('combined publication validates the design and listing before a transaction',async()=>{
 const body={...save,action:'save_publish',listingVersion:0,shopType:'player',priceCents:10000};
 assert.equal(validateStudioRequest(body).action,'save_publish');
 for(const bad of [{...body,priceCents:9999},{...body,listingVersion:-1},{...body,owner_user_id:'victim'},{...body,name:'x'},{...body,shopType:'server'}]){assert.equal(validateStudioRequest(bad),null);const f=fixture();assert.equal((await f.post(bad)).status,400);assert.equal(f.transactions(),0);}
});
test('archive and restore require an existing current owner revision',async()=>{
 for(const action of ['archive','restore']){assert.equal(validateStudioRequest({action,quality:'common',version:0}),null);assert.equal(validateStudioRequest({action,quality:'common',version:1,userId:'victim'}),null);const f=fixture();assert.equal((await f.post({action,quality:'common',version:1})).status,409);assert.equal(f.writes.length,0);}
});
test('combined upgraded publication preserves the game rollout gate',async()=>{
 const f=fixture({unlockAll:true,current:{id:'design',version:2,active:1,upgrade_json:'{"policy":"legendary-player-v1"}'}});
 assert.equal((await f.post({...save,action:'save_publish',quality:'legendary',version:2,listingVersion:0,shopType:'player',priceCents:160000})).status,409);assert.equal(f.writes.length,0);
});
test('archived design cannot be silently replaced by draft save',async()=>{const f=fixture({current:{id:'design',version:3,active:0}});assert.equal((await f.post({...save,version:3})).status,409);assert.equal(f.writes.length,0);});
