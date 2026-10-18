#!/usr/bin/env python3
"""Produce per-server world-generation config ZIPs from the repository's terrain defaults.

Not a Minecraft world pregenerator: actual .mca files require Paper to run.
"""
from pathlib import Path
import shutil, zipfile, re
names=("arrakis","giedi-prime","kaitain","caladan","sigma-draconis-iv","spacing-guild")
source=Path(__file__).resolve().parents[1]/"DuneTerrain/src/main/resources/config.yml"
if not source.exists(): raise SystemExit("Missing DuneTerrain default config")
base=source.read_text()
out=Path("network/generated-worlds")
out.mkdir(parents=True,exist_ok=True)
for name in names:
    ident=name.replace("-","_")
    target=out/name/"plugins"/"DuneTerrain"
    target.mkdir(parents=True,exist_ok=True)
    config=base.replace("planet: arrakis","planet: "+ident,1)
    if name=="spacing-guild":
        config+="\n# WARNING: Spacing Guild custom generation is NOT implemented yet.\n"
    (target/"config.yml").write_text(config)
    (target/"generation.yml").write_text("# Proposed centralized generation settings; current plugin reads config.yml\n"+config)
    (out/name/"WORLD-README.txt").write_text(
        f"{name}: config files only. Start Paper with DuneTerrain to generate actual world files.\n"
        +"Spacing Guild custom world generation is NOT implemented.\n" if name=="spacing-guild"
        else f"{name}: config files only. Start Paper with DuneTerrain to generate actual world files.\n"
    )
    with zipfile.ZipFile(out/(name+"-world-config.zip"),"w",zipfile.ZIP_DEFLATED) as z:
        for p in (out/name).rglob("*"):
            if p.is_file(): z.write(p,p.relative_to(out/name))
    print(out/(name+"-world-config.zip"))
