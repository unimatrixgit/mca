# Guild lobby delivery validation — 2026-10-18

## Delivered

- Saved End world: `worlds/Dune-Guild-Lobby-1.0.0.zip`, **10,893,624 bytes**, SHA256 `6ecfef68a94a603d1f0a7a0485c3bcfd40eeb2180205347e2a2a7eb0f143dda8`.
- ZIP contains the actual `guild_lobby/level.dat`, End Anvil region/entity/data files, DuneLobby 1.0.0 companion JAR, verified dependency installer, setup instructions and per-file SHA256 manifest. It contains no server executable, player data, session lock or world UUID file.
- Source, original panels and reused project artwork are in `DuneLobby/`. Source builds embed the artwork and do not fetch remote images.
- AI design preview and an independent plan rendered from the saved chunks are in `DuneLobby/previews/`.

## Checks completed

1. Compiled with Java 21.0.12.1 and Gradle 8.14 against Paper API 1.21.4. Build passed. This module has no separate unit-test suite; validation below runs the actual server/world.
2. Started a local, loopback-only **Paper 1.21.4 build 232** server with pinned **ImageFrame 2026.1.5.0**. Generated all six rooms, block Navigator, starport arrival apron, five launch platforms, frigate, corridors and four NPCs. Installed eleven ImageFrame panels (276 map tiles).
3. Runtime flood-fill checks passed: **16,274 reachable standing cells**, clear supported central spawn, routes to every room, both smuggler counters and all five raised launch pads. World environment reported **THE_END**.
4. Stopped and restarted the server. The imported world was reused, NPCs remained and artwork restored. Runtime verification passed after restart, without duplicate frames or merchants.
5. `dunelobby build` refused an existing world folder, as intended.
6. Extracted the package into a **second fresh Paper server** with no ImageFrame data or primary-world map data. It imported the End world, recreated its own image-map registry and restored all eleven panels. Runtime verification passed there too. A subsequent restart checked the final panel-facing adjustment and passed again.
7. Independently decoded the saved Anvil/NBT data after shutdown: Minecraft data version **4189**; spawn **0,81,10**; clear headroom; **109,535 non-air blocks in build height**; six supported room centres; five marked launch pads; exactly **4 villagers** (one each liaison, official broker, water seller, spice seller); exactly **276 glowing item frames**; **no Ender Dragon**. See `previews/saved-world-plan.json`.
8. ZIP integrity check passed. All included file hashes are recorded in its `manifest.json`. Temporary validation servers were shut down after testing.

Final runtime output:

```text
GUILD_VERIFY PASS walkable=16274 artwork=276 []
```

## Practical limits

The preview is concept art. The map uses real vanilla blocks, End atmosphere and ordinary vanilla light. There was no graphical Minecraft client available for an in-game visual walkthrough; the saved-block plan was visually inspected. Paid Solaris transactions, actual player merchant clicks and remote proxy destinations were not exercised. DuneEss/DuneCore integration routes through their existing player commands; configure those optional plugins before using the official economy. Lobby merchants use emerald barter and their spice/water are separate sample goods, not faction reward currency. No live server was deployed.

The dedicated lobby has its own join/respawn routing. On an existing mixed backend, disable that routing in the companion config. Install ImageFrame separately using the pinned installer or its official distribution. Keep its data and primary-world map data with normal server backups after installation.
