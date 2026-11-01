"""Production resizing only. Imagery is created with built-in imagegen."""
import json
from pathlib import Path
from PIL import Image, ImageDraw
root = Path(__file__).resolve().parents[3]
art = root / "website/artwork/seals"
web = root / "website/web/assets/seals"
records = json.loads((Path(__file__).parent / "generation-provenance.json").read_text(encoding="utf-8"))
manifest = {}
thumbs = []
for item in records:
    key = item["key"]
    src = Path(item["generated_path"])
    image = Image.open(src).convert("RGBA")
    image.save(art / (key + ".png"), optimize=True)
    alpha = image.getchannel("A")
    if alpha.getextrema()[0] == 255:
        raise ValueError(f"{key}: generated image has no transparency")
    image.thumbnail((600, 600), Image.Resampling.LANCZOS)
    image.save(web / (key + ".png"), optimize=True)
    for size in (150, 300):
        thumb = image.copy()
        thumb.thumbnail((size, size), Image.Resampling.LANCZOS)
        thumb.save(web / f"{key}-{size}.png", optimize=True)
    manifest[key] = {"label": item["label"], "role": item.get("role", ""), "src": f"/assets/seals/{key}.png", "footer150": f"/assets/seals/{key}-150.png", "footer300": f"/assets/seals/{key}-300.png", "original": f"website/artwork/seals/{key}.png", "alpha": True}
    preview = Image.new("RGB", (260, 220), "#121a28")
    t = image.copy()
    t.thumbnail((170, 170), Image.Resampling.LANCZOS)
    preview.paste(t, ((260-t.width)//2, 8), t)
    ImageDraw.Draw(preview).text((8, 185), item["label"], fill="#edca7c")
    thumbs.append(preview)
sheet = Image.new("RGB", (260*4, 220*3), "#121a28")
for i, t in enumerate(thumbs):
    sheet.paste(t, ((i%4)*260, (i//4)*220))
sheet.save(art / "seal-contact-sheet.jpg", quality=92)
(web / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
print(json.dumps({"count": len(records), "manifest": str(web / "manifest.json"), "contact_sheet": str(art / "seal-contact-sheet.jpg")}))
