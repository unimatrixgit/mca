# Arrakeen and starport: deployed Arrakis reset

**Live 7 November 2026, ticket #270634 (internal 390).** The user explicitly instructed “reset arrakis and implement” after selecting the original build. The backend was stopped, fully archived, and its original world retained. The verified new world replaced it; the server restarted successfully with Arrakeen/starport at spawn `(0,72,0)`, WorldGuard protection and restored desert-only sietches. Account, rank and financial services were not changed. Two existing player files retain inventories/ender chests/XP/health and were relocated to the new arrival point; obsolete bed coordinates were cleared because the old world was reset.

Requested 6 November 2026: restore desert-only sietches separately, then make an Arrakeen-like city into Arrakis spawn, with a starport and DuneRealms sandstone, ochre and deep green styling. The user chose an original build after reviewing the option of licensing an external city. No external map blocks or assets are included.

`tools/build-arrakeen.py` creates an offline Sponge v2 schematic and optional projections of its actual blocks. Python 3 writes the schematic with the standard library; Pillow is only needed with `--preview`.

```powershell
python tools/build-arrakeen.py --output output/arrakeen --preview
```

The first draft covers 416 × 256 blocks, with a reserved height of 88 blocks. Ground level is local Y=3. It contains a walled town with twelve houses, a three-tier palace with connected galleries, a covered market avenue, a through-terminal, a control tower, four landing aprons and one grounded decorative frigate with an accessible interior. Gold accents use yellow terracotta rather than mineable gold blocks. It contains no entities, inventories, command blocks, water sources, rewards or executable transport commands.

The local arrival position is `(294.5,4,120.5)`. The deployed world origin is `(-294,68,-120)`, producing arrival `(0.5,72,0.5)`. A block-based flood fill checks two blocks of headroom and one-block steps from arrival to all twelve houses, both palace galleries, the palace floor, market, gate, terminal, four aprons and the frigate interior: 23 required destinations. This catches disconnected corridors but is not a Minecraft client movement or lighting test.

## Current verification

- The generator's 23 reachability assertions pass.
- An independent nbtlib 2.0.4 parse confirms root `Schematic`, dimensions 416/88/256, 14 palette entries, 9,371,648 block indices and no entities/block entities. DataVersion 4189 identifies the older vanilla block vocabulary used by the schematic; the live Arrakis level is DataVersion 4440.
- First artifact SHA-256: `94e09cfdce4f6765175d4ea3a2ef76f6ca715ade7f3742628fa76227df4f2c45` (34,827 compressed bytes; 569,617 non-air blocks).
- Both preview PNGs show actual block geometry using approximate vanilla colors. They are **not** Minecraft client screenshots or proof of a live installation.

## Deployment and recovery

Live Arrakis is `/opt/dunerealms/servers/arrakis/arrakis`. Old world UID was `245cb583-1f1b-40aa-a65b-9a7aef0928a2`; the new world UID is `c4a0d76d-ad20-4e0e-bb9d-79a0753dbb09`. The separate loopback-only Paper 1.21.8/Java 21 staging server built and saved the city, generated two complete desert sietches, loaded the existing highlands datapack, then passed a restart check. It was stopped before copying the world; that staging service is no longer running. Live proxy and authentication settings were preserved.

Backup on the planet host: `/root/dune-arrakis-reset-20261107T062125Z/arrakis-server-before-reset.tgz` (478,224,769 bytes, 1,130 archive members, SHA-256 `34fddf26ab4805dbb803e9efaf98123055d61ad3b719c885de31c89e60785d33`). It includes the entire stopped Arrakis backend, including the old world and plugin/configuration files. The untouched old world also remains under that directory as `world-original`, with targeted old terrain JAR/config copies under `rollback-assets`. Private backup/configuration files are not part of the source repository or a public release.

The city was installed with the original, console-only `DuneArrakeen` companion, fenced by the exact target world UUID and embedded plan SHA-256 `43d26625097c42d9a849832936e5e18393e9635a1b9e4f4372ebe443915a7692`. It saves progress and completion, refuses replay, and makes no automatic edits on startup. The live installation is complete. WorldGuard's native region info confirms `arrakeen`, cuboid priority 100, with build, PvP, explosions, TNT, fire spread, enderman grief and mob spawning denied. Existing administrative bypass rules are unchanged.

To roll back, stop only `dune-arrakis`, preserve the replacement world aside, restore `world-original` (or the full stopped-server archive), restore the old terrain JAR/config, move the new DuneArrakeen JAR/config and the newly created `regions.yml` aside, restore `dune:dune` ownership, then start Arrakis and verify. Do not restore global account/economy databases or touch other backends.

## Terrain compatibility and actual verification

Installed terrain descriptor was 1.0.12 while repository metadata said 1.0.9. The baseline source `SietchPopulator` was compiled, disassembled alongside the installed class, and compared with constant-pool numbering normalized: **identical bytecode**, no differences. The deployment replaced only that class and `plugin.yml` (version `1.0.12-arrakeen1`); every other installed JAR entry was retained unchanged. Do not replace that runtime with a stale full-module build merely because source metadata differs.

- Installed terrain JAR SHA-256: `f6f8547049bfd022bc68b57f37c5a259f268c2ad54a2aa08790ac3f011920bcf`.
- Installed DuneArrakeen JAR SHA-256: `4503620f3a9468d7a00d9e1dbacfed8dabb7629d32c2a9fc6ff20c950d673a38`.
- All **20** terrain JUnit tests passed, including deterministic chunk-order-independent desert generation, no neighbour writes, extra chambers, one cache and stone containment of the unchanged 324-source pool.
- Independent nbtlib/numpy parsing of the stopped world's actual Anvil region files compared **9,371,648 saved positions** against the original block plan: **zero mismatches**, 569,617 non-air blocks, spawn `(0,72,0)` and all 23 destinations safe. `tools/verify-arrakeen-world.py` reproduces that audit against a stopped world/copy.
- Runtime checks of naturally generated sietches at `(1249,492)` and `(1920,-23)` found 324 pool water blocks, zero containment gaps, a chest and both extra rooms. The first sietch also passed after staging restart and on the live backend. Highland generation is not called, and the full footprint is rejected if it intersects a rock formation.
- Independent nbtlib fixtures verified player migration preserves inventory, ender chest, XP, health, arrays and Unicode metadata, correctly changes arrival/world UUID and rejects malformed input. Original player files remain in the full backup and old world.
- Live Arrakis is active; actual console checks confirm 23 safe destinations, completed non-running installer, WorldGuard flags and the sietch reservoir. No DuneTerrain/DuneArrakeen startup exception was observed.

The build remains the original first architectural design: no functional ship travel or transport NPCs are claimed. Java/Bedrock client visual/movement testing was not available; projections and block audits are explicitly distinguished from client captures. The live world itself, safe spawn, protection and desert generator have been implemented and checked.
