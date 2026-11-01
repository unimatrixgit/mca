import {test} from 'node:test';
import assert from 'node:assert/strict';
import {PORTAL_LINKS,updatePortalNavigation,pageContextFor,SHARED_HEADER} from '../scripts/portal-navigation.mjs';
test('portal navigation preserves the exact approved order and canonical destinations',()=>{
 assert.deepEqual(PORTAL_LINKS.map(x=>x[0]),['Home','Boards','Xchange','Discord','Manual/wiki','Staff roster','Support center','Account center','Clan center']);
 assert.equal(PORTAL_LINKS[1][1],'https://forums.dunerealms.com/');assert.equal(PORTAL_LINKS[6][1],'https://support.dunerealms.com/');assert.equal(PORTAL_LINKS[7][1],'https://cp.dunerealms.com/');
 const html='<header><nav aria-label="Main navigation"><a>Old</a></nav></header><nav aria-label="Other">Keep</nav>';
 const changed=updatePortalNavigation(html);assert.ok(changed.endsWith('<nav aria-label="Other">Keep</nav>'));assert.equal(updatePortalNavigation(changed),changed);assert.equal((changed.match(/<span>/g)||[]).length,9);
});
test('shared header uses one illustration per destination and page markers use full artwork',()=>{
 assert.equal((SHARED_HEADER.match(/<img /g)||[]).length,10);
 assert.equal(new Set(PORTAL_LINKS.map(x=>x[2])).size,9);
 assert.equal(pageContextFor('https://cp.dunerealms.com/#exchange').icon,'nav-exchange-full-v2.webp');
 assert.equal(pageContextFor('https://forums.dunerealms.com/showthread.php?tid=7').label,'Boards');
 assert.equal(pageContextFor('https://wiki.dunerealms.com/').label,'Manual/wiki');
 assert.equal(pageContextFor('https://support.dunerealms.com/tickets.php').label,'Support center');
});

test('repeated header publication retains exactly one versioned stylesheet and script',()=>{let html='<html><head></head><body>'+SHARED_HEADER+'</body></html>';html=updatePortalNavigation(html);assert.equal(updatePortalNavigation(html),html);assert.equal((html.match(/portal-navigation.js/g)||[]).length,1);assert.equal((html.match(/portal-navigation.css/g)||[]).length,1);});

test('staff roster menu addition preserves all domain context mappings',()=>{assert.equal(pageContextFor('https://www.dunerealms.com/staff.html').icon,'nav-staff-full-v2.webp');for(const [url,label]of [['https://support.dunerealms.com/','Support center'],['https://clans.dunerealms.com/','Clan center'],['https://cp.dunerealms.com/','Account center']])assert.equal(pageContextFor(url).label,label);});
