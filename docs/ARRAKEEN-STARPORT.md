# Arrakeen and starport: original build draft

Requested 6 November 2026: restore desert-only sietches separately, then make an Arrakeen-like city into Arrakis spawn, with a starport and DuneRealms sandstone, ochre and deep green styling. The user chose an original build after reviewing the option of licensing an external city. No external map blocks or assets are included.

`tools/build-arrakeen.py` creates an offline Sponge v2 schematic and optional projections of its actual blocks. Python 3 writes the schematic with the standard library; Pillow is only needed with `--preview`.

```powershell
python tools/build-arrakeen.py --output output/arrakeen --preview
```

The first draft covers 416 × 256 blocks, with a reserved height of 88 blocks. Ground level is local Y=3. It contains a walled town with twelve houses, a three-tier palace with connected galleries, a covered market avenue, a through-terminal, a control tower, four landing aprons and one grounded decorative frigate with an accessible interior. Gold accents use yellow terracotta rather than mineable gold blocks. It contains no entities, inventories, command blocks, water sources, rewards or executable transport commands.

The local proposed arrival position is `(294.5, 4, 120.5)`. The world installation origin is **unset**. A block-based flood fill checks two blocks of headroom and one-block steps from arrival to all twelve houses, both palace galleries, the palace floor, market, gate, terminal, four aprons and the frigate interior: 23 required destinations. This catches disconnected corridors but is not a Minecraft client movement or lighting test.

## Current verification

- The generator's 23 reachability assertions pass.
- An independent nbtlib 2.0.4 parse confirms root `Schematic`, dimensions 416/88/256, 14 palette entries, 9,371,648 block indices and no entities/block entities. DataVersion 4189 identifies the older vanilla block vocabulary used by the schematic; the live Arrakis level is DataVersion 4440.
- First artifact SHA-256: `94e09cfdce4f6765175d4ea3a2ef76f6ca715ade7f3742628fa76227df4f2c45` (34,827 compressed bytes; 569,617 non-air blocks).
- Both preview PNGs show actual block geometry using approximate vanilla colors. They are **not** Minecraft client screenshots or proof of a live installation.

## Installation remains pending

Live Arrakis is `/opt/dunerealms/servers/arrakis/arrakis`, with recorded vanilla spawn `(0,71,0)`. WorldEdit 7.3.19 is installed. Only a copy of level.dat was read; no game commands, world writes, spawn changes, plugin deployment or server restart have been performed.

Before installation: finish architectural detail and interiors, load the schematic in an isolated compatible Paper/WorldEdit world, inspect it in Minecraft, select and survey the real region, preserve a restorable world/region and spawn backup, prepare arrival/spawn protection and transport integration, then place the build in controlled batches and verify the resulting saved blocks and spawn clearance. Do not paste blindly over existing spawn or regenerate Arrakis. The full schematic includes air, so importing with `-a` would leave unwanted existing terrain inside its rooms; this needs an explicitly prepared empty/cleared footprint.

The city does not implement desert sietch generation. That separate work still needs the terrain source/runtime version mismatch reconciled (source 1.0.9 versus installed descriptor 1.0.12), desert-only enablement, extra rooms and a fully stone-contained reservoir.
