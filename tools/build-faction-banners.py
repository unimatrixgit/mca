"""Original pixel heraldry, native banner/shield UVs, additive Java resource/datapack.
Run with Pillow: python tools/build-faction-banners.py --output OUTPUT --base-pack V7.zip
No Mojang textures or binaries are copied into the source artwork.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import json,zipfile,argparse,hashlib

ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path,required=True);parser.add_argument('--base-pack',type=Path,required=True);args=parser.parse_args()
out=args.output;out.mkdir(parents=True,exist_ok=True)
pack=ROOT/'DuneResourcePack';data=ROOT/'worlds/faction-heraldry-datapack'
with zipfile.ZipFile(args.base_pack) as base:
 base_json={n:json.loads(base.read(n)) for n in base.namelist() if n in ['assets/dunerealms/lang/en_us.json','assets/minecraft/atlases/banner_patterns.json','assets/minecraft/atlases/shield_patterns.json']}
GOLD='#e2b753';DARK='#191816';WHITE='#eee4c2'
factions={'atreides':('#122e23','#b33a2d'),'corrino':('#17181a',GOLD),'harkonnen':('#2a1518','#e75b47'),'fremen':('#173a43','#69d4e3'),'ordos':('#263123','#b6d682')}

def artwork(name,bg,ink):
 # A 40x80 cloth grid makes heraldry legible on placed Minecraft banners.
 im=Image.new('RGBA',(40,80),bg);d=ImageDraw.Draw(im)
 d.rectangle((1,1,38,78),outline=GOLD,width=1);d.line([(3,6),(3,3),(8,3)],fill=GOLD);d.line([(31,3),(36,3),(36,6)],fill=GOLD)
 d.rectangle((3,72,36,75),fill='#76342a' if name=='corrino' else '#101b18')
 d.line((7,73,32,73),fill=GOLD)
 if name=='corrino':
  # Crown and a full rampant lion: forepaws to the left, raised curling tail to the right.
  d.polygon([(12,15),(11,9),(15,12),(18,7),(21,12),(25,9),(24,15)],fill=ink);d.rectangle((12,16,24,17),fill=ink)
  d.polygon([(17,25),(13,23),(10,26),(10,31),(14,35),(12,40),(16,44),(19,43),(22,38),(23,32),(21,27)],fill=ink)
  d.rectangle((8,27,13,29),fill=ink);d.rectangle((11,31,15,33),fill=ink)
  d.polygon([(15,36),(8,37),(8,33),(5,33),(5,40),(14,41)],fill=ink)
  d.polygon([(17,42),(11,46),(8,44),(6,47),(11,50),(21,46),(23,43)],fill=ink)
  d.polygon([(19,43),(21,50),(17,55),(12,56),(12,59),(20,59),(25,53),(26,44)],fill=ink)
  d.polygon([(23,44),(27,51),(25,60),(20,62),(20,65),(29,64),(31,51),(27,40)],fill=ink)
  d.line([(24,40),(30,36),(32,28),(29,23),(26,25),(27,29)],fill=ink,width=3)
  d.point((12,27),fill=DARK)
 elif name=='atreides':
  # The existing DuneRealms Atreides hawk, translated into the narrow cloth grid.
  d.polygon([(5,30),(14,33),(20,39),(26,33),(35,30),(29,42),(23,46),(20,59),(17,46),(11,42)],fill=ink)
  d.polygon([(17,27),(20,20),(23,27),(20,33)],fill=ink)
 elif name=='harkonnen':
  d.polygon([(6,22),(10,34),(15,29),(25,29),(30,34),(34,22),(36,43),(28,46),(26,58),(20,63),(14,58),(12,46),(4,43)],fill=ink)
  d.rectangle((13,41,17,43),fill=DARK);d.rectangle((23,41,27,43),fill=DARK);d.polygon([(16,52),(20,55),(24,52)],fill=DARK)
 elif name=='fremen':
  d.polygon([(20,19),(8,42),(8,52),(20,62),(32,52),(32,42)],fill=ink)
  d.line([(18,31),(13,43),(13,50),(19,55)],fill=bg,width=3)
  d.point((24,49),fill=WHITE)
 else:
  d.polygon([(8,24),(28,24),(34,31),(28,39),(16,39),(11,46),(17,53),(29,53),(29,59),(14,59),(5,47),(12,33),(27,33),(29,30),(8,30)],fill=ink)
  d.polygon([(7,19),(14,24),(7,29)],fill=ink);d.point((10,24),fill=DARK)
 # Broad cloth lighting remains pixelated. No fake UI or text baked into heraldry.
 return im

previews=[];manifest={}
for name,(bg,ink) in factions.items():
 cloth=artwork(name,bg,ink);previews.append((name,cloth))
 texture=Image.new('RGBA',(256,256),(0,0,0,0));front=cloth.resize((80,160),Image.Resampling.NEAREST)
 texture.paste(front,(4,4));texture.paste(front.transpose(Image.Transpose.FLIP_LEFT_RIGHT),(88,4))
 assert texture.crop((4,4,84,164)).tobytes()==front.tobytes()
 assert texture.crop((88,4,168,164)).tobytes()==front.transpose(Image.Transpose.FLIP_LEFT_RIGHT).tobytes()
 path=pack/f'assets/dunerealms/textures/entity/banner/{name}.png';path.parent.mkdir(parents=True,exist_ok=True);texture.save(path)
 shield=Image.new('RGBA',(256,256),(0,0,0,0));shield.paste(cloth.resize((48,88),Image.Resampling.NEAREST),(4,4))
 sp=pack/f'assets/dunerealms/textures/entity/shield/{name}.png';sp.parent.mkdir(parents=True,exist_ok=True);shield.save(sp)
 entry=data/f'data/dunerealms/banner_pattern/{name}.json';entry.parent.mkdir(parents=True,exist_ok=True);entry.write_text(json.dumps({'asset_id':f'dunerealms:{name}','translation_key':f'block.dunerealms.banner.{name}'},indent=2)+'\n')
 manifest[name]={'base':'minecraft:black_banner','pattern':f'dunerealms:{name}','dye':'white','textureSha256':hashlib.sha256(path.read_bytes()).hexdigest()}

for kind in ['banner','shield']:
 atlas=pack/f'assets/minecraft/atlases/{kind}_patterns.json';atlas.parent.mkdir(parents=True,exist_ok=True)
 # Additive atlas definitions merge with vanilla sources; no replace flag.
 content=base_json.get(atlas.relative_to(pack).as_posix(),{'sources':[]})
 sources=content.setdefault('sources',[])
 for name in factions:
  source={'type':'minecraft:single','resource':f'dunerealms:entity/{kind}/{name}'}
  if source not in sources:sources.append(source)
 atlas.write_text(json.dumps(content,indent=2)+'\n')
langpath=pack/'assets/dunerealms/lang/en_us.json';langpath.parent.mkdir(parents=True,exist_ok=True)
lang=base_json.get('assets/dunerealms/lang/en_us.json',json.loads(langpath.read_text()) if langpath.exists() else {})
for name in factions:
 for color in ['white','orange','magenta','light_blue','yellow','lime','pink','gray','light_gray','cyan','purple','blue','brown','green','red','black']:
  lang[f'block.dunerealms.banner.{name}.{color}']=name.title()+' Standard'
langpath.write_text(json.dumps(lang,indent=2)+'\n')
(data/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':81,'description':'DuneRealms faction heraldry | Java 1.21.8'}},indent=2)+'\n')
with zipfile.ZipFile(out/'DuneRealms-Faction-Heraldry-1.21.8.zip','w',zipfile.ZIP_DEFLATED) as z:
 for p in sorted(data.rglob('*')):
  if p.is_file():z.write(p,p.relative_to(data).as_posix())

bundle=out/'DuneRealms-Imperial-GUI-1.21.8-v9.zip'
changed=[p for p in pack.rglob('*') if p.is_file() and ('/textures/entity/banner/' in p.as_posix() or '/textures/entity/shield/' in p.as_posix() or p.name in ['banner_patterns.json','shield_patterns.json'] or p==langpath)]
with zipfile.ZipFile(args.base_pack) as old,zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED) as new:
 names={p.relative_to(pack).as_posix() for p in changed}
 for info in old.infolist():
  if info.filename not in names:new.writestr(info,old.read(info.filename))
 for p in sorted(changed):new.write(p,p.relative_to(pack).as_posix())
 # Prove that every pre-existing unrelated asset is retained byte-for-byte.
with zipfile.ZipFile(args.base_pack) as old,zipfile.ZipFile(bundle) as new:
 assert all(old.read(n)==new.read(n) for n in old.namelist() if n not in names)
manifest['packSha1']=hashlib.sha1(bundle.read_bytes()).hexdigest();manifest['baseSha1']=hashlib.sha1(args.base_pack.read_bytes()).hexdigest()
assert manifest['baseSha1']=='6222875ddd969bebbeefc5b607051fdf61b1a14e'
(out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
preview=Image.new('RGB',(1200,660),'#0d211b');d=ImageDraw.Draw(preview)
for i,(name,cloth) in enumerate(previews):
 x=32+i*236;preview.paste(cloth.resize((200,400),Image.Resampling.NEAREST),(x,100))
 d.text((x+30,530),name.upper(),fill=GOLD,stroke_width=0,font=ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf',20) if Path('C:/Windows/Fonts/arialbd.ttf').exists() else ImageFont.load_default())
preview.save(out/'faction-banners-preview.png')
print(json.dumps({'pack':str(bundle),'sha1':manifest['packSha1'],'factions':5,'retainedBaseAssets':True}))
