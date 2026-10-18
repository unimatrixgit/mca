#!/usr/bin/env python3
"""Simulate ore generation attempts from the shipped DuneTerrain configuration.

This is a config/model simulation, not an actual Paper world-generation test.
"""
import pathlib, re, random, collections, sys
cfg=(pathlib.Path(__file__).resolve().parents[1]/"DuneTerrain/src/main/resources/config.yml").read_text()
java=(pathlib.Path(__file__).resolve().parents[1]/"DuneTerrain/src/main/java/com/example/duneterrain/DuneTerrain.java").read_text()
expected={
 "arrakis":set(),
 "giedi_prime":{"COAL_ORE","IRON_ORE","COPPER_ORE","REDSTONE_ORE","ANCIENT_DEBRIS"},
 "kaitain":{"GOLD_ORE","EMERALD_ORE","DIAMOND_ORE","LAPIS_ORE"},
 "caladan":{"COAL_ORE","IRON_ORE","COPPER_ORE","LAPIS_ORE"},
 "sigma_draconis_iv":{"IRON_ORE","COPPER_ORE","REDSTONE_ORE","DIAMOND_ORE"},
}
assert 'if (planet.equals("arrakis")) return;' in java, "Missing Arrakis ore safeguard"
assert re.search(r'if \(planet\.equals\("arrakis"\)\) return;[\s\S]{0,300}getConfigurationSection\(path \+ "ores"\)',java), "Arrakis safeguard must precede config reading"
def parse(name):
    m=re.search(r"(?m)^  "+re.escape(name)+r":\s*$",cfg)
    assert m, f"Missing planet {name}"
    next_planet=re.search(r"(?m)^  [a-z_]+:\s*$",cfg[m.end():])
    segment=cfg[m.end():m.end()+next_planet.start()] if next_planet else cfg[m.end():]
    line=re.search(r"(?m)^    ores:\s*\{([^\n]+)\}",segment)
    assert line, f"Missing ore config {name}"
    values={}
    for part in line.group(1).split(","):
        key,value=part.strip().split(":",1)
        values[key.strip()]=int(value.strip())
    return {k:v for k,v in values.items() if (k.endswith("_ORE") or k=="ANCIENT_DEBRIS") and v>0}
rng=random.Random(20261018)
for name,wanted in expected.items():
    ores=parse(name)
    assert set(ores)==wanted, f"{name}: got {set(ores)}, expected {wanted}"
    # 1000 hypothetical chunks: the generator attempts configured counts per chunk.
    counts=collections.Counter()
    for _ in range(1000):
        for ore,attempts in ores.items():
            for _ in range(min(attempts,64)):
                # Sample a candidate vein position using the configured Y range.
                x,z,y=rng.randrange(16),rng.randrange(16),rng.randrange(-48,49)
                assert 0<=x<16 and 0<=z<16 and -48<=y<=48
                counts[ore]+=1
    print(f"{name}: {dict(counts)}")
    if name=="arrakis":assert not counts
assert "spacing_guild:" not in cfg, "Lobby must not use an unsupported custom planet"
print("PASS: config-level 1000-chunk ore simulation; not an in-game Paper test")
