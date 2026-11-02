# Hosting catalogue — final deployed state, 2 November 2026

Explicit immediate user exception to the normal scheduled backlog. Hosting catalogue and artwork only: no infrastructure order, domain registration, real transaction, plugin installation or provisioning was performed. All45 native hosting SKUs remain disabled. Existing VIP, receipts and rank entitlements are preserved.

## Structure

Minecraft Hosting (category3) has exactly3 subcategories: Entry / Novice (5), Cloud (6), Dedicated (7). Native parent routing selects Entry by default; each subcategory displays only its own cards. Website Hosting reuses the owner's original empty category2, slug `web`. A duplicate draft category4 is archived/disabled, with no products; nothing was deleted.

- Entry: Novice Cloud, Novice Cloud +, Novice Dedicated, Novice Dedicated +.
- Cloud: Cloud Caravan — Europe / US / Asia, each with a separate + product. Light is two player-world Paper processes + lobby + proxy on one regional VM. + is the full planned world set on one larger VM, not extra VM proliferation.
- Dedicated: Caravan Outpost (25-player planning target), House Citadel (50), Imperial Realm (100), Imperial Realm Pro.
- Website: Caravan Chronicle, standard available .com and initial setup proposal. Shared PHP hosting, not a clone of the custom Node CP.

15 visible plans × monthly/quarterly/yearly =45 disabled native package records. The former9 unpurchased All Worlds draft SKUs were renamed in place to +, with before-state backup and a no-payment-item guard; no paid SKU was renamed.

## Architecture and supplier baseline (internal; hidden on public cards)

Checked official dynamically rendered EUR0%-VAT pages on2 November2026, not obsolete search snippets:

| Plan | Supplier monthly cost | Setup cost | Basis |
|---|---:|---:|---|
| Outpost / Novice Dedicated |59.00|0.00|[AX41](https://www.hetzner.com/dedicated-rootserver/ax41/), Ryzen5 3600,64GB DDR4,2×512GB NVMe |
| Citadel / Novice Dedicated + |99.00|49.00|[AX42](https://www.hetzner.com/dedicated-rootserver/ax42/), Ryzen7 PRO8700GE,64GB DDR5 ECC,2×512GB NVMe |
| Imperial Realm |259.00|129.00|[AX102](https://www.hetzner.com/dedicated-rootserver/ax102/), Ryzen9 7950X3D,128GB DDR5 ECC,2×1.92TB NVMe |
| Novice Cloud (Europe) |43.49|0|CCX13,2 dedicated vCPU,8GB,80GB NVMe,20TB transfer |
| Light Cloud EU / US / Asia |86.49 /87.99 /108.99|0|CCX23,4 dedicated vCPU,16GB,160GB NVMe; EU20TB, US/Asia2TB transfer |
| Cloud + EU / US / Asia |276.49 /279.99 /341.49|0|CCX43,16 dedicated vCPU,64GB,360GB NVMe; EU40TB, US/Asia4TB transfer |
| Website |6.48|custom below|[Webhosting L](https://www.hetzner.com/webhosting/),100GB shared PHP storage |
| Standard .com |14.50/year|—|[Domain catalogue](https://www.hetzner.com/whois/) |

Cloud source: https://www.hetzner.com/cloud/general-purpose/ . Europe is Germany/Finland, US is United States, Asia is Singapore. Supplier pages displayed availability warnings; these are planning quotes, not stock reservations. Transfer overages, tax, exact availability/configuration and future supplier prices need review before activation.

Entry uses [Multiverse](https://mvplugins.org/core/) within one Paper process plus a proxy on the same host. Cloud proposed heaps:5GB base,10GB+. Dedicated proposed heaps:8GB base,24GB+; remaining memory is not a guarantee of plugin compatibility. Light uses2×3GB world heaps+2GB lobby+0.5GB proxy within16GB, leaving OS/services headroom. Full-world sizes need load testing. Multiple worlds do not remove their chunk/entity costs or provide JVM isolation. No Multiverse plugin was installed in production for this catalogue task.

Pro prices5 separate AX42 player-world machines plus one AX41 lobby/proxy machine:6 physical servers,384GB total RAM. Current quoted world set: Arrakduin, Kaladam, Giedermark, Kaiterhof, Sigmadraken IV plus lobby. Additional worlds require a new quote. A cheaper suitable dedicated auction host was not observed (offers started74.70 versus AX41 at59); therefore Entry Dedicated uses AX41 and does not fabricate a cheaper physical-host cost.

## Final user pricing policy

Monthly hosting is supplier×1.30 (25% cost markup +5% management, additive), rounded to cents. Quarter is rounded monthly sell price×3×0.925; annual×12×0.85. Quarterly discount7.5%, yearly15%, monthly0%. Dedicated setup supplier×1.25, no management or term discount. .com annual supplier×1.25 =18.13/year, not discounted. Website setup15%×(one supplier hosting month + supplier annual domain)=(6.48+14.50)×.15=3.15, as explicitly confirmed. Domain billed annually, not on every monthly/quarterly renewal.

| Package | Monthly sell price EUR excl.VAT |
|---|---:|
| Novice Cloud / + |56.54 /112.44 |
| Novice Dedicated / + |76.70 /128.70 |
| Cloud Europe / + |112.44 /359.44 |
| Cloud US / + |114.39 /363.99 |
| Cloud Asia / + |141.69 /443.94 |
| Outpost / Citadel / Imperial |76.70 /128.70 /336.70 |
| Imperial Realm Pro |720.20 |
| Chronicle hosting |8.42 (+18.13 annual domain,3.15 once setup) |

Pro gross space before payment fees/management time:166.20/month,336.56/quarter,698.04/year; setup gross61.25. It has the highest absolute gross contribution among these quotes, so user requested Featured on the card and monthly term. Customer copy does not mention internal profitability.

## Presentation and safety

Public cards omit supplier names/links and show CPU/RAM/storage, type CLOUD or DEDICATED, region globe, proposed topology and quote. Existing4 original raster artworks are reused. Titles/specs sit in transparent artwork panels. Original SVG imperial cloth banners at upper left colour-code Entry burgundy, EU cloud sapphire, US teal, Asia plum, Outpost bronze, Citadel emerald, Imperial purple, Pro midnight and website turquoise. + uses a gilded upgrade crest; Pro uses a Featured star/cloth medallion.

Native category descriptions supply previews; scoped CSS and hosting-preview.patch suppress empty-category alerts only for these categories. Native packages stay disabled and one-off placeholder billing, without console commands, currencies or active renewal delivery. Backend integration, setup/domain line items, tax, real billing and provisioning remain out of scope. Do not enable draft records simply because quotes are visible.

Automatic iframe sizing uses an exact-origin/source-window handshake, bounded heights, ResizeObserver and image/font updates. Parent supports allowlisted category deep links. The child removes embedded min-height constraints and fixes sidebar card height stretching; a live cloud view measured body/scroll3166px within3167px iframe, scrolling=no, no clipped inner scrollbar. Untrusted origins and heights are rejected by tests.

Backups: /root/hosting-preview-before-20261102 (Blade and web entry); /home/dunerealms/.config/hosting-preview-20261102 (before.json, provider/cloud/plus/discount checkpoints and applied catalogue, private0600). Roll back only these managed categories/assets/view patch. Do not restore whole payment tables or alter existing VIP. Current generator and applied metadata let installer reject unexpected outside edits and rerun without duplicate SKUs. Source Alpha tag remains unchanged.
