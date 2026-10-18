# Guild lobby 1.1.0 validation — 2026-10-18

This rebuild supersedes the simplified 1.0.0 draft. The current package is `worlds/Dune-Guild-Lobby-1.1.0.zip` (21,383,329 bytes), SHA256 `f66c0bc3192a1b1010b28e7123af60133b7ec32a2eb5fc62ca88e6d36a2bc5cf`.

## Changes and visual evidence

- Monumental Navigator tank, broad stepped dais, gold floor medallion, upper galleries, carved blackstone columns, short diagonal connections and furnished rooms replace the sparse grid layout.
- All connecting halls and room boundaries are opaque interiors with flat ceilings. There are no exterior/sky views. The outer enclosure is opaque too.
- Twenty-five ImageFrame paintings use 1,024 map tiles; fifteen separate item displays decorate the official market. Water and spice sellers remain separate NPCs in the dim, screened smuggler market.
- `previews/actual-1.1.0/navigator-chamber.jpg` is an actual installed Minecraft Java 1.21.4 client window capture, using vanilla rendering. It was displayed to the user before their approval. It is not an AI rendering. The historical concept remains separately labelled.
- The real client joined the local loopback test server. An End dragon-controller issue discovered during that check was corrected: the companion disables the controller through the tested Paper 1.21.4 adapter, preventing the boss bar, dragon and vanilla exit portal from appearing.
- Computer control was stopped by the user after the visual check. No further app input was issued. The previously captured game image was exported as an artifact through file operations.

## Checks completed

1. Java 21 / Gradle 8.14 build passed against Paper API 1.21.4. There is no separate unit-test suite; these checks run the real server and decode the saved world.
2. Built a fresh world on Paper 1.21.4 build 232 with ImageFrame 2026.1.5.0. The builder performed 565,328 block placements.
3. Restarted with the final companion, restored all 25 image maps and verified the saved world without regenerating its blocks.
4. Runtime flood-fill and environment checks passed: six rooms, both seller approaches, five launch pads, four named NPCs, 1,024 artwork frames, clear supported spawn, opaque flat ceiling/enclosure and no active dragon battle controller.
5. After clean shutdown, an independent Anvil/NBT reader confirmed data version 4189, spawn 0/81/10, 386,882 non-air blocks in the inspected height range (including enclosure), four villagers, 1,024 glowing painting frames, fifteen item frames, six text labels, and no Ender Dragon. It checked the flat ceiling and outer walls directly from saved blocks. Results and an actual-block plan are in `previews/actual-1.1.0/`.
6. ZIP integrity check passed. The package includes a per-file SHA256 manifest, the actual saved End chunks, companion JAR and setup/dependency installer. Locks, UUID identity files, player data and the server executable are excluded. Local test servers were shut down.

Final runtime output:

```text
GUILD_VERIFY PASS walkable=13791 artwork=1024 []
```

## Limits

The screenshot verifies the Navigator interior; it is not a claim of pixel-for-pixel equivalence to the earlier AI art. Other room geometry, paint placement and connectivity were checked from the saved world and running server. Merchant clicks, paid Solaris transfers, remote proxy destinations and production concurrency were not exercised. No live server was deployed. The smuggler merchants use emerald barter and sample goods, separate from redeemable faction rewards. Paper versions other than the tested 1.21.4 require validation because the dragon-controller adapter is version-specific. Install the new world folder to replace a 1.0.0 draft; updating only the JAR deliberately leaves existing blocks intact.
