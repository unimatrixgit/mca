// Design data only. Deliberately not imported by checkout or entitlement delivery.
export const cosmeticRewards = Object.freeze([
  'bronze-seal', 'silver-seal', 'imperial-seal', 'sapphire-seal',
  'profile-theme', 'profile-frame', 'profile-banner', 'supporter-acknowledgement',
]);
const rewardSet = new Set(cosmeticRewards);
export const supporterDrafts = Object.freeze([
  { key: 'caravan-supporter', name: 'Caravan Supporter', proposedPriceCents: 1000,
    rewards: ['bronze-seal', 'profile-theme'] },
  { key: 'guild-patron', name: 'Guild Patron', proposedPriceCents: 2500,
    rewards: ['silver-seal', 'profile-theme', 'profile-frame'] },
  { key: 'imperial-patron', name: 'VIP — Imperial Patron', proposedPriceCents: 5000,
    rewards: ['imperial-seal', 'profile-theme', 'profile-frame', 'profile-banner'] },
  { key: 'grand-benefactor', name: 'Grand Benefactor', proposedPriceCents: 9000,
    rewards: ['sapphire-seal', 'profile-theme', 'profile-frame', 'profile-banner', 'supporter-acknowledgement'] },
].map(draft => Object.freeze({ ...draft, rewards: Object.freeze(draft.rewards),
  currency: 'EUR', enabled: false, priceConfirmed: true, rewardsConfirmed: true,
  state: 'approved-design', websiteRank: null, commands: Object.freeze([]),
})));

/** Strict draft contract, not a checkout gate or a promise of delivered rewards. */
export function validateSupporterDrafts(drafts) {
  if (!Array.isArray(drafts) || drafts.length === 0) throw new Error('Draft catalogue required');
  const keys = new Set();
  const fields = new Set(['key', 'name', 'proposedPriceCents', 'rewards', 'currency',
    'enabled', 'priceConfirmed', 'rewardsConfirmed', 'state', 'websiteRank', 'commands']);
  for (const draft of drafts) {
    if (!draft || typeof draft !== 'object' || Array.isArray(draft)
      || Object.keys(draft).some(key => !fields.has(key))) throw new Error('Unexpected draft fields');
    if (typeof draft.key !== 'string' || !/^[a-z][a-z0-9-]{2,63}$/.test(draft.key)
      || keys.has(draft.key)) throw new Error('Unique draft key required');
    keys.add(draft.key);
    if (typeof draft.name !== 'string' || !draft.name.trim() || draft.name.length > 80)
      throw new Error('Draft name required');
    if (!Number.isSafeInteger(draft.proposedPriceCents) || draft.proposedPriceCents <= 0
      || draft.proposedPriceCents > 100000 || draft.currency !== 'EUR') throw new Error('Invalid proposed price');
    if (draft.enabled !== false || draft.priceConfirmed !== true || draft.rewardsConfirmed !== true
      || draft.state !== 'approved-design') throw new Error('Approved design must remain disabled');
    if (draft.websiteRank !== null || !Array.isArray(draft.commands) || draft.commands.length)
      throw new Error('Draft cannot grant ranks or commands');
    if (!Array.isArray(draft.rewards) || !draft.rewards.length
      || new Set(draft.rewards).size !== draft.rewards.length
      || draft.rewards.some(reward => !rewardSet.has(reward))) throw new Error('Fixed cosmetic rewards only');
  }
  return true;
}
