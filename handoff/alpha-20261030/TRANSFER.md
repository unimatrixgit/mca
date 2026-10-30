# Alpha handoff — PAUSED, 30 October 2026

The user explicitly paused work and requested Git updates and transfer instructions. **Do not resume feature work, activate purchases, migrate servers or merge to main until the user resumes.** This is a review snapshot, not a completed alpha release.

## Read first

- Sources under the ChatGPT project `sources/` directory are read-only references.
- The shared main working tree is dirty. This handoff uses an isolated Git index/review branch; do not reset, clean, broadly stage, or overwrite the website colleague's work.
- Repository source and production artifacts are not interchangeable. The current Core artifact contains earlier method-level fixes not safely reproduced by a wholesale build of the old source tree.
- Website work has a separate review handoff from the mail/website chat. Its published pause branch is `handoff/website-alpha-20261030`, commit `06d767b8d6f099ee9b50978bb89ce78c453261e0`; read `website/docs/ALPHA-WEBSITE-HANDOFF-20261030.md` there before integrating branches. Do not replace the full live CP server, shared header, navigation or main web runtime from this branch.
- No credentials, database dumps, keys, player records or actual confiscated items belong in Git. Private server-side credential/backup paths below describe locations only.

## Actual production state at pause

| Area | Actual status |
| --- | --- |
| Four Paper backends | Still Paper **1.21.4**. Core **1.0.27**, Atelier **1.0.0**, Clans **1.0.3**, Bank **1.0.2**, DuneBan Paper **1.0.0** remain live. |
| Hosts | Lobby/proxy on 2.28.33.142; Arrakis/Kaitain/Giedi Prime on 159.69.82.29; web/MySQL on 162.55.87.104. Three planet heaps are 3GB. |
| Database migration | Gameplay/LuckPerms and per-server CoreProtect storage are already central MySQL on the web host through private tunnels. Do not rerun the old PostgreSQL import or world-regeneration installers. |
| Scoped teams | Gateway `/permissions/server-teams` is live and authenticated; 115 tests plus actual MySQL isolation proof passed. Website module/export is staged only, not wired/deployed. No QA role grants were applied. |
| Kit financial engine | `kit_commerce.py` **29395a483fdb3f6d94b36fa57587e3a1391d6e4ed8a44159cbb488dd47d960e6** is live on the gateway. 133 source tests and actual MySQL four-kind mixed-cart/rollback/replay/authority tests pass. |
| Kit schema | Immutable `commerce_kind` column and conservation/type checks were applied. A MySQL escaped-CHECK-metadata normalization issue was caught before engine activation and fixed; the migration now returns no further work. Existing receipt count was zero; no historical financial values or real balances/items changed by QA. |
| Kit authority | Two updated SQL procedures were applied, source SHA **a583e49fe070246209fdbafa4059f6e5dd235a359f5ff242511d79f0818680e6**. Execute grants restored. Catalogue/order/arc and all bank/office read routes returned 200. |
| New kit types | **Unpublished/disabled.** Do not enable nonpersonal publication until Atelier1.0.2 is live on every backend and website/native integration is verified. Tavren II Aedrecht is an unpublished Legendary draft, no invented price. |
| Casework | New canonical case-context MJS/procedure/trigger and DuneStaff native module are **not installed or wired**. |
| New GUI | New DuneMenus, Bedrock skin, Core bridge and governance screens are **not deployed**. Actual Java/Bedrock visual acceptance remains outstanding. |
| Website | Public Browse Kits, My Designs, checkout, Account hero and Exchange artwork have been updated by the website colleague. Their new server-team module remains staged. They fixed repeated web restart/502 gaps by setting only the web PM2 memory limit to 1GB. Obtain their exact current handoff. |

## User-confirmed kit policy (explicit alpha exceptions)

