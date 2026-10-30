# Native player, clan and economy GUI audit — frozen alpha

Prepared locally, not deployed. Parent owns the coordinated engine/renderer rollout.

## Verified existing paths

| Function | Actual entry/navigation | Authority and data |
| --- | --- | --- |
| Player item exchange | Core Navigator Item Exchange -> DuneEss `/exchange` | Existing escrow SQL and Core inventory checkpoint; owned stack identity/quantity rechecked before listing |
| Sell inventory items | Exchange Add Items / Sell -> selected storage slot -> quantity/price -> confirmation | Exact stack is rechecked in the same fenced transaction as listing insertion and item removal |
| Own item listings | Exchange My Listings -> current owned records -> reclaim confirmation | Actual seller UUID, current escrow quantity; repeated or stale purchases cannot reuse consumed escrow |
| Exchange transaction history | Existing website Account Transactions (`#transactions`) | Actual history module; no new in-game history query was invented during scope freeze |
| Personal bank | Core Navigator Personal Bank; `/dunebank` | Current native use node; own UUID; existing wallet/bank conservation checks |
| Faction treasury | Personal Bank -> Faction treasury; `/dunebank faction` | Current actual faction, read-only; no invented withdrawal policy |
| Clan home | Core Navigator Clan Shop; `/duneclan` | Current website-linked Minecraft UUID and clan context |
| Clan bank/history | Clan Shop -> Clan bank -> deposits / officer withdrawals / Bank history | Dunemarks only, current SQL clan membership/officer role checked during transaction |
| Clan kits/shop | Clan Shop -> Shop / Kits -> wallet or officer-funded purchase | Current immutable blueprint and kit quote rechecked; existing 15/5/80 crafting allocation unchanged |
| Clan kit preview/editor | Native kit name, colour, quality, lore summary -> Clan Center website | Existing shared visual editor on website; no duplicate new generator |
| Personal kit commerce | Core Navigator Kits Exchange; `/duneatelier` | Actual paid buyer-bound receipts; native inventory checkpoint is still the sole delivery acknowledgement |
| Personal sales chapters | Atelier -> The Loom of the Desert -> claims | Existing paid/delivered external sales and fixed five-tier rewards; treasury-funded, once per UUID/tier |
| Jobs | Core player/server jobs screens | Existing job-specific session/source generation checks retained; no edits made here |

The new shared renderer applies across these modules without changing their storage model. NativeMenus bridge contracts are unchanged.

## Concrete fixes

DuneClans1.0.4: `entryAllowed`, `guarded`, `current`, `open`, `show`, `choice`, `website`, `action`, `onDisable`. Existing menu buttons now recheck current online/player permission on selection, including after a permission revocation. Monetary actions check it again inside the authoritative transaction. Async context queries own an exact loading model; replies cannot replace a closed or different menu. Friendly home labels Shop / Kits / Clan bank replace uppercase labels.

DuneBank1.0.3: `open`, `openFaction`, `loading`, `current`, `guarded`, `choice`, `show`, `confirm`. Ledger and navigation callbacks now recheck current access too; previously they could display cached staff ledger data after the staff node was revoked. Slow reads cannot reopen closed/replaced screens. Existing fresh SQL staff authority for money movement remains unchanged.

DuneAtelier1.0.1: `entryAllowed`, `guarded`, `choice`, `loading`, `current`, `website`, `menu`, `arc`, `claimReward`. Async receipts/progress/claim completion cannot replace a different or closed menu. Buttons recheck online/view access. Queued paid deliveries retain their independent owner/redeem checks, and the background queue does not open a loading screen.

## Validation and release safety

- Clans:42 tests pass, including4 new callback/access/view ownership cases.
- Bank:18 tests pass, including3 new revocation/stale-view cases.
- Atelier:15 tests pass, including3 new revocation/stale-view cases.
- All store, fee, receipt, arc and item factory class bytes are IDENTICAL to exact current live baseline artifacts. Only each module's main/menu class and its generated nested classes plus version metadata changed.
- Exact candidate paths, hashes, baseline hashes and changed-class lists are in `output/alpha-gui-module-fixes-manifest.json`.
- No production purchases, balances, inventories, quest rewards or schema were touched. No deployment, broad commit, world change or website edit was performed.

## Limits retained honestly

Exchange history remains on the existing authenticated website Transactions page; there was no native history screen to verify. Clan kit native preview is the existing text/material summary; the full coloured mannequin preview is on the website. Do not claim an in-game custom mannequin or offline inventory editor was created by this audit. Current bridge and menu changes need the parent's actual Java/Bedrock visual/engine validation before being called live.

## Later explicit monetary exceptions (parent-authorized after scope freeze)

A new authoritative commerce classifier now distinguishes personal75/25 wallet/server, current official80/20 wallet/server, faction60/25/15 designer-personal-bank/owning-faction-bank/server, and protected-publisher server100%. Existing clan crafting15/5/80 remains unchanged. Historical receipt amounts are never recomputed. Native Python source and an atomic MySQL migration PLAN are prepared; parent owns actual SQL ALTER and deployment. Unified procedure row, not buyer payload, selects kind, office/term, owning faction and protected publisher authority. The authoritative personal-tier override bypasses only the seller quality gate; it creates no quest claims/rewards.

Atelier is now1.0.2 (16 tests) because the parent explicitly requested ArcStore to exclude nonpersonal commerce from personal sales chapters. This is the only additional store-class change since the earlier menu-only comparison. The immutable `commerce_kind` column migration must precede that JAR. See the updated GUI module manifest and `output/alpha-kit-commerce-policy-manifest.json`.

Parent ran the actual MySQL TEMP mixed-four-kind probe successfully with the initial candidate. The final probe additionally uses the exact category mapping and needs parent recopy/rerun before release. Source unit suite132 tests passes. No production monetary changes or deployment were performed by this child task.
