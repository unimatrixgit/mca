Imported handoff from the supplied ZIP. This records the package's original state; current integration and deployment status is in INVENTORY-EXCHANGE-INTEGRATION.md. The CP source is now available and wired locally; the package's statement that CP integration is missing is historical.

# DuneRealms — shared planetary inventory, exchange and account UI handoff

Prepared 27 October 2026. This package contains implemented source plus compiled Core/Essentials JARs. It does not deploy a running database, Minecraft server or cp.dunerealms.com.

## Repository and source state

- Repository read: https://www.dunerealms.com/git/unimatrix/MC
- Base main commit: `73e1bc8b75608b77280c6d957bf590be3f3ed8b6`.
- Local checkout: `/workspace/scratch/54b8ff792ef6/MC`.
- Changes are local, packaged in this ZIP, and have not been pushed to DuneGit.
- The separate MC-inventory-sharing repository was inspected; its main branch is an older copy of this project. This package implements inventory ownership inside DuneCore; do not install a competing inventory sync plugin.
- Source package includes plugin modules, shared GUI code, website source/assets, scripts/configurations and text documentation. Existing large generated manual/artwork exports and pre-generated world archives are omitted. Keep the current worlds and resource/data files on production; this is not a replacement server directory.

## Requirements described by the supplied package

1. Shared inventory is for the five planetary servers only: Arrakis, Giedi Prime, Kaitain, Caladan and Sigma Draconis IV.
2. Lobby inventory is separate and must never be loaded from or saved to the planetary shared inventory.
3. Ender chests are local to each world/backend. Do not synchronize, copy into shared SQL or expose them in the account inventory display.
4. Account page on cp.dunerealms.com should display saved planetary inventory using a nice Minecraft-style layout and a dummy skin wearing the saved armor.
5. Show completed exchange items under the exact label **History of sales**.
6. Finish exchange coding, package the code/JARs and provide a handoff. No production deployment or DuneGit push was performed in this session.

## Implemented

### Core 1.0.12

SharedDatabase loads inventory/hotbar/armor/offhand, faction, XP level/progress/total and selected hotbar slot. Existing serialized inventories remain readable. It never calls ender-chest accessors. Existing unused legacy SQL ender-chest columns, if any, are left untouched.

Only known planetary network.local-planet identifiers enable profile loading. Lobby and spacing_guild are excluded. Keep this setting correct on each backend; planet identifiers with hyphens are normalized to underscores.

Each online planetary player owns a dedicated PostgreSQL connection and session advisory lock. Concurrent/early transfer loads are rejected. Failed loads cannot write a stale local inventory on quit. Durable session_open markers block automatic login after a process/connection loss until an administrator reconciles the checkpoint. Controlled quit closes the open inventory, writes the full checkpoint, clears the marker, then releases the connection. Autosave defaults to 60 seconds (minimum 10). Failed SQL saves attempt a timestamped local recovery YAML snapshot.

InventoryWebSnapshot writes a safe JSONB display projection and timestamp in the same SQL checkpoint, with material/name/quantity, enchantment flag, durability and leather dye color. It contains no ender chest data or Bukkit serialization for the browser to decode.

transactNetworkInventory coordinates exchange SQL and the resulting authoritative inventory checkpoint on the same owned connection. Ordinary transaction failures restore the prior in-memory inventory. Unknown commit outcomes quarantine and disconnect the profile without an ordinary quit save over the evidence.

### Essentials 1.0.4 / exchange

Existing native Java/Bedrock menus, held-item listing, purchase confirmation, owner cancellation/reclaim, filters, pagination, inventory capacity checks and admin inspection are retained.

Actual trades require a loaded planetary Core profile. Listing escrow, buyer debit, seller credit, stock changes and the inventory checkpoint commit together. The bound ExchangeStore cannot independently commit or close Core's transaction. Delivery overflow aborts the transaction; it does not drop items into the world.

Every completed unit purchase also records dune_exchange_sales: listing ID, seller/buyer identities and names, material/display name, quantity, exact unit/total price, planet and timestamp. Failures and cancellations create no sale. Existing historic sales were not previously recorded and cannot be backfilled from missing data.

Shared economy deposits/withdrawals now use SQL arithmetic, avoiding stale read/overwrite races with exchange payments. Configured shared mode does not fall back to local cached wallets during SQL failures. This does not make all other Core/gameplay actions atomic with the economy; review those separately before public launch.

### Database deployment

network/database provides a PostgreSQL 17 Compose template, non-superuser application role and setup.sh with generated private credentials, persistent volume, localhost-default listener and health check. The guide explains private networking, backend configuration, migration, capacity, backups and manual recovery. The scripts have not been run against the user's server. Migrations are additive and preserve existing records.

### Account inventory and sales UI