- Regular personal designs: **75% actual seller economy wallet /25% server bank**.
- Official current officeholder designs: **80% actual seller economy wallet /20% server bank**; current native office and exact election term required at purchase.
- Faction designs: **25% owning faction bank /60% actual designer personal bank /15% server bank**. Registered current faction leader is the publisher/beneficiary; no dummy UUIDs.
- Protected network/server design: **100% server bank**, actual protected publisher; Tavren II Aedrecht is Legendary and initially unpublished.
- Prices are configurable. Do not invent active prices or publish drafts.
- Integer-cent allocation floors faction/server shares and gives the remainder to the seller; totals conserve exact gross. Historical paid receipts retain their original allocation.
- These are cosmetic designs built on the actual buyer's native faction gear. Buying a design grants no office authority or foreign faction powers. Do not add an unrequested same-faction purchase restriction.
- Personal minimum prices/tier progression apply only to personal sales. Other kinds use current authority and configured positive price. All nonpersonal sales are excluded from personal quest progress.
- The actual unimatrix999 website account has all five designer rarity overrides, without fake quest claims/rewards. Its current verified Java link is used by the authority procedure; do not substitute a guessed Bedrock identity.
- Existing clan craft fee split remains 15% faction /5% server /80% sink. Slower/grindier progression was explicitly deferred until after the existing queue.

## Prepared candidates and tests

`release-manifest.json` records exact candidate hashes and test counts. Candidates are supplied for review/reproduction, **not automatic deployment**.

- DuneMenus1.1.0: 24 tests pass after final source changes. New native dialog overview/review, shared player/clan/economy/staff presentation, `/dunestaff`, `/navigator staff`, permitted service routing and Houses/Governance views. Bedrock titles use `DRUI|`.
- DuneStaff1.0.1: 30 tests pass. Canonical Take/creator/suspect context; exact online UUID selection; audited teleport; inventory inspection/confiscation into durable evidence; conserved wallet/treasury correction; case-authorized faction selector. No new item/money grants.
- Clans1.0.4: 42 tests; stale async views and revoked callback access fixed.
- Bank1.0.3: 18 tests; stale menus and fresh ledger/action access fixed.
- Atelier1.0.2: 16 tests; stale async views and explicit personal-only arc classification fixed.
- DuneBan Paper1.0.1: 14 tests; verified Java/Bedrock identity/name handling, no guessed offline UUIDs, empty command enters the existing staff navigator. Velocity component unchanged.
- Core faction facade: 10 isolated method tests pass. The source contains `adminAssignFaction(actor,target,faction,lockedCaseAuthority)`; donor/merge helper is in `patch-tools/faction-facade`.
- Bedrock UI: eight package/contract tests pass; minimum engine1.21.100. Emerald/gold cards reuse existing art and preserve native button indices. Other forms/item pack remain separate. No actual client screenshot/hit testing was performed.
- Canonical case API: 11 route/transaction tests; 27 actual MySQL TEMP-fixture authority checks. Real invalidation-trigger execution and cross-session revocation-lock proof were still being prepared when paused; do not assume those were completed.

## Critical Core packaging limitation

`pinned/live-Core27.jar` is the exact live baseline, SHA **1aba67f491fe470469465470b599055100f64eb5c6866cf375359663673fd327**.

The local workspace `output/alpha-final-core/DuneCore-1.0.28-alpha-final.jar` (SHA836c46ce4b35b5c320e35128570087d8a22b03de05bba0b56df07f875561a145) contains only the StaffMenuBridge route patch. It **does not contain the subsequently added faction facade**. Do not deploy it as final. An older rejected Core28 ban-menu artifact (SHA78ff...) must never be deployed either.

On resume, compile the isolated facade donor, merge ONLY its new public method and its namespaced lambda into the pinned/current Core class using `MergeFactionFacade.java`, then combine the StaffRoutePatch/helper. Never copy the donor constructor or transaction stub. Verify every unrelated Core method/entry remains unchanged and run actual staging checks. The patch tools currently preserve original workspace-relative paths; restore their workspace layout or deliberately adapt paths before running. Do not blindly run their build script from a different directory.

## Engine compatibility already proved

Isolated Paper1.21.8 build60 on actual Java21 loaded all 21 genuine production plugins plus the QA probe with no binary/enable failures. Twenty-six real faction factory items serialized by1.21.4 survived both NBT and BukkitObject decoding on1.21.8 with combat properties, appearance and identity tags intact. No production configuration, worlds, credentials or database connections were copied into those tests.

Evidence is in `evidence/engine-RESULT.md`; detailed local logs remain in workspace output/engine-validation-20261030. Staging directories are not release templates and must never be copied wholesale over production.

