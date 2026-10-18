# Spacing Guild lobby world

A separate **Minecraft Java / Paper 1.21.4 / Java 21** End-dimension lobby. The rebuilt 1.1.0 world is already built and saved in [`../worlds/Dune-Guild-Lobby-1.1.0.zip`](../worlds/Dune-Guild-Lobby-1.1.0.zip); the deterministic builder and runtime companion are here. It does not replace a planet world.

![Actual Minecraft Navigator chamber](previews/actual-1.1.0/navigator-chamber.jpg)

The image above is an actual Minecraft 1.21.4 client capture of the rebuilt world, using vanilla rendering. Version 1.1.0 replaces the simplified 1.0.0 draft. It has opaque interior walls, enclosed connecting halls and flat ceilings; no outside or sky views. The original AI overview remains in previews as a historical reference, not evidence of the delivered build. The independently decoded block plan is in previews/actual-1.1.0/.

## Rooms

| Room | Centre X/Z | Contents |
|---|---|---|
| Central spawn | 0 / 0 | Safe arrival at **0.5, 81, 10.5**, facing north; monumental amber Navigator tank on a broad stepped dais, upper galleries and Guild liaison |
| Starport / teleport | -78 / -58 | Dune II inspired square arrival apron, grounded frigate, control tower and **five raised launch pads** with striped edges, lights and destination consoles |
| Official Exchange | -66 / 40 | CHOAM counters, commodities, storage and Exchange Broker |
| News | 62 / -42 | Illustrated dispatch panel, reading benches and interactive news lecterns |
| Rules | 62 / 37 | Charter panel, benches and interactive rules lecterns |
| Smuggler exchange | -66 / 101 | Enclosed dim side passage, framed paintings, screens, brown awnings, pipes, crates and worn copper; separate **water** and **spice** seller NPCs |

All rooms connect by enclosed corridors. Launch consoles ask for confirmation. There are no Nether portals: the starport is a landing/launch design.

## Install the already built world

