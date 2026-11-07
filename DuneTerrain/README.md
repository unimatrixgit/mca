# DuneTerrain for Paper 1.21.8

DuneRealms terrain runs on Java 21. Build with `gradle build`; install `build/libs/DuneRealms-Terrain.jar`. Keep the existing plugin name/data directory `DuneTerrain`. Existing worlds, player data and configuration files are preserved; new terrain applies only to new chunks. Do not delete or regenerate an existing world to install this update.

## Arrakis update

- **Dune-highlands** (`dunerealms:dune-highlands`): irregular, terrain-aligned darkstone mountain regions, roughly 100 blocks across, with three sand terraces. Default terrace rise is four blocks (hard maximum five). Mountain sides rise 32 blocks before the terraces; narrow erosion ridges and jagged darkstone spires add up to 40 blocks on the outer shoulders, leaving the three upper flat terraces intact. Three additional broad sand ledges near the foot of the mountain also have four-block default steps; a steep rocky shoulder separates the lower and upper terrace groups. Centres have a 300–500-block gap to their north, south, east and west neighbours. These replace the old circular plateau formations.
- **Dense Spice** (`dunerealms:dense-spice`): one 30 × 30 rich patch per 512-block placement region, with seed-dependent positions. This is configurable village-like density, not vanilla village placement. Deep orange terracotta represents finite rich spice sand. Each authentic deposit yields four existing tagged Melange Spice items by default. Silk Touch and Fortune cannot clone or multiply it; creative mining consumes the deposit without a reward. A chunk Persistent Data Container stores each deposit's position and removes it on harvest. Ordinary orange terracotta has no spice value. Pistons and explosions cannot move or duplicate registered deposits. Ledger state persists in normal chunk saves across restarts; restoring a world backup restores its historical deposits too.
- A persistent spice seller appears in the centre of each new rich field. Right-click opens the shared native menu and offers 16 tagged spice at **standard DuneEss price × 1.35**. At 15 Solaris each, 16 cost 324 Solaris. Existing spice sales, taxes and CHOAM trading remain unchanged. Confirming purchases checks distance, inventory capacity and successful economy withdrawal.
- **Desert villages**: custom four-house sandstone settlements, with cut-sandstone lanes, villagers, beds, non-farming workstations, lanterns and finite shared supply chests. No farmland, crops or hay bales enter the structure palette. Chests use `dunerealms:chests/desert-village`: bread, dried kelp, apples and raw iron. Placement regions default to 768 blocks, with terrain suitability checks.
- **Old Harkonnen Fortress**: a custom jungle-temple-style Arrakis adaptation with sandstone walls, darkstone pillars, a stepped roof, red banner, stair and underground loot vault. It is a custom layout, not a replacement of Minecraft's built-in jungle temple structure definition. The default attempt rate is one per 1,000 Arrakis chunks, matching the Java desert-well-style chance; uneven terrain can reject an attempt. Its finite chest uses `dunerealms:chests/old-harkonnen-fortress`. When loot is generated, DuneTerrain appends a complete Harkonnen kit from the updated DuneCore kit factory: red faction stillsuit, native equipment attributes and trim, and the enchanted spice shovel. Food and raw iron also come from the loot table. Install updated Core and Terrain together; without the updated Core API, fortress rewards fall back to basic sword/food rather than fabricated faction equipment.

Sietches generate **only in the open desert**; the active population path never calls the legacy highland-site generator. The default `sietches.enabled` is true and `sietches.highlands.enabled` remains false. The 2,000-block placement grid and one personal cache per site are retained. The complete footprint is checked against rock formations, with a 512-block arrival reserve. Each intersecting chunk writes only its own slice using deterministic terrain heights, avoiding LimitedRegion clipping and neighbour edits. Two additional chambers connect to the sleeping and kitchen wings. The existing 324-source pool has an explicit stone floor, cardinal side walls and raised rim; no extra water or reward cache is added. New terrain applies only to newly generated chunks. The 7 November Arrakis reset was separately and explicitly authorized by the user, backed up and recorded in `docs/ARRAKEEN-STARPORT.md`.

Oasis trees, personal cave sietch supplies, regular suspicious-sand spice deposits and magma-marked spice blows remain enabled. Custom structures are generated only on the Arrakis world.

## Server datapack installation

`gradle build` also creates `build/distributions/DuneRealms-Dune-Highlands-Datapack.zip`, containing both custom biomes and the structure loot tables. While the server is stopped, copy this ZIP into the **primary world's** `datapacks/` directory (the world selected by `level-name` in `server.properties`), then restart before exploring new terrain. For a primary world named `world`, use `world/datapacks/`; for primary Arrakis, use `arrakis/datapacks/`. Do not replace `level.dat`, world folders or plugin data. Keep the datapack installed after generating custom-biome chunks. It uses Minecraft 1.21.8 data pack format 81.

The pack is server-side: no client mods or mandatory resource packs. Without it, the plugin safely uses Desert biome metadata and direct fallback chest supplies; terrain geometry still works. Custom-biome climate and tint translation through Geyser must be checked on a live Java/Bedrock staging network; no live client compatibility is claimed by unit tests.

For Arrakis as the primary world, use:

```yaml
worlds:
  arrakis:
    generator: DuneTerrain:arrakis
    biome-provider: DuneTerrain:arrakis
```

Set `server.planet` in `plugins/DuneTerrain/config.yml` to `arrakis`, `giedi_prime`, `kaitain`, `caladan` or `sigma_draconis_iv`. Public Velocity server names may use hyphens; these internal planet IDs keep underscores. A separate planet world created by the plugin gets its generator and biome provider directly.

Existing configurations receive the new features through code defaults. Old radius defaults 10/18 automatically upgrade to 45/55; customized radii are retained. `planets.arrakis.highlands` now controls formation enablement, density and mountain rise. The old `darkstone-islands` radius and terrace settings remain compatible, but its old `spacing` and `enabled` values no longer control highlands. Use `highlands.enabled` to disable the replacement. New density options are `dense-spice.spacing`, `desert-villages.spacing` and `harkonnen-fortress.rarity`.

DuneNPC continues to own Guild Navigators. Install updated DuneCore and DuneEss alongside Terrain for authentic fortress kits and native spice-seller purchases.

## Validation limits

Gradle tests cover irregular terrain contours, terrace steps, cardinal spacing, biome footprint alignment, 30 × 30 patch bounds, seeded rarity and deposit identity persistence/consumption. A reflection probe invokes the actual compiled terrain code to render a development preview. Compilation and mocks do not validate server startup, generated-world registry persistence, chunk event timing, structure physics or real Java/Bedrock/ViaVersion clients. Exercise these on a separate staging world before production. No existing user world was opened or changed during development.

[Actual generator render](previews/dune-highlands.png) · [Validation](../docs/Arrakis-Update-Validation.md)
