import test from 'node:test';
import assert from 'node:assert/strict';
import { supporterDrafts, validateSupporterDrafts } from '../integrations/azuriom/supporter-drafts.mjs';
const copy = () => structuredClone(supporterDrafts);
test('four disabled fixed-cosmetic proposals; VIP price stays EUR50', () => {
  assert.equal(validateSupporterDrafts(supporterDrafts), true);
  assert.equal(supporterDrafts.length, 4);
  assert.equal(supporterDrafts.find(x => x.key === 'imperial-patron').proposedPriceCents, 5000);
  assert.ok(Object.isFrozen(supporterDrafts[0].rewards));
});
for (const [field, value] of [
  ['enabled', true], ['enabled', 'false'], ['priceConfirmed', true], ['rewardsConfirmed', true],
  ['state', 'active'], ['websiteRank', 'owners'], ['websiteRank', 'vip'],
  ['commands', ['lp user {uuid} parent add owners']], ['currency', 'USD'],
  ['proposedPriceCents', -1], ['proposedPriceCents', 0], ['proposedPriceCents', 10.5],
  ['proposedPriceCents', Infinity], ['proposedPriceCents', '1000'],
  ['proposedPriceCents', Number.MAX_SAFE_INTEGER], ['name', ''],
  ['rewards', []], ['rewards', ['profile-theme', 'profile-theme']],
  ['rewards', ['cape']], ['rewards', ['legendary-kit']], ['rewards', ['random-power-token']],
  ['rewards', ['dunemarks']], ['permissions', ['*']], ['money', 100],
]) test(`reject ${field}=${JSON.stringify(value)}`, () => {
  const drafts = copy(); drafts[0][field] = value;
  assert.throws(() => validateSupporterDrafts(drafts));
});
test('reject duplicate keys, absent required fields and non-catalogues', () => {
  const drafts = copy(); drafts[1].key = drafts[0].key;
  assert.throws(() => validateSupporterDrafts(drafts));
  for (const field of Object.keys(copy()[0])) {
    const missing = copy(); delete missing[0][field];
    assert.throws(() => validateSupporterDrafts(missing), field);
  }
  for (const input of [null, {}, [], [null], ['vip']])
    assert.throws(() => validateSupporterDrafts(input));
});
