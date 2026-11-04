import { supporterDrafts, validateSupporterDrafts } from './supporter-drafts.mjs';
const labels = Object.freeze({
  'bronze-seal':'Bronze caravan profile seal', 'silver-seal':'Silver guild profile seal',
  'imperial-seal':'Gold imperial profile seal', 'sapphire-seal':'Sapphire patron profile seal',
  'profile-theme':'Decorative profile colour theme', 'profile-frame':'Decorative profile frame',
  'profile-banner':'Decorative profile banner', 'supporter-acknowledgement':'Optional supporter acknowledgement',
});
const target = document.querySelector('#catalogue');
try {
  validateSupporterDrafts(supporterDrafts);
  const cards = supporterDrafts.map(draft => {
    const card = document.createElement('article'); card.className = 'card';
    const append = (tag, text, className) => {
      const node = document.createElement(tag); node.textContent = text;
      if (className) node.className = className; card.append(node); return node;
    };
    append('p', 'Approved design', 'draft'); append('h3', draft.name);
    append('p', new Intl.NumberFormat('en-IE', {style:'currency',currency:'EUR',maximumFractionDigits:0}).format(draft.proposedPriceCents / 100), 'price');
    append('p', draft.key === 'imperial-patron' ? 'Existing VIP price · new cosmetics not yet active' : 'Approved one-off price · not yet available', 'price-note');
    const list = append('ul', '');
    for (const reward of draft.rewards) { const li = document.createElement('li'); li.textContent = labels[reward]; list.append(li); }
    append('p', 'Not available for purchase', 'unavailable'); return card;
  });
  target.replaceChildren(...cards);
} catch {
  target.textContent = 'The draft collection is unavailable for review. No purchase can be made.';
}

