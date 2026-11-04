import {writeFile} from 'node:fs/promises';
import {supporterDrafts,validateSupporterDrafts} from './supporter-drafts.mjs';
validateSupporterDrafts(supporterDrafts);
const labels={'bronze-seal':'Bronze caravan seal','silver-seal':'Silver guild seal','imperial-seal':'Gold imperial seal','sapphire-seal':'Sapphire patron seal','profile-theme':'Decorative profile theme','profile-frame':'Decorative profile frame','profile-banner':'Decorative profile banner','supporter-acknowledgement':'Optional supporter acknowledgement'};
const escape=value=>value.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export const nativeDrafts=supporterDrafts.map((d,i)=>({key:d.key,name:d.name,priceCents:d.proposedPriceCents,enabled:false,rank:d.key==='imperial-patron'?'vip':'user',position:i,
 description:'<h2>'+escape(d.name)+'</h2><p>Approved cosmetic collection — disabled until delivery acceptance.</p><img src="/store/assets/img/supporter-hall-v1.png" alt="Imperial patron collection" style="max-width:100%;height:auto;border-radius:12px"><ul>'+d.rewards.map(r=>'<li>'+escape(labels[r])+'</li>').join('')+'</ul><p>No gameplay advantage, currency, enchantment bonus, progression shortcut, staff authority or random paid reward. Existing purchases and earned progression are unchanged.</p>'}));
if(process.argv[1]&&new URL(import.meta.url).pathname.endsWith(process.argv[1].replaceAll('\\','/').split('/').pop())) {
 if(!process.argv[2])throw Error('Output path required');
 await writeFile(process.argv[2],JSON.stringify({version:1,checkoutEnabled:false,packages:nativeDrafts},null,2));
}