1. Use a **separate lobby Paper backend**. Stop that server before copying files. Leave existing planet servers and worlds in place.
2. Extract the ZIP into the server directory. It contains `guild_lobby/`, `plugins/DuneLobby-1.1.0.jar`, and these setup instructions. Do not extract over an existing `guild_lobby`; preserve it separately first.
3. Install [ImageFrame from Spigot](https://www.spigotmc.org/resources/106031/). The tested version is **2026.1.5.0**, which lists Minecraft 1.21.4 support. With Python available, `python DuneLobby/tools/install_imageframe.py /path/to/lobby-server` downloads that exact dependency and verifies its SHA256. The third-party JAR is not vendored in this repository/package. Dependency source: <https://github.com/LOOHP/ImageFrame> (GPL-3.0).
4. Start Paper. DuneLobby loads `guild_lobby` as **THE_END**, preserves the saved blocks and restores the 25 ImageFrame paintings. Artwork is included in DuneLobby's JAR, so no remote artwork hosting is required. ImageFrame map IDs are allocated for this server rather than trusting IDs from another world. Keep ImageFrame installed to render the pictures.
5. `/lobby` returns to central spawn. Console `dunelobby verify` checks spawn clearance, room/pad/vendor accessibility, NPCs and artwork. Console `dunelobby artwork` restores decorations if required; it reuses registered image maps instead of creating duplicates.

DuneLobby creates a world only when **console** runs `dunelobby build`, and refuses if a folder with that name already exists. This command is for reproducing a new world, not installing the included map. No planet file or existing lobby structure is regenerated on startup. To replace a 1.0.0 lobby, stop the server, preserve that old guild_lobby folder, and import the new ZIP; merely updating the JAR will not replace the draft geometry.

Keep Paper's default primary world as a small bootstrap world; the plugin loads the End lobby as a secondary world and routes joins/respawns there. Do not use `guild_lobby` as `level-name`, since that loads it as the primary overworld before the plugin can select THE_END. Example **lobby backend** settings:

```properties
level-name=lobby_bootstrap
level-type=minecraft:flat
generator-settings={"layers":[],"biome":"minecraft:the_void","structures":{}}
generate-structures=false
allow-nether=false
spawn-protection=0
```

For an existing multiworld server instead of a dedicated backend, set `join-at-lobby: false` and `respawn-at-lobby: false` in `plugins/DuneLobby/config.yml` to preserve other plugins' routing. The shipped ZIP is a **Paper multiworld import**, not a standalone singleplayer save or a schematic. DuneLobby supplies the void generator and prevents new End terrain around the build.

## Network and economy

- Optional DuneEss: launch consoles call the player's existing `/duneess travel <planet>`, preserving configured fares, permissions and Arrakis checks. The Navigator liaison opens `/duneess travel`.
- Without DuneEss: consoles teleport to an already loaded planet world, or request a Velocity/Bungee `Connect` using the names in the lobby config. Requests have no delivery acknowledgment and charge no fare. Configure and test the destinations on your own network; no live server deployment is performed by this package.
- The Exchange Broker opens `/dune exchange` when DuneCore is installed. Without DuneCore it explains that the official market needs that integration; no fake Solaris balances are created.
- Both smuggler sellers work without DuneCore/Vault: one drinking water potion for 4 emeralds, one orange-dye spice sample for 8 emeralds (configurable). **These are lobby barter goods**, deliberately not tagged finite Arrakis water or redeemable DuneCore spice. This avoids minting Solaris or faction rewards outside the existing economy. They are stationary, named, invulnerable villagers with custom merchant menus.
- News and rules lecterns display the text in the companion config. Run `dunelobby reload` after edits. Illustrated noticeboards are static artwork, not a live website feed.

Velocity example (adapt the backend address):

```toml
[servers]
lobby = "127.0.0.1:25570"
# Keep existing planet entries.
try = ["lobby"]

[advanced]
bungee-plugin-message-channel = true
```

Use your existing modern forwarding configuration and forwarding secret. Do not install DuneTerrain on a dedicated lobby backend, since it is intended to create a selected planet. DuneCore/DuneEss remain optional and their shared economy/database setup is unchanged.

## Protection and build

Visitors cannot break/place blocks, manipulate frames, use ordinary container blocks or damage entities in the lobby. Explosions do not damage blocks, hostile spawns are suppressed, and falling below the build returns the player to spawn. Operators have `dunelobby.build` for maintenance; other worlds are unaffected. This companion does not implement a general-purpose region protection system for other plugins' console edits.

Build the companion with `gradle -p DuneLobby build` using Java 21 and Gradle 8.14+. Typographic boards are reproducible with `python DuneLobby/tools/prepare_boards.py` (Pillow). Existing project artwork is copied unchanged from `docs/artwork`. The build embeds the local artwork; it downloads no artwork.

Design references: [1984 Navigator / heighliner production artwork](https://www.duneinfo.com/arrakis/navigators/), [Dune II Starport](https://dunerts.wiki.gg/wiki/Starport_(Dune_II)). Reference designs are interpreted in blocks; the original game sprites and film stills are not included as new assets.

## Validation

See `VALIDATION.md` for the recorded build, actual saved-world checks and limitations. The package includes `manifest.json` with file hashes. No live network, paid Solaris transfer or real player account is used in local validation.

## Rendering and compatibility

There are 25 ImageFrame paintings (1,024 map tiles), plus framed item displays and lit copper/blackstone borders. Local artwork is embedded in the companion JAR. No client mod or shader is required. The visual check used the installed Java client against a loopback-only test server. Computer control was then stopped by the user; no further app input was issued.

The companion disables the End dragon-fight controller using the tested Paper 1.21.4 ServerLevel.setDragonFight API through a narrow reflection adapter. This prevents a boss bar, dragon and exit-portal generation after a real player joins. Other Paper versions require validation; incompatible versions report an explicit error instead of silently claiming the lobby is ready.
