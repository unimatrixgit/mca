# DuneRealms native menus and Essentials

Target: Paper 1.21.8, Java 21. No resource pack or client mod is needed.

## Plugin filenames

| Module | Published filename | Existing internal ID / data folder |
| --- | --- | --- |
| DuneCore | DuneRealms-Core.jar | DuneCore |
| DuneEss — DuneRealms Essentials | DuneRealms-Essentials.jar | DuneEss |
| DuneTerrain | DuneRealms-Terrain.jar | DuneTerrain |
| DuneNPC | DuneRealms-NPC.jar | DuneNPC |
| DuneLobby | DuneRealms-Lobby.jar | DuneLobby |

The internal IDs intentionally remain stable. Do not rename existing plugin data folders.
Stop each backend and replace its old project JARs; do not leave duplicate old/new copies.
Do not replace existing worlds, server properties, configs, balances, SQL databases or player data.
Back up the existing server and database first. The network ZIP is a **fresh-install template**;
never extract it over an existing deployment. Existing configuration files are not rewritten.

## Menus

CHOAM Exchange, DuneEss help/travel, the configurable Navigator (including its economy pages),
lobby departures and `/essentials` now use a shared renderer:

* Bedrock: Floodgate native SimpleForms, checked before Java protocol detection.
* Java 1.21.6+ (protocol 771+): native Paper dialogs, including Paper 1.21.8 clients.
* Earlier clients supported by the existing Via stack: clickable chat actions.
* Unknown Via protocol: clickable chat fallback, avoiding unsupported dialog packets.

CHOAM listings retain the SQL transactions, tagged item payloads, ownership checks,
capacity checks, purchase/reclaim confirmations and Solaris economy. Actions are owner-bound,
expire after two minutes, accept only one selection and become invalid when a new menu opens.
Bedrock responses schedule gameplay actions on the Bukkit thread. The shared layer does not
shade or duplicate Geyser, Floodgate, ViaVersion or Cumulus APIs.

The existing DuneNPC/admin-only inventory interfaces are outside this player-menu migration.
Older client login support still depends on the installed ViaBackwards/ViaRewind configuration;
ViaVersion by itself permits newer clients, not every older version.

## Crossplay package

`tools/crossplay-lock.json` pins official download URLs and SHA-256 hashes:

* Geyser 2.12.0 build 1267 (Spigot and Velocity)
* Floodgate 2.2.5 build 141 (Spigot and Velocity)
* ViaVersion 5.12.1 (universal)

Run `python3 tools/download-crossplay.py built-crossplay` to obtain and verify the JARs.
Upstream metadata was checked on 2026-10-21. These are pinned builds, not moving `latest` URLs.
Run `python3 tools/package-server.py` after the Gradle builds for a local all-JAR ZIP.
The actual supported Bedrock versions follow the selected Geyser release; staging must verify
that version against the production Bedrock clients before rollout.

For a standalone Paper server: install the Spigot variants and ViaVersion in `plugins/`.
For Velocity: install Geyser-Velocity and Floodgate-Velocity on the proxy; install
Floodgate-Spigot and ViaVersion on every Paper backend. Do not also install Geyser-Spigot
behind a proxy running Geyser. Do not install ViaVersion at both proxy and backend layers.
Retain existing ViaBackwards/ViaRewind plugins if used.

For backend forms, enable `send-floodgate-data` on the proxy and use the same Floodgate
`key.pem` on the proxy and backends. Keep that private key out of Git and ZIPs.
Set Geyser's `auth-type` to `floodgate`, retain existing UUID/link settings, and keep backend
ports protected. Do not regenerate keys, forwarding secrets or player UUID settings during an upgrade.

GitHub build.yml publishes **DuneRealms-All-Server-Jars.zip**, containing every Dune plugin,
Vault and the platform-specific crossplay plugins together, plus the lock manifest and instructions.
The network workflow puts the appropriate JARs into each server directory and includes the lobby
plugin only on the Vaerkring backend. Paper/Velocity engine executables are obtained separately;
this package contains server **plugin** JARs.

## Minimal essentials

`/duneess` and `/essentials` open **DuneRealms Essentials**. The existing `/duneess help`,
economy, trade, administration, spice sale and travel commands continue to work.

* `/sethome [name]`, `/home [name]`, `/delhome [name]`, `/homes`
* `/tpa <player>`, `/tpaccept`, `/tpdeny` (60-second consent requests)
* `/spawn` for the current world
* `/server [planet]` for the existing paid Guild server travel

Permission: `duneess.basics` (default true); default home limit: three, configurable through
`essentials.max-homes`. Homes persist atomically in `plugins/DuneEss/homes.yml`, keyed by UUID.
They are local to the backend, not synchronized across the proxy. Player requests and home/spawn
teleports remain within the current world, so they cannot bypass planet permits or fares. Use
Guild travel first, then the destination's local homes. Vanilla handles valid bed/anchor respawns;
this plugin does not overwrite existing spawn locations, beds, anchors or respawn event behavior.

HuskHomes was evaluated as a focused alternative to EssentialsX. It supports global homes and
cross-server player teleports, but also exposes a substantially broader command surface. This
change implements a small original subset inside DuneEss; it does not embed or bundle HuskHomes.
If global homes or cross-server player-to-player teleports are wanted, that requires an explicit
shared destination/arrival design rather than bypassing the existing Guild economy.

## Verification and rollout

Run Gradle 8.14 `clean build` for all five modules on Java 21. The feature branch workflows
also compile/test before creating ZIPs. Unit tests cover session ownership, expiry/invalidation,
repeated selection and home-name validation, alongside the existing trading/economy tests.

Before merging/deployment, verify on an isolated Paper 1.21.8/Velocity staging copy:

1. Java 1.21.8 dialogs, supported older Via client fallback, and current Bedrock forms.
2. Listing, filtered browsing, pagination, buy, reclaim, full-inventory rejection, offline database,
   stale confirmation and repeated click. Compare Solaris and item counts before and after.
3. Help pagination, configured Navigator actions, fares/permits, lobby departures and proxy failure.
4. Save homes, restart, reconnect, request/accept/decline/expiry, permission denial, full home limit,
   absent worlds, and vanilla death at a valid bed; test broken beds and anchors as well.
5. Compare world/config/player/database snapshots before and after; deploy only replacement JARs.

Compilation and unit tests do not establish live Bedrock rendering or proxy transfer compatibility.
