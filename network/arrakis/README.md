# Separate Arrakis backend — 19 October 2026

Arrakis has its own Paper directory `/opt/dunerealms/servers/arrakis`, separate from `/opt/dunerealms/lobby`. Service: `dune-arrakis`; Java 21, Paper 1.21.4 build 232, localhost TCP 25566, 256–1536 MB Java heap. The lobby remains on localhost TCP 25571 with 256–768 MB heap so both can run on the existing 4 GB VM. This is a temporary two-backend test layout, not the five-planet production deployment.

Installed Arrakis plugins: DuneCore 1.0.10, DuneTerrain 1.0.7, DuneEss 1.0.2, DuneNPC 1.0.0, Vault 2.5.0 and ViaVersion 5.12.1. No DuneLobby or ImageFrame is installed on Arrakis. No lobby world is copied into the Arrakis directory.

The primary world is named `arrakis`. `bukkit.yml` registers both generator and biome provider as `DuneTerrain:arrakis`, and DuneTerrain selects `server.planet: arrakis`. This creates actual saved Minecraft terrain. Further terrain generates normally as players explore; this is not a claim that an infinite world has been fully pregenerated.

Velocity uses modern forwarding with the existing private secret. Both backend ports stay private. Its temporary server order is `try = ["arrakis", "spacing-guild"]`, so the existing public Java/Bedrock entry leads to Arrakis first with the lobby as fallback. The website monitor has a systemd environment override mapping Arrakis to localhost:25566.

Before changes, the proxy config and lobby launch wrapper were preserved under `/opt/dunerealms/backups/20261019-arrakis/`. To return to lobby-first routing, set the proxy order to `["spacing-guild", "arrakis"]` and restart only Velocity. Preserve the separate Arrakis directory and world when switching routes.

Console input: `printf 'list\n' > /opt/dunerealms/servers/arrakis/console.fifo`. Live logs: `journalctl -f -u dune-arrakis -u dune-lobby -u dune-proxy`. Do not publish forwarding secrets or player data in Git.

Shared PostgreSQL and the live website exchange bridge remain unconfigured. The default local economy is for this single Arrakis test; do not treat it as a completed multi-server economy. The exchange refuses real trades without its shared database.

The first start successfully loaded DuneTerrain's generator and a console biome test at 0/80/0 passed for `minecraft:desert`. A subsequent bulk force-load of a 16x16-chunk area exceeded the watchdog budget. The server saved its chunks before stopping; its forced-chunk data was preserved in the backup directory and removed from active use before restarting. Avoid bulk synchronous `/forceload` pregeneration on this VM. Region headers recorded 1,721 saved chunk entries, including intermediate generation states; this is not a count of fully pregenerated playable chunks.

Recovery verification passed: the existing Arrakis world loaded in 6.5 seconds, all six plugins enabled, the desert biome test passed again, `/forceload query` reported no forced chunks, and `save-all flush` completed. Both Paper ports bind only to localhost and both returned Minecraft status responses through the monitor. Velocity restarted successfully with Arrakis first. The lobby remained active and restored its artwork. Final memory check showed approximately 560 MB available and 40 MB swap in use; no real-player Arrakis walkthrough has yet been claimed.
