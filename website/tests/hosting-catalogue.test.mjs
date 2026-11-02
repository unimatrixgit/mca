import test from 'node:test';import assert from 'node:assert/strict';
import {plans,cloudPlans,entryPlans,plusPlans,proPlan,quote,catalogue} from '../integrations/azuriom/hosting-catalogue.mjs';
test('hosting prices preserve markup, management, term discount and separate setup',()=>{
 assert.deepEqual(quote(plans[0],1),{monthly:7670,recurring:7670,setup:0,domain:0,first:7670,discount:0});
 assert.equal(quote(plans[0],3).recurring,21284);assert.equal(quote(plans[0],12).recurring,78234);
 assert.equal(quote(plans[1],1).setup,6125);assert.equal(quote(plans[1],12).setup,6125);assert.equal(quote(plans[1],12).recurring,131274);
 assert.equal(quote(plans[2],3).recurring,93434);assert.equal(quote(plans[2],12).recurring,343434);assert.equal(quote(plans[2],1).setup,16125);
 const web=quote(plans[3],1);assert.equal(web.monthly,842);assert.equal(web.domain,1813);assert.equal(web.setup,315);assert.equal(web.first,2970);
 assert.equal(quote(plans[3],12).domain,1813);assert.equal(quote(plans[3],12).setup,315);assert.equal(quote(plans[3],12).recurring,8588);
 assert.throws(()=>quote(plans[0],2));
});
test('all forty-five hosting SKUs are non-purchasable placeholders without reward commands',()=>{
 const c=catalogue();assert.equal(c.checkoutEnabled,false);assert.equal(c.provisioningEnabled,false);assert.equal(c.packages.length,45);
 assert.equal(new Set(c.packages.map(p=>p.key)).size,45);assert(c.packages.every(p=>p.is_enabled===false&&p.commands.length===0));
 assert.deepEqual(c.categories.map(c=>c.name),['Minecraft Hosting','Website Hosting','Entry / Novice','Cloud','Dedicated']);
 assert(c.categories.every(c=>c.description.includes('preview')));
 assert(c.categories.every(c=>!c.description.includes('data-package-url')&&!c.description.includes('/cart')));
});
test('regional clouds consolidate worlds and show specs without supplier names',()=>{
 assert.deepEqual(cloudPlans.map(p=>quote(p,1).monthly),[11244,11439,14169]);
 assert.deepEqual(cloudPlans.map(p=>quote(p.upgrade,1).monthly),[35944,36399,44394]);
 assert(cloudPlans.every(p=>p.worlds==='2 player worlds + 1 lobby + proxy'&&p.ram==='16 GB RAM'&&p.upgrade.ram==='64 GB RAM'));
 const c=catalogue(),html=c.categories.find(x=>x.slug==='cloud-hosting').description;
 assert.equal((html.match(/class="dune-hosting-card/g)||[]).length,6);assert.equal((html.match(/<summary>Upgrade: all worlds/g)||[]).length,0);assert.equal(plusPlans.filter(p=>p.cloud&&!p.entry).length,3);
 assert(!/hetzner/i.test(html));assert(!/hetzner/i.test(c.categories[1].description));
 for(const icon of ['🌍','🌎','🌏'])assert(html.includes(icon));assert(html.includes('dune-hosting-art-info'));assert(html.includes('upgrade-plus-imperial.svg'));assert(html.includes('imperial-cloth-'));assert(c.categories.find(c=>c.slug==='dedicated-hosting').description.includes('DEDICATED'));assert(html.includes('CLOUD'));
 assert(cloudPlans.every(p=>p.artKey==='caravan-outpost'));
});

test('Entry and plus products use Multiverse and Pro costs one physical host per world',()=>{
 assert.equal(quote(entryPlans[0],1).monthly,5654);assert.equal(quote(entryPlans[1],1).monthly,7670);
 assert.equal(quote(plusPlans.find(p=>p.key==='novice-cloud-plus'),1).monthly,11244);assert.equal(quote(plusPlans.find(p=>p.key==='novice-dedicated-plus'),1).monthly,12870);
 assert.equal(proPlan.monthlyCostCents,55400);assert.equal(quote(proPlan,1).monthly,72020);assert.equal(quote(proPlan,1).setup,30625);
 const c=catalogue();for(const [slug,count] of [['entry-hosting',4],['cloud-hosting',6],['dedicated-hosting',4],['web',1]])assert.equal((c.categories.find(c=>c.slug===slug).description.match(/class="dune-hosting-card/g)||[]).length,count);
 assert(!c.categories.find(c=>c.slug==='minecraft-hosting').description.includes('<article'));
 assert(c.categories.filter(c=>c.parent==='minecraft-hosting').length===3);assert(c.packages.filter(p=>p.name.includes(' + ')).length===15);
 assert(c.categories.every(c=>!c.description.includes('undefined')&&!/hetzner/i.test(c.description)));
});

test('quarter discount is 7.5%, annual 15%, never applied to setup or domain',()=>{
 for(const p of [...plans,...plusPlans]){const q1=quote(p,1),q3=quote(p,3),q12=quote(p,12);assert.equal(q3.discount,7.5);assert.equal(q12.discount,15);assert.equal(q3.setup,q1.setup);assert.equal(q12.domain,q1.domain);}
 assert.equal(quote(plans[1],3).recurring,35714);assert.equal(quote(plans[3],3).recurring,2337);assert.equal(quote(proPlan,3).recurring,199856);assert(catalogue().categories.find(c=>c.slug==='cloud-hosting').description.includes('Quarterly <small>−7.5%</small>'));assert(catalogue().categories.find(c=>c.slug==='dedicated-hosting').description.includes('featured-imperial.svg'));
});
