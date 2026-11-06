"""Original DuneRealms Arrakeen block plan; offline only, never connects to a server.

Python 3 standard library writes Sponge v2 .schem. Pillow is optional for previews.
Coordinates are local, with ground at y=3; installation origin is intentionally unset.
"""
import argparse
from collections import Counter, deque
import gzip
import hashlib
import json
import math
from pathlib import Path
import struct

W, H, L = 416, 88, 256
NAMES = ["air", "sandstone", "smooth_sandstone", "cut_sandstone",
         "chiseled_sandstone", "yellow_terracotta", "dark_prismarine",
         "green_terracotta", "tinted_glass", "sea_lantern", "sand",
         "polished_deepslate", "red_sandstone", "brown_terracotta"]
AIR, STONE, SMOOTH, CUT, CARVED, GOLD, GREEN, WALLGREEN, GLASS, LIGHT, SAND, DARK, RED, BROWN = range(len(NAMES))
blocks = bytearray(W * H * L)


def at(x, y, z):
    return x + W * (z + L * y)


def box(x0, y0, z0, x1, y1, z1, material):
    assert 0 <= x0 <= x1 < W and 0 <= z0 <= z1 < L and 0 <= y0 <= y1 < H
    row = bytes([material]) * (x1 - x0 + 1)
    for y in range(y0, y1 + 1):
        for z in range(z0, z1 + 1):
            i = at(x0, y, z)
            blocks[i:i + len(row)] = row


def disk(cx, y, cz, radius, material):
    for dz in range(-radius, radius + 1):
        dx = math.isqrt(radius * radius - dz * dz)
        box(cx - dx, y, cz + dz, cx + dx, y, cz + dz, material)


def dome(cx, y, cz, radius, material):
    # Hollow dome with a one-block stone shell, open below its spring line.
    for dy in range(radius + 1):
        r = math.isqrt(radius * radius - dy * dy)
        disk(cx, y + dy, cz, r, material)
        inner = r - 2
        if inner >= 0 and dy < radius - 1:
            disk(cx, y + dy, cz, inner, AIR)


