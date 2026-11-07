# Grand Atreides Arrakeen

**Live7 November2026, ticket938393 (native392).** The user requested a real-block rendering from DuneGit's basic spawn template, then a grander palace, a bigger city, the Atreides logo and explicitly: “place it ingame”. The enlarged design replaces the spawn area in the **same** Arrakis world. No world reset was performed.

## Build and editable artifacts

- Walled city418×336 blocks,2.77times the original226×224 footprint;28 houses instead of12.
- Palace225×157 blocks,6.18times the original65×88 footprint, with three tiers, large dome, four minarets, audience hall, terraces and two reachable galleries.
- Red concrete Atreides hawk mosaics on the palace and eastern gate follow the existing `website/web/assets/faction-atreides.svg` polygon design.
- Developed market arcades, roof pavilions, four landing aprons, hangars, control tower and decorative accessible frigate. No functional ship transport is implied.

The full model is608×144×384, with20 ordinary full-cube materials and no entities, command blocks, rewards or inventories. World origin is`(-486,68,-184)`; local arrival`(486.5,4,184.5)` remains world`(0.5,72,0.5)`. City floor blocks areY71; standing height isY72. Gallery standing heights areY111 andY139. Highest palace finial isY197.

Editable schematic and textured real-block renders are in`designs/arrakeen-grand/`. These are software projections of actual exposed voxel faces with textures read from the user's existing Minecraft1.21.8 client, not AI paintings or Minecraft client screenshots. No game JAR or separate extracted texture pack is redistributed.

```powershell
python designs/arrakeen-grand/build-grand-design.py --no-render --output output/grand-arrakeen
python designs/arrakeen-grand/build-grand-design.py --client-jar "path/to/your/Minecraft1.21.8-client.jar" --output output/grand-arrakeen
```

The generator uses numpy and Pillow. Its frozen compressed baseline is the prior original block proposal; the base geometry utilities remain in`tools/build-arrakeen.py`. Rendering uses the companion`block-renderer.py`. A clean reproduction produced the identical runtime plan hash below. The vanilla block vocabulary remains compatible with the live1.21.8 server.

## Installer and verification

`DuneArrakeenGrand` is a separate companion, preserving the original installer/sietch audit tool. It makes no automatic block edits. The console-only command requires exact Arrakis worldUUID and runtime resourceSHA, works in bounded tick batches, saves checkpoints and refuses replay once complete.42 embedded safe destinations cover spawn, palace, galleries, all28 houses, promenades, gate, market, terminal, ship and aprons.

The completed revision restores only the verified ground-level spawn metadata after terrain initialization, fenced by the stored worldUUID and checks for solid footing/two air blocks. This avoids the terrain startup selecting the terminal canopy roof as spawn. It never replays construction on startup.

```text
arrakeengrand status
arrakeengrand verify
arrakeengrand install <exact UUID> <exact resource SHA256>
```

WorldUUID remains`c4a0d76d-ad20-4e0e-bb9d-79a0753dbb09`.

- Runtime planSHA256:`fa04fb9980e7f01fb9d426c76b3c9fc1ebec1a53092fa07d327ec19c37e27f2e`.
- Editable schematicSHA256:`9bb3a5cf6f28fe8ba6b3ec5934a1e379ce669eb778928cb27f891fe79a4e4524`.
- Live companion1.0.1SHA256:`b2a1b2d4db14d9e6122bd832bdbc152926de6386e51328b9ebddd88ab70f6d8c`.
- Existing terrain JAR retained byte-for-byte:`f6f8547049bfd022bc68b57f37c5a259f268c2ad54a2aa08790ac3f011920bcf`.

Actual checks: Java21 compilation;42 block-based route checks; wrongUUID and wronghash refusal; complete-install replay refusal; independent stopped Anvil audit of **33,619,968 positions, zero mismatches**,1,434,573 non-air blocks and42 safe destinations. `tools/verify-grand-arrakeen-world.py` performs that independent audit with nbtlib2.0.4/numpy. Live restart confirms the ground spawn anchor, completed/non-running installer,42 safe targets, expanded native WorldGuard region and the original sietch at1249,492 with324 water blocks/zero containment gaps/chest/extra rooms. No gameplay client visual or movement test is claimed.

## Deployment and rollback

The live backend was stopped and fully archived before changes. A loopback-only Paper1.21.8 staging process used a clone of the current world with the sameUUID and only the terrain/grand installer plugins. All playerdata, advancements and stats files matched the stopped original before placement. Two player positions inside changed geometry were moved safely to arrival, preserving all unrelated raw NBT, inventories, ender chests, XP, beds and other metadata. Players outside the changed area retain their coordinates. Accounts, ranks, global economy and other backends were untouched.

Private backup on the planet host:`/root/dune-arrakis-grand-20261107T190110Z/arrakis-server-before-grand.tgz`,468,619,496bytes,1128 entries,SHA256`eb86c58c4d29bfbab5700af5c5967c29ba7f795c372d2992b6a3d474e0c2e1d0`. The original world also remains beside it as`world-before-grand`; original WorldGuard bounds are in`regions-before-grand.yml`. Never publish the full server archive or private configurations.

Only existing region`arrakeen` bounds changed to`(-486,-64,-184)` through`(121,319,199)`. Priority100, existing build/PVP/mob/explosion/fire protections, owners and members remain unchanged. Only Arrakis was restarted. The old sietches and external world terrain remain; this was an enlargement of the capital, not a seed/world reset.

For rollback, stop Arrakis, retain its latest world separately, restore`world-before-grand` and the saved region file, move the new Grand JAR/config aside, restore dune:dune ownership and restart. Preserve any subsequent user edits for review before restoring an older world. Do not restore account/economy databases or other backends. The original DuneArrakeen tool remains installed, but its first-draft gallery targets are superseded; use`arrakeengrand verify` for the current city and the original`arrakeen sietch` command for the unchanged sietch audit.
