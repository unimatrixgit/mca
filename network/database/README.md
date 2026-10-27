# DuneRealms shared PostgreSQL setup

This is a fresh database deployment template. It does not modify a running host until you run it. Existing PostgreSQL installations should keep their current database and credentials; only install the updated plugin and additive migrations. Back up SQL and backend player data before upgrading, and stop all backends during the change.

## Fresh install

Install Docker Engine and the Compose plugin on the database host. Run:

```bash
cd network/database
./setup.sh
```

The script creates a private `.env` with two independently generated passwords and starts PostgreSQL 17. The `dune_admin` account is the container administrator. The `dune` application account can create its tables in the public schema but is not a superuser. The database volume persists across restarts. Never use `docker compose down -v` on an installation with player data. Changing `.env` passwords after initialization does not rotate existing PostgreSQL role passwords.

The listener binds to localhost by default. If Paper backends run on different machines, set `DB_BIND_IP` in `.env` to the database host's private network IP, then rerun `./setup.sh`. Permit TCP 5432 from backend private IPs only in the host/Hetzner firewall. Use the database host's private IP in each JDBC URL. Do not use a public listener. For traffic outside a trusted private network, configure PostgreSQL TLS and a trusted certificate before using `sslmode=verify-full`.

## Configure every backend before starting it

Use the same existing configuration keys in `plugins/DuneCore/config.yml` on every planetary backend. Copy the `DUNE_PASSWORD` value from the database host's `.env`; do not use the administrator password.

```yaml
database:
  enabled: true
  autosave-seconds: 60
  jdbc-url: "jdbc:postgresql://DATABASE_PRIVATE_IP:5432/dunerealms"
  username: "dune"
  password: "YOUR_GENERATED_DUNE_PASSWORD"
```

In `plugins/DuneEss/config.yml`, keep:

```yaml
database:
  use-core-database: true
```

DuneEss starts before Core and reads Core's file directly. Create/configure that file before the first network startup. Check that Essentials reports a working shared economy; configured shared mode now refuses network payments during an outage instead of falling back to a local wallet. Wallet deposits and withdrawals use atomic SQL arithmetic so they cannot overwrite concurrent exchange payments. The older `dune_players.solaris` column is legacy data; current wallets live in `dune_economy_balances`. Inventory saves do not overwrite wallets.

Core creates missing tables and adds missing player columns automatically, under a transaction-scoped schema lock. It preserves existing profile rows, inventory blobs, economy tables, and politics. No manual inventory schema import is required. Use one dedicated database for this network.

## Shared inventory behavior

- Shares main inventory, hotbar, armor, offhand, selected hotbar slot, faction, XP level/progress/total.
- Uses the existing Bukkit item serialization and retains compatibility with stored inventory blobs. Verify custom item metadata on staging with your actual plugins.
- First shared login adopts that backend's local inventory when no shared inventory exists. It does not merge items from other backend player files. Choose the authoritative first-login backend and archive other backend inventories before migration. Null legacy XP columns adopt the first upgraded backend's values.
- Saves every configured interval (minimum 10 seconds), on quit/server switch, and on orderly shutdown. Final saves close open inventories to return cursor/crafting items.
- A dedicated PostgreSQL session lock admits one backend per UUID. A fast transfer arriving before the source finishes saving is rejected; reconnect after the source quits. There is no automatic proxy retry in this change.
- A failed load cannot save local stale inventory back into SQL. An unclean session remains marked open in SQL even after its connection dies; login is blocked until an administrator reconciles it.
- SQL save failures attempt a timestamped local `plugins/DuneCore/inventory-recovery/UUID-*.yml` snapshot. These files are evidence for manual recovery, never automatically replayed. A hard process/machine crash may leave no such file.
- Autosaving restores temporarily hidden Ordos armor before capturing it; a save can shorten Shadow Step's hidden-armor period. The invisibility effect retains its existing timer.
- Ender chests, positions, beds, health, hunger, effects, advancements, statistics and arbitrary plugin player tags are not synchronized. All backends must use compatible Paper/plugin/item versions and identical authenticated player UUIDs through Velocity/Floodgate.

Do not run another inventory synchronization plugin on these same backends. This code is part of DuneCore. Only the five configured planetary identifiers opt into shared profiles; lobby and spacing_guild are excluded even if Core is installed. Keep network.local-planet correct on every backend. The lobby keeps its own inventory. Ender chests are never read, written, synchronized or exposed in account snapshots by this code; each planetary backend retains its local ender chest. Do not add an external ender chest sync plugin.

## Recovery after an unclean session

Never clear `session_open` as a routine login fix. A crash can leave the last SQL checkpoint older than item trades, drops or container changes. Automatically restoring that checkpoint would risk duplicating or losing items.

1. Keep the affected player disconnected everywhere. Stop the affected backends; for recovery during an outage, stop all planetary backends.
2. Back up SQL, backend player data and all `inventory-recovery` files.
3. Inspect the affected `dune_players` row and compare checkpoint data with local recovery/player data and any trades/container changes. Reconcile items explicitly. Recovery snapshots contain Base64 Bukkit item arrays; interpret them with this plugin on the same compatible Paper version. Do not concatenate blobs or merge inventories blindly.
4. Only after reconciling and choosing the authoritative checkpoint, use an administrator SQL session to clear the marker for that single UUID:

```sql
BEGIN;
SELECT uuid,faction,session_open FROM dune_players
WHERE uuid='REPLACE-WITH-PLAYER-UUID' FOR UPDATE;
UPDATE dune_players SET session_open=FALSE
WHERE uuid='REPLACE-WITH-PLAYER-UUID';
COMMIT;
```

5. Start the backends, reconnect the player and verify all slots, XP and wallet. Archive the incident and recovery files. Never clear markers for all players at once.

## Capacity, backups and verification

Inventory I/O follows this plugin's existing synchronous server-thread model with connection/socket timeouts. Each active planetary player holds one direct PostgreSQL connection plus short-lived economy/politics connections. Size `max_connections`, memory and backend player caps together; do not use PgBouncer transaction pooling for these session locks. Measure login and autosave tick times before increasing player limits. Java 21 and Paper 1.21.8 are the repository's pinned target, not an unverified newer release.

Example backup from this directory (shell creates the backup on the host):

```bash
umask 077
docker compose exec -T postgres pg_dump -U dune_admin -d dunerealms -Fc > dunerealms-backup.dump
```

Test restores on a separate database. Keep backups outside the Docker volume and off the host.

Staging acceptance: transfer between two distinct planetary backends with tagged spice, named/enchanted/PDC items, armor, offhand and XP; confirm different local ender contents remain different; verify exact quantities and metadata. Try duplicate connections, fast transfer, database loss during login and quit, and a forced backend crash. Confirm rejected/unclean sessions cannot overwrite SQL. Verify Java, Bedrock/Floodgate and ViaVersion clients. Production deployment is pending this live validation.