control-panel-inventory contains the account UI, self-contained inventory-preview.html, safe authenticated API adapters, read-only market bridge, public asset installer and tests. The dark teal/gold layout has a local block-style dummy mannequin wearing saved armor, equipment/offhand slots, backpack/hotbar grid, item inspection, XP and a **History of sales** tab. No lobby or ender chest items appear. Item icons are stylized local vector glyphs, not exact Minecraft textures; the mannequin supports base armor shapes/colors but not exact trims, resource-pack models or elytra geometry.

Account APIs resolve the signed-in CP account's verified Minecraft UUID server-side. Visitors cannot supply another UUID. Responses are private/no-store. Queries are parameterized, bounded and read only. Sales use seller-only keyset pagination. The optional private market bridge supplies sanitized live listings to the existing website gateway with a separate server-to-server bearer token; purchases remain in-game.

## Critical remaining CP integration work

The public cp.dunerealms.com page and panel.js were inspected. They already implement verified /api/game-link associations, account sessions and rewards. The real CP server source, verified-link schema and deployment environment are NOT present in MC main, the public beta/cloudpanel branches or the older MC-inventory-sharing main copy. Personal Context did not provide another source location.

Do not claim this feature is live. Obtain the current CP source from its actual owner/workspace. Follow control-panel-inventory/README.md to wire the provided handlers into the existing server's session validator and verified-link resolver. Preserve existing account/reward/security/staff features. Install public assets using install-assets.py, grant a separate CP reader only the documented columns, deploy through the existing CP workflow, and test two-account isolation and link/unlink/logout behavior. Never replace verified linking with an editable profile name or guess a link table.

For the existing public website live exchange, mount the supplied private bridge, then configure its EXCHANGE_LISTINGS_URL and EXCHANGE_READ_TOKEN secrets. Without that runtime configuration the existing website intentionally keeps labelled demo listings. This session did not change live settings.

## Build and validation completed

Target: Java 21 / Paper 1.21.8 as pinned by the repository. No unverified change to a newer Paper API was made.

- Core: `gradle build` successful, **21 tests passed** (mocked JDBC/Paper), including failed/dirty loads, ownership, snapshots, transaction rollback and unknown commit quarantine.
- Essentials: `gradle build` successful, **21 tests passed**. Exchange SQL tests use H2 in PostgreSQL compatibility mode and cover last-item concurrency, wallet/stock rollback, ownership, sales history and rollback of externally owned transactions. This is not a live PostgreSQL test.
- Account integration: `node --test tests/*.test.mjs`, **12 tests passed**, including verified ownership, no-store, parameter rejection, outage handling, sales pagination and the private market bridge.
- Layout: JSDOM checked 41 equipment/inventory buttons, the armor mannequin, item selection, History of sales navigation and zero runtime errors with the demo fixture. A real-browser visual review was not available in this environment.
- Asset installer: tested idempotence and preservation of the original HTML backup against the observed CP page.
- Shell syntax, YAML parsing, Python compilation, JavaScript syntax and `git diff --check` passed.

## Files and installation

The `plugins/` directory in this ZIP contains the built `DuneRealms-Core.jar` and `DuneRealms-Essentials.jar`. Replace both together with all planetary servers stopped. Preserve existing worlds, player data, config files, Vault, terrain/NPC/lobby plugins and proxy settings. Do not install the planetary Core/profile stack in place of the selection lobby plugin.

Before enabling shared mode, select one authoritative first-login backend for existing inventories. A player with no SQL snapshot adopts that backend's inventory; inventories from other backend player files are not merged automatically. Back up/archive alternatives. Configure the same database on all planets before DuneEss starts; it reads Core's config from disk.

Run fresh database setup only on the intended database host. For an existing database keep its data/credentials and use the plugin's additive migrations. Configure private routing/firewall to allow only backend/CP hosts. Never expose PostgreSQL publicly, overwrite configs with defaults or clear all unclean-session markers.

## Staging acceptance before public use

Test two real planetary backends with PostgreSQL, Velocity and the same player UUIDs. Verify exact custom item metadata/quantities, armor/offhand, XP, listing escrow, purchase/reclaim, wallets, sales history, transfers and process/SQL failure paths. Verify the lobby inventory is different and unchanged; verify each planet keeps different local ender contents. Check Java, Bedrock/Floodgate and ViaVersion clients. Review synchronous SQL tick time and PostgreSQL connection capacity under expected concurrency. SQL profile locks require direct/session-compatible connections; do not use transaction-pooled PgBouncer.

Known scope limits: general non-exchange item/container mutations still depend on checkpoints and manual crash reconciliation; politics still uses its existing shared YAML snapshot and is not normalized by this change. Health/hunger/position/advancements/other plugin data are not synchronized. Fast transfers may need a reconnect while the source backend finishes its final save. Autosaving restores temporarily hidden Ordos armor, which can shorten the hidden-armor portion of Shadow Step. Per-world ender isolation assumes the intended one-planet-per-backend network; vanilla worlds within a single backend still follow that backend's existing ender-chest behavior.
