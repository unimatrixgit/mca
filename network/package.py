#!/usr/bin/env python3
"""Assemble six configured Paper backends and one Velocity proxy from built plugin jars."""
from pathlib import Path
import os, shutil, zipfile, secrets
ROOT=Path("DuneRealms-Velocity-Network")
PLANETS={"arrakis":25566,"giedi-prime":25567,"kaitain":25568,"caladan":25569,"sigma-draconis-iv":25570,"spacing-guild":25571}
IDS={"arrakis":"arrakis","giedi-prime":"giedi_prime","kaitain":"kaitain","caladan":"caladan","sigma-draconis-iv":"sigma_draconis_iv","spacing-guild":"spacing_guild"}
def copy_jar(source, destination):
    with zipfile.ZipFile(source) as archive:
        if archive.testzip() is not None:
            raise ValueError(f"Corrupt JAR: {source}")
    shutil.copy2(source, destination)

if ROOT.exists(): shutil.rmtree(ROOT)
secret=secrets.token_hex(32)
(ROOT/"proxy"/"plugins").mkdir(parents=True)
if not Path("built-crossplay/universal/Vault-2.5.0.jar").is_file():
    raise RuntimeError("Vault is required on every backend")
shutil.copy2("built-crossplay/Vault-LICENSE.txt", ROOT/"Vault-LICENSE.txt")
shutil.copy2("docs/Native-GUI.md", ROOT/"Native-GUI.md")
shutil.copy2("built-crossplay/crossplay-lock.json", ROOT/"crossplay-lock.json")
for jar in Path("built-crossplay/velocity").glob("*.jar"):
    copy_jar(jar, ROOT/"proxy"/"plugins"/jar.name)
if len(list((ROOT/"proxy"/"plugins").glob("*.jar"))) != 2:
    raise RuntimeError("Run tools/download-crossplay.py built-crossplay before packaging")
(ROOT/"proxy"/"forwarding.secret").write_text(secret+"\n")
servers="\n".join(f'  {name} = "127.0.0.1:{port}"' for name,port in PLANETS.items())
(ROOT/"proxy"/"velocity.toml").write_text(f'''config-version = "2.7"
bind = "0.0.0.0:25565"
motd = "<gold>DuneRealms <gray>| Spacing Guild"
show-max-players = 100
online-mode = true
player-info-forwarding-mode = "modern"
forwarding-secret-file = "forwarding.secret"
[servers]
{servers}
try = ["spacing-guild"]
[advanced]
bungee-plugin-message-channel = true
''')
src=Path("DuneTerrain/src/main/resources/config.yml").read_text()
core=Path("DuneRealms-minecraft-only/src/main/resources/config.yml").read_text()
npc=Path("DuneNPC/src/main/resources/config.yml").read_text()
ess=Path("DuneEss/src/main/resources/config.yml").read_text()
for name,port in PLANETS.items():
    dest=ROOT/"servers"/name
    plugins=dest/"plugins"
    plugins.mkdir(parents=True)
    (dest/"server.properties").write_text(f"server-port={port}\nserver-ip=127.0.0.1\nonline-mode=false\nlevel-name={IDS[name]}\nspawn-protection=16\n")
    (dest/"eula.txt").write_text("eula=false\n# Review Minecraft EULA and set true to accept.\n")
    (dest/"config").mkdir()
    (dest/"config"/"paper-global.yml").write_text(f'''proxies:
  velocity:
    enabled: true
    online-mode: true
    secret: "{secret}"
''')
    (dest/"bukkit.yml").write_text(f'''worlds:
  {IDS[name]}:
    generator: DuneTerrain
    biome-provider: DuneTerrain
''')
    for plugin in ("DuneTerrain","DuneCore","DuneNPC","DuneEss"):
        (plugins/plugin).mkdir()
    terrain=src.replace("planet: arrakis",f"planet: {IDS[name]}",1)
    if name=="spacing-guild":
        terrain+="\n  spacing_guild:\n    enabled: true\n    spawn: {x: 0, z: 0}\n    terrain: {base-height: 80, height-variation: 0, noise-scale: 0.02}\n    biome: PLAINS\n    surface: SMOOTH_STONE\n    filler: STONE\n    water: {level: 0, swamp-pools: false, coverage: 0.0}\n    vegetation: {trees: false, tree-chance: 0.0, flowers-per-chunk: 0}\n    ores: {min-y: -48, max-y: 48, vein-size: 5, COAL_ORE: 0}\n"
    (plugins/"DuneTerrain"/"config.yml").write_text(terrain)
    (plugins/"DuneTerrain"/"generation.yml").write_text("# Generation profile staging file; DuneTerrain currently reads config.yml.\n"+terrain)
    core_config=core.replace("local-planet: arrakis",f"local-planet: {IDS[name]}")
    core_config=core_config.replace("sigma_draconis_iv: sigma-draconis-iv","sigma_draconis_iv: sigma-draconis-iv\n    spacing_guild: spacing-guild")
    (plugins/"DuneCore"/"config.yml").write_text(core_config)
    (plugins/"DuneNPC"/"config.yml").write_text(npc)
    (plugins/"DuneEss"/"config.yml").write_text(ess)
    copy_jar("built-crossplay/spigot/floodgate-spigot.jar", plugins/"floodgate-spigot.jar")
    for jar in Path("built-crossplay/universal").glob("*.jar"):
        copy_jar(jar, plugins/jar.name)
    if name=="spacing-guild":
        copy_jar("built-plugins/DuneLobby.jar", plugins/"DuneRealms-Lobby.jar")
    for plugin in ("DuneCore","DuneTerrain","DuneNPC","DuneEss"):
        matches=list(Path("built-plugins").glob(("DuneRealms-Essentials" if plugin=="DuneEss" else plugin)+"*.jar"))
        if len(matches)!=1: raise RuntimeError(f"Expected one jar for {plugin}, found {matches}")
        copy_jar(matches[0],plugins/("DuneRealms-"+ ("Essentials" if plugin=="DuneEss" else plugin.removeprefix("Dune"))+".jar"))
(ROOT/"README.md").write_text("""DuneRealms Velocity network — Paper 1.21.8 / Java 21
See docs/Native-GUI.md in the source for Floodgate forwarding and shared key setup.
Run the Velocity proxy and six Paper backends separately. Download the matching server
and proxy binaries from their official distribution sites. Do not expose backend ports.
Set eula=true only after reviewing the Minecraft EULA.
Spacing Guild is the first-join lobby (port 25571); connect players to Velocity port 25565.
IMPORTANT: Spacing Guild world generation is NOT yet implemented by DuneTerrain; its
configuration is a proposed profile and will not generate a custom lobby until code is added.
generation.yml is a staging copy; DuneTerrain currently reads config.yml.
Guild NPC/return command and cross-server economy synchronization are not verified.
All backend server.properties bind to loopback; adapt for separate hosts with firewall rules.
""")
with zipfile.ZipFile("DuneRealms-Velocity-Network.zip","w",zipfile.ZIP_DEFLATED) as z:
    for p in ROOT.rglob("*"):
        if p.is_file(): z.write(p,p.relative_to(ROOT.parent))
print("Created DuneRealms-Velocity-Network.zip")
