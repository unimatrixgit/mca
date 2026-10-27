# CHOAM exchange — DuneEss 1.0.4

Install matching DuneRealms-Core.jar (1.0.12) and DuneRealms-Essentials.jar (1.0.4), with Vault and existing modules on Java 21 / Paper 1.21.8. Stop all planetary servers, back up SQL/player data, replace only these plugin JARs and configure the shared PostgreSQL database on every backend before starting. Preserve all worlds/configurations. See [database setup](../network/database/README.md).

## Player controls

- `/exchange` (`/choam`, `/market`): native Java/Bedrock exchange menu.
- `/exchange sell <amount> <price-each>`: list 1–64 held items, up to 1,000,000 Solari per item, two decimal places. Items are escrowed with names, enchantments, Dune metadata and item data. Administrative items are rejected.
- `/exchange buy <id>`: inspect and confirm the purchase of one item.
- `/exchange mine`: browse your listings and reclaim unsold stock.
- `/exchange cancel <id>`: confirm cancellation of your own listing.
- `/exchange admin`: operator inspection and database check. Ownership rules still apply.

Filters, pagination, capacity checks, stale-confirmation handling and self-purchase rejection remain supported. Missing/incompatible shared Core profiles refuse to trade; lobby inventories never enter the planetary exchange.

## Transactions and history

Every actual player trade runs through Core's owned profile connection. Listing escrow, buyer debit, seller credit, stock changes, the resulting authoritative player inventory checkpoint, and any completed sale history commit together. A normal SQL failure restores the in-memory inventory and rolls back all SQL. If the commit outcome is uncertain, the profile is quarantined and ordinary quit saving is suppressed until manual recovery. Excess item delivery aborts the transaction instead of dropping items into the world.

The sales ledger `dune_exchange_sales` retains a row per completed unit purchase, including seller/buyer names and UUIDs, item/material, quantity, exact unit/total price, origin and timestamp. Cancelling or failing a trade creates no sale. Earlier sales cannot be reconstructed retroactively. Configured shared economy deposits/withdrawals now use atomic SQL arithmetic and do not fall back to stale local balances during SQL outages.

General gameplay/container changes outside these exchange transactions still rely on autosaves and unclean-session recovery. This is not an exactly-once guarantee for every Minecraft/plugin interaction. Ender chests remain local. SQL remains synchronous with bounded timeouts; profile capacity and staging tests are documented in the database guide.

## Account page and website

[control-panel-inventory](../control-panel-inventory/README.md) contains the account-page armor mannequin, saved inventory and **History of sales**, plus authenticated read-only API handlers, a private market listings bridge, integration instructions and a self-contained demo preview. The CP source is not in MC, so live wiring/deployment is a handoff task. Purchases remain in-game. Existing public website demo offers remain labelled until its operator enables the supplied live bridge.

## Validation

Run `gradle build` in Core and DuneEss and `node --test tests/*.test.mjs` in control-panel-inventory. Core tests use mocked JDBC/Paper and exchange SQL tests use H2 PostgreSQL compatibility mode, including concurrency, rollback, sales ledger and externally owned transactions. Live PostgreSQL/Paper/Velocity/Bedrock validation is pending.
