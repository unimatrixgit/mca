# Account inventory and History of sales

Integrated into the existing account control panel source at website/control-panel/. The server now uses its real session validator and verified site_game_links association. Runtime activation still needs the private PostgreSQL reader configuration and a reviewed deployment. This folder retains the original package's adapter tests, installer and standalone design preview; the final section describes the completed source integration.

## Layout

Open `inventory-preview.html` for a self-contained layout preview. It contains explicitly labelled fictional data. Production assets never load demo fixtures.

The component shows a blocky dummy Minecraft skin wearing the saved helmet, chestplate, leggings and boots; leather armor uses its actual saved dye color. Armor, offhand, backpack, hotbar and XP are readable, and clicking an item shows its name, quantity, enchantment flag and durability. Item glyphs are local stylized drawings, with material names for identification; they are not exact Mojang item textures. No player skin service or external CDN is used. Custom armor trims/elytra geometry are not rendered by the simplified mannequin.

The second tab is exactly **History of sales**. It shows completed exchange sales with item, quantity, buyer, planet, payment and date. It reads an immutable SQL sales ledger and supports older-page cursors. No pre-update sales can be reconstructed if the old exchange never recorded them. Failed, cancelled and unsold listings are not completed sales.

Lobby inventories stay separate. Ender chests are world/backend local and are neither synchronized nor displayed. Account inventory is read only and shows the last saved checkpoint, not a live item-transfer interface.

## Integrate with the real CP server

1. Obtain the existing CP source checkout. Keep its current HTTPS/session/CSRF/account-link implementation. Install this package's `pg` dependency into that server (`pg` 8.16.3 is declared in package.json).
2. Create a small PostgreSQL pool on the CP server, with a private `DUNE_INVENTORY_DATABASE_URL`, max 3 connections, `connectionTimeoutMillis: 5000` and `idleTimeoutMillis: 30000`. Do not expose the connection string in HTML or JavaScript sent to browsers. It must connect to the same network database as Core and Essentials.
3. Import `createInventoryHandler` and `createSalesHistoryHandler` from `inventory-api.mjs`. Construct both with `{authenticate, verifiedMinecraftUuid, pool}`. `authenticate(request)` must validate the CP's existing server-side session and return its account object with `id`, or null. `verifiedMinecraftUuid(accountId)` must query that account's current, verified `/api/game-link` association on the server, or return null. Use the exact existing CP link schema. Never use a submitted username, UUID, editable profile field or public Mojang lookup as proof of ownership.
4. Mount the two handlers on GET `/api/inventory` and GET `/api/exchange/sales-history` in the existing request router. They consume Web `Request` objects and return Web `Response` objects, or null for unrelated paths. Preserve cookies and return their status and headers. They reject writes, foreign-UUID parameters and unsupported snapshot versions, bind SQL parameters, and return private `no-store` results. Do not add public CORS or caching.
5. Run `python3 install-assets.py /absolute/path/to/cp/public`. It copies the component and adds its stylesheet/module references to the existing account HTML, preserving a source backup. The mounting module observes the current dashboard sign-in visibility and verified identity display, clears private data on sign-out/link changes and leaves rewards/security/staff features intact. No edits to panel.js are required.
6. Deploy that reviewed CP checkout through its existing hosting workflow. Verify sign-in, link/unlink, inventory, sales, logout, and account A/B isolation in the browser. The prepared assets do not go live by simply adding this folder to MC.

## Minimum database grants

After Core and Essentials have created their tables, create a separate non-superuser reader role in an administrator psql session. Generate the password privately, then run:

```sql
\prompt -s 'New control-panel reader password: ' cp_password
CREATE ROLE dune_cp LOGIN PASSWORD :'cp_password';
GRANT CONNECT ON DATABASE dunerealms TO dune_cp;
GRANT USAGE ON SCHEMA public TO dune_cp;
GRANT SELECT(uuid,inventory_view,inventory_updated_at,session_open) ON dune_players TO dune_cp;
GRANT SELECT(id,listing_id,seller,material,display_name,quantity,unit_price,total_price,origin,buyer_name,sold_at) ON dune_exchange_sales TO dune_cp;
```

This reader cannot read raw Bukkit inventory blobs, wallet tables, modify inventories or perform purchases. The authenticated application still has to scope each query to the verified account UUID; grants alone do not enforce per-account visibility.

## Checks

`node --test tests/*.test.mjs` exercises authentication, verified-link ownership, private caching, input rejection, SQL outages, empty snapshots and sales pagination. Live CP integration is pending access to its source and deployment environment.

## Existing public website market feed

`market-bridge.mjs` completes the missing read-only server-to-server listings bridge for `website/worker/exchange-api.mjs`. Mount its `createMarketBridge({pool,readToken})` result at `/internal/exchange/listings` on the CP server. Generate a separate private token (at least 32 characters), set that same secret as the website's `EXCHANGE_READ_TOKEN`, and set its `EXCHANGE_LISTINGS_URL` to `https://cp.dunerealms.com/internal/exchange/listings`. Keep the token out of browser code. The bridge exposes only the latest 500 public offer fields, never serialized item payloads, UUIDs, wallets or account inventories. Purchases remain in-game with confirmation.

Add the following grant for the CP reader if using this bridge:

```sql
GRANT SELECT(id,material,category,origin,seller_name,quantity,price) ON dune_exchange_listings TO dune_cp;
```

## Integrated account inventory (27 October 2026)

The supplied Inventory-Exchange-Account ZIP is merged into the current source, including verified account inventory and History of sales routes in website/control-panel/server.mjs. Runtime adapters are in website/control-panel/inventory/, and themed assets are in website/control-panel/public/inventory/. This package's API entry files re-export those canonical runtime adapters; its tests exercise the same implementation. install-assets.py now reads the canonical production asset directory.

The existing site_sessions and site_game_links tables provide authentication and verified ownership. DUNE_INVENTORY_DATABASE_URL is optional and must be a private PostgreSQL reader connection with the column grants above. Without it, linked players receive an honest unavailable state; no demo inventory is returned. EXCHANGE_READ_TOKEN optionally enables the private listings bridge. Neither database credentials nor a live market feed are activated by committing this source. Existing rewards, security and staff controls are preserved.

The original standalone preview is an explicitly fictional layout reference. Production currency and planet labels use the current Dutch-inspired names and Dunemarks. In-game code targets Paper 1.21.8; the currently deployed 1.21.4 binaries are not replaced by this integration.