Java resourcepack v4 already supports formats46–64: retain SHA1 **9c7311ff078851686e0ede1252dba73b25d27c9d**, SHA256 **9df29dc6ce8f0b507d27babfec4c2762f3983585a70d074b2638bacfdc478509**. No v5 was needed. Java dialogs do not have a per-Dune-window background texture hook; do not invent one or recolor global warning controls.

## Resume order — only after user resumes

1. Reconcile this review branch and the website colleague's review branch/bundle. Preserve private configs and unrelated dirty work; review all diffs.
2. Read current on-host hashes/status. Do not replay a deployment script whose expected live hash no longer matches.
3. Finish the Core facade merge and final DuneStaff integration; rebuild/hash final candidates. Re-run actual MySQL native DuneStaff TEMP probe and the final case API/procedure checks.
4. Review/install canonical case-context SQL and trigger, then surgically wire its single exact CP route. No raw website-table SELECT grants to game writers. Native actions require current assigned/open/department-scoped case, current central role, exact actor/target/version and local permission. Bank corrections additionally require current independent bank authority.
5. Finish scoped-team website wiring/outbox acknowledgements and the remaining website storefront/SSO/perk work from the website handoff. Do not enable external-money providers without actual configuration.
6. Plan one coordinated upgrade of **all four** shared-inventory backends. Do not run a mixed1.21.4/1.21.8 writing fleet. Freeze game-state writers including direct website reward/code writes, private commerce/bank API and game sessions. Stop/checkpoint gracefully, not by forced kill.
7. Take final consistent gameplay DB plus all four worlds/playerdata/config/JAR/engine snapshots. Existing preflight DB snapshots are NOT a coordinated rollback point. Lobby world folders are `lobby_base`, `lobby_base_nether`, `lobby_base_the_end`, `guild_lobby`; planet folders match backend IDs. Do not regenerate any world.
8. Deploy only verified engine/module/pack artifacts, preserve all secrets/UUIDs and DB endpoints. Validate role/context/gameplay/inventory and actual Java+Bedrock GUI interactions, forwarding/travel and public connectivity before reopening normal writes.
9. A rollback after a1.21.8 save must restore BOTH the consistent database and every world/playerdata backup plus prior engine/JARs. Reverting only Paper is unsafe.
10. Enable nonpersonal kit publication only after all four Atelier1.0.2 copies and authority/UI checks are confirmed. No real purchase, test payout or punishment is required merely to prove routing; use isolated fixtures/test records under authorization.
11. Finalize release manifest/manual and remove exact temporary rollout SSH keys when deployment/handoff access is no longer needed. Do not claim alpha complete with outstanding steps.

## Private operational locations (no secret values)

- Gateway/new game private control source: `/opt/dunerealms/server-control`; service `dunerealms-server-control`.
- Native private DB configs: `/etc/dunerealms-gameplay-mysql.json`, `/etc/dunerealms-luckperms-mysql.json`; receipt-code key `/etc/dunerealms-kit-code.key`.
- Web root private DB metadata: `/root/dunerealms-game-databases.json`; administrator helper `/root/resolve_db_admin.py` captures CloudPanel credentials in memory, never prints them.
- Latest preflight DB backup: `/root/alpha-backups/database-preflight-20261030T002619Z` on web host. This is six database dumps, not a synchronized stopped-fleet snapshot.
- Gateway policy backup: `/root/kit-policy-backup-20261030T002726Z`; web procedure backup `/root/alpha-backups/kit-procedures-20261030T003326Z`.
- Scoped-role gateway backup: `/root/server-teams-backup-20261030T000231Z`.
- Temporary rollout identity was `.tools/rollout-transfer-key`; its final cleanup state is recorded separately in the Git-publication handoff. Do not publish private keys or weaken SSH/MySQL privileges to regain access.

## Git publication and access cleanup

This snapshot is published to review branch `handoff/gameplay-alpha-20261030`. Main is unchanged. The matching website review branch is `handoff/website-alpha-20261030`; both must be reviewed, not blindly overwritten. A local delta bundle is retained in workspace output for recovery and requires the existing main/base history. Temporary rollout SSH authorization is revoked as the final publication step; do not rely on that private key for a resumed task. Use approved existing access, never credentials from Git. The final chat confirms the actual cleanup outcome.

