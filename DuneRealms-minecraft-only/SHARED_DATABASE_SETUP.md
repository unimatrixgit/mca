# Shared PostgreSQL network

Current setup, migration, capacity and recovery instructions are maintained in [network/database/README.md](../network/database/README.md).

DuneCore 1.0.12 shares planetary inventories (armor/offhand included), faction and XP. Lobby inventory stays separate; ender chests remain local and are never synchronized or exposed in account snapshots. The same PostgreSQL database serves the existing DuneEss wallets and exchange. Direct session locks prevent concurrent backend ownership, durable unclean-session markers require explicit recovery, and exchange operations commit their inventory checkpoint together with wallets, listings and sales history.

The new inventory/exchange changes have automated validation. Live PostgreSQL, Paper, Velocity, Geyser and ViaVersion staging validation remains necessary. SQL still runs synchronously. Existing political YAML snapshots, smuggler persistence and travel/bounty limitations are outside this change; this does not claim completion of those systems.

Known pre-existing limits: political YAML writes can race; smuggler career persistence, cross-world water tagging, bulk bounty calculation and travel/refund behavior need separate review. SQL is synchronous on the game thread. Run real PostgreSQL and multi-backend client acceptance before enabling shared inventory in production.