def room(x, z, width, depth, height, y=4, dome_roof=False):
    x1, z1, top = x + width - 1, z + depth - 1, y + height
    box(x, y, z, x1, top, z1, SMOOTH)
    box(x + 1, y, z + 1, x1 - 1, top - 1, z1 - 1, AIR)
    box(x, y + 1, z, x1, y + 1, z1, CUT)
    box(x + 1, y + 1, z + 1, x1 - 1, y + 1, z1 - 1, AIR)
    for yy, mat in [(top - 2, GOLD), (top, GREEN)]:
        box(x, yy, z, x1, yy, z, mat)
        box(x, yy, z1, x1, yy, z1, mat)
        box(x, yy, z, x, yy, z1, mat)
        box(x1, yy, z, x1, yy, z1, mat)
    # Door on both sides, all frontages on connected public lanes.
    mid = x + width // 2
    for zz in (z, z1):
        box(mid - 1, y, zz, mid + 1, y + 3, zz, AIR)
        box(mid - 1, y + 4, zz, mid + 1, y + 4, zz, CARVED)
    for xx in range(x + 3, x1 - 1, 5):
        for zz in (z, z1):
            box(xx, y + 5, zz, xx + 1, y + 6, zz, GLASS)
    box(x + 2, y, z + 2, x + 2, y, z + 2, LIGHT)
    if dome_roof:
        dome(x + width // 2, top, z + depth // 2,
             min(width, depth) // 2 - 2, GOLD)


def tower(x, z):
    disk(x, 3, z, 7, STONE)
    for y in range(4, 28):
        disk(x, y, z, 6, CUT if y % 6 == 0 else SMOOTH)
        disk(x, y, z, 4, AIR)
    disk(x, 28, z, 7, GOLD)
    disk(x, 29, z, 7, GREEN)
    dome(x, 30, z, 6, SMOOTH)
    disk(x, 36, z, 1, GOLD)


def build():
    box(0, 0, 0, W - 1, 2, L - 1, STONE)
    box(0, 3, 0, W - 1, 3, L - 1, SAND)
    box(16, 3, 16, 241, 3, 239, CUT)
    box(251, 3, 24, 403, 3, 231, DARK)
    # Four defensive walls, walkable ramparts and castellations.
    for x0, z0, x1, z1 in [(16, 16, 241, 19), (16, 236, 241, 239),
                            (16, 16, 19, 239), (238, 16, 241, 239)]:
        box(x0, 4, z0, x1, 20, z1, SMOOTH)
        box(x0, 17, z0, x1, 17, z1, GOLD)
    for x in range(20, 238, 7):
        for z in (16, 238):
            box(x, 21, z, x + 2, 22, z + 1, CUT)
    for z in range(20, 236, 7):
        for x in (16, 240):
            box(x, 21, z, x + 1, 22, z + 2, CUT)
    for x in (19, 128, 238):
        for z in (19, 236):
            tower(x, z)
    for z in (96, 144):
        tower(238, z)
    # Axial arrival avenue connects port, east gate and palace forecourt.
    box(95, 3, 109, 409, 3, 131, SMOOTH)
    for z in (109, 131):
        box(95, 3, z, 409, 3, z, GOLD)
    box(237, 4, 112, 242, 15, 128, AIR)
    box(237, 16, 113, 242, 17, 127, CARVED)
    # Original terraced imperial palace, with accessible galleries.
    room(37, 76, 65, 88, 22, dome_roof=False)
    room(43, 85, 53, 70, 17, y=27)
    room(51, 97, 37, 46, 13, y=45)
    dome(69, 59, 120, 16, GOLD)
    # Broad palace portal, terrace staircase, internal stairs to each gallery.
    box(99, 4, 114, 102, 14, 126, AIR)
    box(40, 26, 80, 42, 26, 105, AIR)
    for i in range(23):
        box(40, 4 + i, 80 + i, 42, 4 + i, 81 + i, SMOOTH)
    box(40, 27, 101, 45, 28, 105, AIR)
    box(46, 44, 90, 48, 44, 110, AIR)
    for i in range(18):
        box(46, 27 + i, 90 + i, 48, 27 + i, 91 + i, SMOOTH)
    box(46, 45, 106, 53, 46, 110, AIR)
    # Habitable quarters arranged around shaded lanes, rather than solid scenery.
    for row, z in enumerate((32, 69, 150, 187)):
        for col, x in enumerate((114, 155, 196)):
            room(x, z, 28, 27, 10 + 3 * ((row + col) % 3), dome_roof=(row + col) % 2 == 0)
    for x in (108, 149, 190, 231):
        box(x, 3, 25, x + 3, 3, 231, SMOOTH)
    # Covered market stalls on the main avenue; canvas at least four blocks above feet.
    for x in range(116, 225, 18):
        for z in (101, 135):
            box(x, 4, z, x, 8, z, CUT)
            box(x + 10, 4, z, x + 10, 8, z, CUT)
            box(x, 9, z - 2, x + 10, 9, z + 2, GREEN)
            box(x + 1, 4, z, x + 9, 4, z, BROWN)
            box(x + 4, 8, z, x + 5, 8, z, LIGHT)
    # Starport terminal: a through-building arrival hall, no implied transport scripts.
    room(256, 86, 29, 69, 17)
    box(256, 4, 114, 284, 13, 126, AIR)
    box(257, 4, 110, 283, 4, 110, GOLD)
    box(257, 4, 130, 283, 4, 130, GOLD)
    room(260, 39, 19, 25, 32)
    box(259, 29, 38, 279, 33, 64, GLASS)
    box(258, 34, 37, 280, 35, 65, GREEN)
    # Four recessed landing aprons, gold rings, illuminated radial markings.
    for x, z in ((316, 65), (374, 65), (316, 183), (374, 183)):
        disk(x, 3, z, 24, GOLD)
        disk(x, 3, z, 22, GREEN)
        disk(x, 3, z, 19, DARK)
        for dx, dz in ((0, 21), (0, -21), (21, 0), (-21, 0)):
            box(x + dx, 3, z + dz, x + dx, 3, z + dz, LIGHT)
        box(x - 10, 3, z, x + 10, 3, z, GOLD)
        box(x, 3, z - 10, x, 3, z + 10, GOLD)
        # Safe, flush access strip to public avenue.
        za, zb = sorted((z, 120))
        box(x - 2, 3, za, x + 2, 3, zb, SMOOTH)
    # Grounded ceremonial frigate with access ramp, not a functioning vehicle.
    box(304, 5, 49, 328, 9, 81, SMOOTH)
    box(309, 10, 54, 323, 15, 76, GREEN)
    box(312, 12, 48, 320, 15, 53, GLASS)
    box(305, 6, 50, 327, 8, 80, AIR)
    box(313, 6, 80, 319, 8, 82, AIR)
    box(313, 5, 82, 319, 5, 82, CUT)
    for x in (304, 328):
        box(x, 4, 50, x, 4, 52, DARK)
        box(x, 4, 78, x, 4, 80, DARK)
    for i in range(2):
        box(313, 4 + i, 84 - i, 319, 4 + i, 84 - i, CUT)
    # Spawn plaza symbol and shielded lamp columns on both sides of the main path.
    disk(294, 3, 120, 7, GOLD)
    disk(294, 3, 120, 5, GREEN)
    for x in range(107, 409, 22):
        for z in (107, 133):
            box(x, 4, z, x, 8, z, CARVED)
            box(x, 9, z, x, 9, z, LIGHT)


def validate():
    # Real block-based walking flood fill, 2 blocks headroom, one-block step limit.
    start = (294, 4, 120)
    def walk(x, y, z):
        return (0 < x < W - 1 and 1 <= y < H - 1 and 0 < z < L - 1
                and blocks[at(x, y - 1, z)] != AIR
                and blocks[at(x, y, z)] == AIR and blocks[at(x, y + 1, z)] == AIR)
    assert walk(*start), "Spawn clearance"
    queue, seen = deque([start]), {start}
    while queue:
        x, y, z = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in (0, 1, -1):
                p = x + dx, y + dy, z + dz
                if p not in seen and walk(*p):
                    seen.add(p)
                    queue.append(p)
                    break
    targets = {"palace": (95, 4, 120), "terminal": (270, 4, 120),
               "city_gate": (239, 4, 120), "city_market": (160, 4, 120),
               "palace_gallery_1": (60, 27, 90), "palace_gallery_2": (60, 45, 100),
               "frigate_interior": (316, 6, 65)}
    for row, z in enumerate((32, 69, 150, 187)):
        for col, x in enumerate((114, 155, 196)):
            targets[f"house_{row}_{col}"] = (x + 14, 4, z + 12)
    targets.update({f"landing_{x}_{z}": (x + 18, 4, z)
                    for x, z in ((316, 65), (374, 65), (316, 183), (374, 183))})
    results = {name: pos in seen for name, pos in targets.items()}
    assert all(results.values()), results
    assert max(blocks) < 128, "Writer uses one-byte palette VarInts"
    return {"spawnLocal": start, "walkablePositions": len(seen), "reachable": results,
            "dimensions": [W, H, L], "installation": "not installed; origin unset",
            "nonAir": len(blocks) - blocks.count(0),
            "paletteCounts": {NAMES[k]: v for k, v in Counter(blocks).items()}}


def named(kind, name, payload):
    text = name.encode("utf-8")
    return bytes([kind]) + struct.pack(">H", len(text)) + text + payload


def schematic():
    tags = []
    for name, value in (("Version", 2), ("DataVersion", 4189), ("PaletteMax", len(NAMES))):
        tags.append(named(3, name, struct.pack(">i", value)))
    for name, value in (("Width", W), ("Height", H), ("Length", L)):
        tags.append(named(2, name, struct.pack(">H", value)))
    tags.append(named(11, "Offset", struct.pack(">iiii", 3, 0, 0, 0)))
    palette = b"".join(named(3, "minecraft:" + name, struct.pack(">i", i))
                       for i, name in enumerate(NAMES)) + b"\0"
    tags.append(named(10, "Palette", palette))
    tags.append(named(7, "BlockData", struct.pack(">i", len(blocks)) + blocks))
    for name in ("BlockEntities", "Entities"):
        tags.append(named(9, name, b"\x0a\0\0\0\0"))
    return gzip.compress(named(10, "Schematic", b"".join(tags) + b"\0"), mtime=0)


def preview(out):
    from PIL import Image, ImageDraw
    colors = ["#152b25", "#b89a62", "#dfc38c", "#c7a76e", "#c2aa71",
              "#c59b32", "#1c5148", "#3c5640", "#425959", "#f6edbb",
              "#bfa167", "#333a3c", "#ac6744", "#73543a"]
    top = Image.new("RGB", (W, L))
    heights = []
    for z in range(L):
        for x in range(W):
            y = next(y for y in range(H - 1, -1, -1) if blocks[at(x, y, z)])
            heights.append(y)
            rgb = tuple(bytes.fromhex(colors[blocks[at(x, y, z)]][1:]))
            factor = .78 + y / H * .35
            top.putpixel((x, z), tuple(min(255, int(v * factor)) for v in rgb))
    top = top.resize((W * 3, L * 3), Image.Resampling.NEAREST)
    canvas = Image.new("RGB", (W * 3 + 48, L * 3 + 112), "#0b211a")
    canvas.paste(top, (24, 64))
    d = ImageDraw.Draw(canvas)
    d.text((24, 18), "DUNEREALMS / ARRAKEEN + STARPORT / ORIGINAL BLOCK PLAN", fill="#e7c87c")
    d.text((24, L * 3 + 83), "Top view of actual schematic blocks. First build draft; not a Minecraft client capture.", fill="#d7ddcc")
    canvas.save(out / "arrakeen-plan.png")
    # Height-map projection of the same blocks, not an invented concept image.
    iso = Image.new("RGB", (1500, 950), "#0b211a")
    d = ImageDraw.Draw(iso)
    rgb_colors = [tuple(bytes.fromhex(c[1:])) for c in colors]
    def project(x, y, z):
        return 580 + (x - z) * 2, 158 + (x + z) - y * 2
    def shade(rgb, factor):
        return tuple(int(v * factor) for v in rgb)
    for diagonal in range(W + L - 1):
        for x in range(max(0, diagonal - L + 1), min(W - 1, diagonal) + 1):
            z = diagonal - x
            y = heights[z * W + x]
            for dx, dz in ((1, 0), (0, 1)):
                nx, nz = x + dx, z + dz
                neighbor = heights[nz * W + nx] if nx < W and nz < L else -1
                for yy in range(neighbor + 1, y + 1):
                    rgb = rgb_colors[blocks[at(x, yy, z)]]
                    if dx:
                        corners = [(x + 1, yy, z), (x + 1, yy, z + 1),
                                   (x + 1, yy + 1, z + 1), (x + 1, yy + 1, z)]
                    else:
                        corners = [(x, yy, z + 1), (x + 1, yy, z + 1),
                                   (x + 1, yy + 1, z + 1), (x, yy + 1, z + 1)]
                    d.polygon([project(*p) for p in corners], fill=shade(rgb, .60 if dx else .8))
            rgb = rgb_colors[blocks[at(x, y, z)]]
            d.polygon([project(x, y + 1, z), project(x + 1, y + 1, z),
                       project(x + 1, y + 1, z + 1), project(x, y + 1, z + 1)], fill=rgb)
    d.text((32, 30), "DUNEREALMS / ARRAKEEN + STARPORT / FIRST BLOCK BUILD", fill="#e7c87c")
    d.text((32, 913), "Projection of actual schematic blocks. Vanilla palette approximation; not a Minecraft client capture.", fill="#d7ddcc")
    iso.save(out / "arrakeen-isometric.png")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--preview", action="store_true")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    build()
    report = validate()
    data = schematic()
    (args.output / "DuneRealms-Arrakeen-Starport.schem").write_bytes(data)
    report.update(sha256=hashlib.sha256(data).hexdigest(), compressedBytes=len(data))
    (args.output / "arrakeen-validation.json").write_text(json.dumps(report, indent=2) + "\n")
    if args.preview:
        preview(args.output)
    print(json.dumps(report, indent=2))
