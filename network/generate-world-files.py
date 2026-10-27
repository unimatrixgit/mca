#!/usr/bin/env python3
"""Build planet-specific DuneTerrain configs (not Minecraft region files)."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import argparse, re

PLANETS = ("arrakis", "giedi-prime", "kaitain", "caladan", "sigma-draconis-iv")
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="network/generated-worlds")
    args = parser.parse_args()
    source = Path(__file__).resolve().parents[1] / "DuneTerrain/src/main/resources/config.yml"
    original = source.read_text(encoding="utf-8")
    if not re.search(r"(?m)^  planet: arrakis\s*$", original):
        raise SystemExit("Expected 'server.planet: arrakis' in DuneTerrain config")
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    for planet in PLANETS:
        folder = output / planet
        target = folder / "plugins" / "DuneTerrain"
        target.mkdir(parents=True, exist_ok=True)
        content = re.sub(r"(?m)^(  planet: )arrakis\s*$", lambda m: m.group(1) + planet.replace("-", "_"), original, count=1)
        (target / "config.yml").write_text(content, encoding="utf-8")
        (folder / "README.txt").write_text(
            f"{planet}: DuneTerrain plugin config only.\n"
            "Generate actual world/region/*.mca by starting a compatible Paper server.\n"
            "For primary-world terrain generation, configure generator and biome provider in bukkit.yml.\n",
            encoding="utf-8")
        archive = output / (planet + "-world-config.zip")
        with ZipFile(archive, "w", ZIP_DEFLATED) as z:
            for file in folder.rglob("*"):
                if file.is_file():
                    z.write(file, file.relative_to(folder))
        print(archive)
    lobby = output / "spacing-guild"
    lobby.mkdir(exist_ok=True)
    (lobby / "README.txt").write_text(
        "Vaerkring custom world generator is not implemented.\n"
        "Use vanilla Paper world generation temporarily; do NOT set DuneTerrain server.planet to spacing_guild.\n",
        encoding="utf-8")
    with ZipFile(output / "spacing-guild-world-config.zip", "w", ZIP_DEFLATED) as z:
        z.write(lobby / "README.txt", "README.txt")
    print(output / "spacing-guild-world-config.zip")
if __name__ == "__main__":
    main()
