# CHOAM exchange — DuneEss 1.0.2

Install `DuneEss-1.0.2.jar` with Vault and the existing DuneCore/DuneTerrain/DuneNPC suite on Paper 1.21.4 / Java 21. Replace the previous DuneEss JAR while the server is stopped. Existing wallet and listing tables are retained; startup adds nullable item-payload and display-name columns to the listing table.

## Player controls

- `/exchange` (also `/choam` or `/market`): faction-themed standard inventory menu. Actual DuneCore faction selection controls its appearance, including Ordos and unaffiliated Guild styling. Uses vanilla inventory slots; no Java-only client UI is required.
- `/exchange sell <amount> <price-each>`: reserve 1–64 held items, priced per item, maximum 1,000,000 Solaris with two decimal places. You cannot offer more than the held stack. Item names, enchantments, Dune tags and other serialized item data are preserved. Operator admin rods cannot be sold.
- `/exchange buy <id>`: review a listing, then confirm purchase of **one** item. The inventory GUI uses the same confirmation. Self-purchases are refused.
- `/exchange mine`: view your offers; select one to confirm cancellation and reclaim all unsold items. Only the seller can cancel. Your offers span all planets regardless of the browsing filter.
- `/exchange cancel <id>`: open the reclaim confirmation directly.
- `/exchange admin`: permission `dune.admin`, with all-planet listing inspection and a real connection check. Normal ownership rules still apply.

Category filters, all-items browsing, planet filters and pagination work together. The GUI identifies its own inventories through holders, cancels item transfers and drags, and prevents queued duplicate confirmations after a screen changes.

## Shared database

Enable PostgreSQL in `plugins/DuneCore/config.yml`, using the same database on every backend. Leave `database.use-core-database: true` in DuneEss. DuneEss first initializes the existing shared `dune_economy_balances` ledger, then initializes the exchange. Restart after configuration changes. The exchange refuses to trade against fallback local wallets or an unavailable shared economy. Menu/guide access still works when unconnected, and no demo items are sold in-game.

Payments, seller credit and stock changes share one SQL transaction with a locked listing row. Buying the final item deletes the row instead of attempting an invalid zero quantity. Insufficient funds, a missing seller wallet or another transaction failure rolls back the database changes. Legacy material-only listings remain readable. Unsold item payloads are stored in PostgreSQL.

**Verification limits:** automated SQL tests use H2 in PostgreSQL compatibility mode, and GUI tests use mocked Paper events. A live PostgreSQL + Paper/Velocity/Geyser staging run has not been performed. Queries are bounded and have five-second query/connect/socket timeouts but currently run on the server thread. Bukkit inventory/player-file writes and SQL commits are separate operations: abrupt process loss between them is not an exactly-once transfer guarantee. Keep normal backups and test staging before opening a public multi-backend economy. This change does not alter the wider Core profile/transfer synchronization system.

## Website

The website uses 40 clearly labelled fictional offers while the game-market gateway is unavailable. Demo offers never display purchase commands. When the configured gateway returns a valid live response, it switches to read-only live listings automatically on page load or Refresh; a valid empty live response remains an empty live market.

The current gateway expects an HTTPS read-only bridge in `EXCHANGE_LISTINGS_URL`, authenticated server-to-server with `EXCHANGE_READ_TOKEN`. These are runtime secrets, never browser code. No bridge or production database is provisioned by this change. The response is `{ "listings": [{ "id": 1, "material": "IRON_INGOT", "category": "ores", "origin": "giedi_prime", "seller": "Trader", "quantity": 16, "price": 90 }] }`. Category and planet IDs must match the website's documented filter IDs. The bridge reads `dune_exchange_listings`; the website never executes a purchase or writes to SQL.

## Checks

Run `gradle build` in DuneEss and `npm run build` / `npm test` in website. SQL coverage includes the final item, partial purchases, insufficient funds, rollback after debit, concurrent buyers, ownership, filters/pagination and validation. GUI coverage includes holder isolation, bottom-inventory transfers, drags, stale duplicate confirmation and admin permission enforcement.
