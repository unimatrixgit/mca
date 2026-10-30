# Website alpha handoff — 30 October 2026

## Work paused

The user requested a pause. Resume only after a new instruction. This handoff records completed changes and unfinished work; it does not declare the alpha finished. The slower/grindier kit rarity progression was explicitly deferred. Do not expand the alpha beyond its existing queue and the explicitly approved kit exceptions.

## Live website work

- Shared main menu, ordered and themed, including standalone Shop, Exchange and Kit Exchange pages. DuneGit footer links directly to `https://www.dunerealms.com/git/unimatrix/MC`.
- Staff areas: Website, Community, Minecraft and Network. Matching welcome layouts, grouped **Quick shortcuts**, and prominent authorized open-ticket queue counts. Community shortcuts exclude unrelated rank/LuckPerms tools. Existing staff layouts were preserved when Account banners changed.
- Account pages use the Personal bank-style illustrated banner, with page-specific title, icon and description. Inventory retains its Refresh control and real inventory contents.
- Public **Kit Exchange**: `https://www.dunerealms.com/kits.html`. Separate Player, Official, Faction and Server categories, search and sorting, real public catalogue only. No fabricated listings.
- CP **My Designs**: `https://cp.dunerealms.com/#kits-exchange`. Cosmetic design save, revision checks, listing controls, current native office checks and server-side ownership enforcement.
- Protected **Network Kits**: `https://cp.dunerealms.com/#server-kits`. The actual `unimatrix999` account owns the Legendary **Tavren II Aedrecht** draft. Its sale proceeds are 100% server bank. No price was invented and the draft is not published.
- `unimatrix999` has all five design rarities unlocked through `site_kit_design_unlocks`. No quest claims, rewards, balances or items were fabricated.
- Private checkout: `#kit-checkout`. The public page passes only the public listing ID; the CP reloads authoritative listing data before review/payment. Saved carts retain cart/order IDs across retry and reload. Payments use the existing atomic native checkout; no production purchase was performed for QA.
- Private kit receipt display and redemption queue. Codes belong to the purchasing verified Minecraft UUID; actual game delivery remains authoritative.
- Ban register now imports live bans on startup and every 30 seconds, rather than only after a page request. The missing latest network test ban was imported. All three actual active game bans were verified present; historical/demo rows remain distinct.
- Exchange's obsolete oversized **Interplanetary Exchange** banner was replaced with the current emerald/gold illustrated module. Real market data remains read-only; confirmation of resource trades stays in Minecraft.
- Fixed repeated web gateway interruptions: PM2 was restarting the website every 30 seconds because its 384 MiB cap was lower than normal ~560 MiB usage. Only `dunerealms-web` was raised to 1 GiB and the PM2 configuration was saved.

## Final kit policies

| Sale kind | Seller/designer | Faction bank | Server bank |
|---|---:|---:|---:|
| Regular player kit | 75%, existing seller-wallet destination | — | 25% |
| Official current-officeholder kit | 80%, existing seller-wallet destination | — | 20% |
| Faction kit | 60% to actual designer's personal bank | 25% to owning faction | 15% |
| Server kit, including Tavren II Aedrecht | 0% | 0% | 100% |

Prices are configurable; apply percentages to the actual price, using exact integer cents and conserving the full total. Never recalculate historical paid receipts. The separate existing clan-kit crafting charge/sink policy was not changed by these sale policies.

## Deployment gates and unfinished work

1. **Non-personal kit publication remains disabled** unless `website_settings['kit-policy-v2-publish-enabled']` is explicitly set to `true` after the Minecraft maintainer confirms the matching Atelier rollout on all four game servers. Draft editing is available. Do not bypass this gate just to populate the public catalogue.
2. The native maintainer reported four-kind payment engine deployment and actual MySQL atomicity/rounding/replay tests. The matching Atelier 1.0.2 fleet rollout was still pending at handoff. Confirm its final status in that maintainer's chat before enabling official/faction/server listings.
3. V2 auth-owned schema and the two `dunegameplay` definer procedures were applied. SQL source: `website/integrations/clans/kit-commerce-policy-v2.sql`, SHA-256 `a583e49fe070246209fdbafa4059f6e5dd235a359f5ff242511d79f0818680e6`. Native checks current office+term and protected authority; the browser cannot choose a beneficiary or commerce kind to bypass these checks.
4. **Server teams**: `website/node/server-teams.mjs` is an unfinished local draft. It is NOT wired, NOT deployed, NOT tested and its schemas/exports are NOT activated. Finish tests and review durable export concurrency before wiring it. Keep `game_server_admin` and `game_server_mod` out of global `/permissions/teams`; use the already deployed `/permissions/server-teams` endpoint with per-server scopes.
5. Azuriom is installed privately, but its central SSO, public storefront integration, payment-provider setup, staff sales controls, real promotion feed and separate website-perk entitlements remain unfinished. Do not present them as working or expose the private installation prematurely.
6. Staff in-game case context, inventory correction and GUI work is being handled by the Minecraft maintainer. Avoid overwriting its new `game-ticket-context.mjs` work or native plugins.
7. API colleague branch `apidev/MC`, `feature/api-v1-foundation` contains a default-off read API. Reconcile it with current source before integration. Never deploy its historical main over live work.

## Repository/source handling

The branch contains current deployed Node/CP source plus the specifically marked staged files and current public kit/Exchange/menu assets. The deployed-source manifest records exact captured hashes. It is a review/handoff branch, not a complete deployable production image: PHP integrations, generated asset bundle, native game services, credentials and database contents must be handled separately.

The existing administration token has no `write:repository` scope; do not broaden it or activate the old disabled Forgejo owner account to publish. Git handoff publication uses authorized existing server repository filesystem access, on a NEW review branch only. Main and other contributors' working trees are untouched.

## Transfer/resume instructions

1. Read this document and inspect the handoff branch diff against current upstream. Preserve concurrent work from the Minecraft/API maintainers.
2. Compare deployed source hashes before editing or replacing a module; the live site is newer than historical upstream main.
3. Verify current health, kit publication flag, native Atelier fleet versions and the four-kind procedure contract. Keep production purchases/rewards out of QA.
4. Finish scoped server-team tests/wiring and pending integration work in the existing queue. Do not implement the deferred slow-progression rebalance during this alpha.
5. Use existing secure deployment credentials locally. Never commit environment files, database dumps, admin tokens, generated account passwords or private redeem codes.
6. Run relevant tests, use isolated transaction fixtures for economy/authority checks, and collect browser proof. Deploy only the reviewed components with a rollback plan; do not rebuild/replace the full live asset bundle blindly.

## Verification at pause

Targeted suite run at handoff: studio authorization/ownership, cart retry/replay, native kit bridge access, public catalogue projection, ban feed/background sync and staff-area menu boundaries. Browser checked Account banner, real inventory, protected Legendary server-kit draft, public Kit Exchange and imported network ban. No real purchase, reward claim, unban or production delivery was performed for QA.
