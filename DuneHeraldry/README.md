# Faction banner staff kit

Five genuine placeable banner patterns: Atreides (red hawk on green), Corrino
(crowned gold rampant lion on black with crimson trim), Harkonnen (red bull),
Fremen (cyan water emblem), and Ordos (green serpent). Heraldry extends the
existing DuneRealms faction icon vocabulary; these are decorative building items,
not ranks, awards, weapons, paid kits, or WorldGuard bypasses.

- `/staffkit banners` — receive eight banners for each of the five factions.
- `/staffkit banners corrino` — receive eight of one faction. All five canonical
  faction names are supported, with tab completion.
- `staffkit give <exact-online-player> <faction|all>` — console-only delivery.
- `staffkit verify` — console-only registry, native item persistence and inventory
  capacity checks; it issues no items and edits no world blocks.
- Permission: `dune.staffkit.banners`, default OP. Give this specific node to
  authorised staff/builders, never to ordinary player groups. The live node is
  assigned to existing `dr_access_staff`, `dr_access_corestaff`, `dr_access_owners`
  and `wrole_game_builder` groups through native LuckPerms commands. Existing unrelated
  permissions, political offices and faction membership are unaffected.

The full delivery is checked against storage capacity before giving anything.
Nothing is overwritten, dropped as overflow, or automatically issued on login.
Normal placement, breaking and item persistence use Minecraft's banner system.
The custom patterns deliberately have no public loom recipe.

## Install / rebuild

1. Build `DuneHeraldry` with Java 21 / Paper 1.21.8 API.
2. Install `worlds/faction-heraldry-datapack` as a ZIP with `pack.mcmeta` at its root
   into each backend's primary world's `datapacks` directory. Dynamic registries
   then apply to that backend's other dimensions too.
3. Install the plugin JAR and restart that backend normally.
4. Serve the updated Java resource pack and set its exact SHA1. Accept the updated
   pack on the client to see the custom heraldry. Vanilla banner textures remain
   untouched. Bedrock/Geyser does not render these custom Java banner patterns;
   it may show the underlying black banner. No Bedrock visual parity is claimed.

`python tools/build-faction-banners.py --output OUTPUT --base-pack BASE_V7.zip`
generates original pixel assets, banner/shield atlas entries, a registry datapack,
an additive v9 bundle and a preview. Requires Pillow. Native banner front and
mirrored rear UV rectangles are both verified against the generated cloth.
The pinned v7 base SHA1 is
`6222875ddd969bebbeefc5b607051fdf61b1a14e`. Every unrelated base asset is verified
byte-identical; existing language and atlas entries are merged. The published v9
SHA1 is `7a73dc20b7894555dd45af66c176fc90de899862`. Earlier immutable v7/v8 downloads
are retained separately.

Native isolated loopback QA (`qa/HeraldryProbe.java`, not installed live) verifies
all five placed wall banners and their drops, item serialization and a saved-world
restart. Plugin verification additionally covers full, empty, insufficient and
partially stackable inventories without mutating their contents. The artwork
preview shows actual texture pixels, not a gameplay screenshot. No live-client
visual inspection is claimed.

For rollback, retain the banner registry datapack once any custom banners have
been used. Removing registered patterns from an existing world can invalidate
stored items/block entities. The plugin itself can be removed independently.
