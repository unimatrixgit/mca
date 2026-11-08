import test from 'node:test';
import assert from 'node:assert/strict';
import {ribbonFor, MENU_RELEASE} from '../control-panel/public/menu-ribbons.mjs';
test('only current release changes show; NEW wins over UPDATE', () => {
  const changes = {page:{introducedIn:'1.0.0.4',updatedIn:'1.0.0.4'}};
  assert.equal(ribbonFor('page','1.0.0.4',changes).label,'NEW');
  assert.equal(ribbonFor('page','1.0.0.5',changes),null);
  assert.equal(ribbonFor('page','',changes),null);
});
test('preview wording and unchanged/unreleased exclusions', () => {
  assert.match(ribbonFor('landsraad',MENU_RELEASE).description,/design preview/);
  assert.equal(ribbonFor('site-profile',MENU_RELEASE).label,'UPDATE');
  for (const page of ['quests','hosting-control','webmail','rewards-codes','internal-mail']) assert.equal(ribbonFor(page,MENU_RELEASE),null);
});
test('staff menu entries use the same release rules', () => {
  assert.equal(ribbonFor('staff-tickets',MENU_RELEASE,{'staff-tickets':{updatedIn:MENU_RELEASE}}).label,'UPDATE');
});
