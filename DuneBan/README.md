# DuneBan: explicit local and network moderation

Paper 1.21.4 / Java 21 command plugin and a Velocity 4.3.1 / Java 25 network guard. This is a separate plugin; it does not replace Core, Essentials, inventories, wallets or worlds.

Commands from an authenticated in-game staff player:

```
/duneban local <user> <reason>
/duneban global <user> <reason>
/duneunban local <user> <created-ISO> <reason>
/duneunban global <user> <created-ISO> <reason>
```

The new command name is deliberate: vanilla `/ban` is not silently redefined. Ban commands create permanent bans; the staff GUI can pass the existing 15-minute, one-hour, 24-hour, seven-day or permanent choices. Unban requires the reviewed original creation timestamp. Reasons must be single-line 5–500 characters, with no control characters or legacy formatting codes. Invalid or unavailable operations report no confirmed success.

Separate permissions `dune.ban.local`, `dune.ban.global`, `dune.unban.local`, `dune.unban.global` default to operators. A global grant does not implicitly confer local rights, or vice versa. Self, operator and protected targets are rejected. Protection recognizes `dune.ban.protected` and existing `dune.moderation.protected`. Online UUIDs are authoritative; offline Java names use the local verified name cache or the exact official Mojang name lookup. Offline target protection requires a successful LuckPerms user lookup. No offline-mode UUID is guessed. Select an online Bedrock player through the GUI rather than fabricating a Java identity.

Local bans use Paper's profile-ban API, verify the native ban before disconnecting, and remain local. Global bans are immutable historical records in central **MySQL** `dunegameplay`, with one active row per player. InnoDB player lock rows and a unique generated active-player index serialize changes. Global unban compares the exact original creation snapshot under the same row lock and retains the revoked row with actor/reason/time. Prepared statements bind all player and staff input. Shared database credentials are never embedded in JARs.

The backend and proxy check global bans on login. Velocity also checks each server transfer. Both check currently connected UUIDs at a bounded two-second interval, querying only the connected UUIDs; these checks do not poll ranks. Newly globally banned accounts disconnect without waiting for another login. A database/configuration failure rejects login/transfer or disconnects active sessions with a clear moderation-service-unavailable message, rather than inventing an account ban. Polling plus database timeout/scheduler delays bounds responsiveness; it is not a claim of instantaneous propagation.

## Private configuration and deployment

Install `DuneBan-Paper-1.0.0.jar` on the lobby and all planet backends. `plugins/DuneBan/config.yml` must set exact `server-id` (`lobby`, `arrakis`, `kaitain`, `giedi_prime`). Blank database settings inherit the existing DuneCore `database.jdbc-url`, `username`, `password`; the lobby, which does not run Core, needs those private settings explicitly.

Current topology uses an authenticated private SSH forward on **each game/proxy host**: `127.0.0.1:13307` → central website MySQL. Use a private JDBC URL such as `jdbc:mysql://127.0.0.1:13307/dunegameplay?allowPublicKeyRetrieval=true&connectionTimeZone=UTC` and the provisioned account. The public-key exchange remains inside the authenticated SSH transport; do not expose MySQL or downgrade its account authentication.

Apply `schema.sql` to the central gameplay schema before enabling the guard. The Paper writer also initializes missing tables, but the proxy guard intentionally does not need DDL rights. Its account can be SELECT-only on `dune_network_bans`. Paper needs read/write on the ban and lock tables plus initial schema setup if schema was not preprovisioned.

Install the network guard JAR in Velocity `plugins/`. Private `plugins/duneban-network/database.properties` contains:

```properties
jdbc-url=jdbc:mysql://127.0.0.1:13307/dunegameplay?allowPublicKeyRetrieval=true&connectionTimeZone=UTC
username=PRIVATE_ACCOUNT
password=PRIVATE_VALUE
```

Provision this file **before** loading the guard: missing credentials intentionally fail closed. Connector/J 26.10.0 and its protobuf runtime dependency are packaged. No Velocity server classes are packaged.

`output/network-ban-20261029/build-guard.sh /exact/velocity.jar [private/database.properties]` compiles the guard against the exact deployed Java-25 Velocity API using JDK25 and `--release 21`. It runs isolated real Velocity event API tests. The optional second argument runs the actual MySQL store smoke using **connection-local TEMPORARY tables only**; no record is visible to the live network guard, and no real player is punished. The test account needs CREATE TEMPORARY TABLES for this separate verification.

## Staff GUI API

Plugin name `DuneBan`; public asynchronous methods:

- `banScoped(Player, UUID, String reason, Duration durationOrNull, String scope)` → `CompletableFuture<Result>`.
- `unbanScoped(Player, UUID, String expectedCreatedISO, String reason, String scope)` → `CompletableFuture<Result>`.
- `listBans(Player, String scope)` → `CompletableFuture<List<BanView>>`.

Scope is exactly `local` or `global`. `Result.ok()`/`message()` carry confirmed outcome; `BanView` exposes `uuid`, `name`, `reason`, `created`, `expires`, `source`. Ban writes and view results recheck the authenticated current player/permission. Database and remote name operations run away from the Paper thread; Paper profile actions dispatch on its thread. Reflection adapters should keep loading-view UUID/permission/current-view checks and never turn an unavailable future into success.

## osTicket appeals and server-control integration

The old gateway agent reads global MySQL bans as `network:<player UUID>` with scope `network`, original UTC millisecond creation snapshot and expiry. Local planet sources remain independent. Private `/bans/action` accepts `remove` only, compares the exact snapshot under the same InnoDB lock, commits retained revocation metadata and verifies that no active replacement exists before returning matching-key `confirmed=true, absent=true`. A stale snapshot, outage or mismatched response keeps the appeal pending.

Private Python config `/etc/dunerealms-gameplay-mysql.json` uses host `127.0.0.1`, port `13307`, database `dunegameplay`, username and password. The agent uses PyMySQL; credentials and arbitrary hosts cannot arrive from a request. Global feed `recordId` is the same UUID shown in the login-ban reference and is preserved as the CP ban/appeal ID; it is not replaced with an unrelated ID. The CP feed validator permits only explicit `arrakis`, `lobby`, `kaitain`, `giedi_prime`, `network` scopes and verifies all rows before touching history.

## Verification limits

Eleven Java unit tests cover scoped policy, reason/username/duration bounds, target protection, current permissions, native ban confirmation before kick, local/global separation, exact unban snapshots and outage handling. Server-control tests cover transactional network removal, stale/replaced/absent records, parameter binding and matching feed timestamps. Velocity event smoke and live JDBC temporary-table smoke are separate deployment checks. None of these represent a real Minecraft client session, or permission to create a test punishment for a real player.

