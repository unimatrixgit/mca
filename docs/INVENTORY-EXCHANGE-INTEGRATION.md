# Inventory, exchange and account integration

Prepared 27 October 2026. Imported DuneRealms-Inventory-Exchange-Account.zip (SHA256 666db85847308b5f24fb461d466df86de6d2a38b0df6d59d163573bae70b7b99), based on DuneGit main 73e1bc8b75608b77280c6d957bf590be3f3ed8b6.

## Merged behavior

- Planetary inventory ownership, dedicated PostgreSQL session locks, durable unclean-session markers, armor/offhand and XP checkpoints, safe browser projections. Lobby and ender chests remain separate.
- Core-owned exchange transactions commit inventory, escrow, wallet changes and completed sales together. SQL arithmetic preserves concurrent wallet updates. Existing durable monthly community rewards and UUID/month receipts are retained.
- CP inventory and exact History of sales UI are wired into the real account server. It validates the existing host session and resolves the current verified UUID from site_game_links. The client cannot choose a UUID. Unlink/logout and two-account isolation are covered by integration tests.
- Canonical runtime modules: website/control-panel/inventory/. Canonical UI: website/control-panel/public/inventory/. The original package API entry points re-export the canonical modules so its tests exercise production code. The asset installer reads those canonical assets.
- Current Vaerkring/Dunemarks names, planet labels, site/account theme, notes/seal, disclaimer, CAPTCHA contact, rewards and staff controls are preserved. Existing internal world/faction/model identifiers remain compatible.
- An explicitly configured but incomplete shared database cannot silently use a second local wallet.

## Validation and publication scope

Core 1.0.12 and Essentials 1.0.4 target Java 21 / Paper 1.21.8. Rebuilt JARs come from reviewed merged source, not the ZIP's precompiled plugins. The live server remains on its existing 1.21.4 compatibility binaries. No database setup, migration, inventory import or new game deployment is performed by this source integration.

Website build/tests, package API tests, merged Core/Essentials tests and a local browser review cover the integration. The browser uses clearly fictional local fixture data; it is not evidence of live inventory availability. PostgreSQL, multi-backend transfer/crash behavior and real Java/Bedrock clients still need staging acceptance.

For activation, configure DUNE_INVENTORY_DATABASE_URL with the separate column-limited reader described in control-panel-inventory/README.md. It is optional: a linked account receives an honest unavailable response while unconfigured. EXCHANGE_READ_TOKEN optionally protects the private server-to-server market bridge; the main website requires its existing EXCHANGE_LISTINGS_URL setting. No production secret is committed.

The original supplied handoff is preserved as INVENTORY-EXCHANGE-IMPORT-NOTES.md. Its missing-CP-source statements describe the original package, before this integration. Source publication is a normal DuneGit push, not a force push or an automatic live rollout.
