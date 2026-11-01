from pathlib import Path
import zipfile,json,hashlib,io
from PIL import Image,ImageDraw
r=Path('output/gui-design-20261101');src=r/'DuneRealms-Imperial-GUI-1.21.8-v5.zip';dst=r/'DuneRealms-Imperial-GUI-1.21.8-v6.zip';updates={}
with zipfile.ZipFile(src) as a:
 meta=json.loads(a.read('pack.mcmeta'));meta['pack'].update({'min_format':46,'max_format':[97,1],'supported_formats':{'min_inclusive':46,'max_inclusive':97},'description':'DuneRealms Imperial Navigator | Java 1.21.4 through 26.3'});updates['pack.mcmeta']=json.dumps(meta).encode()
 # Only private-use glyphs are added; ordinary vanilla character providers remain intact.
 default={'providers':[{'type':'reference','id':'dunerealms:imperial_menus'},{'type':'reference','id':'minecraft:include/space'},{'type':'reference','id':'minecraft:include/default','filter':{'uniform':False}},{'type':'reference','id':'minecraft:include/unifont'}]};updates['assets/minecraft/font/default.json']=json.dumps(default).encode()
 # Explicit sprite registration is needed when GUI model textures are outside item/block directories.
 for atlas in ['blocks','items']:
  updates['assets/minecraft/atlases/'+atlas+'.json']=json.dumps({'sources':[{'type':'single','resource':'dunerealms:gui/imperial_menu_atlas_v2','sprite':'dunerealms:gui/imperial_menu_atlas_v2'}]}).encode()
 # Original code-drawn nine-slice primitives; no generated image is edited.
 for name,fill,gold in [('button',(14,34,39,255),(171,132,53,255)),('button_highlighted',(29,64,58,255),(244,211,127,255)),('button_disabled',(24,29,34,255),(89,85,68,255))]:
  im=Image.new('RGBA',(200,20),fill);d=ImageDraw.Draw(im);d.rectangle((0,0,199,19),outline=(5,14,20,255));d.rectangle((1,1,198,18),outline=gold);d.line((3,2,196,2),fill=tuple(min(255,c+20) for c in gold[:3])+(255,));d.line((3,17,196,17),fill=(65,57,35,255));
  for x in [3,196]:d.rectangle((x-1,8,x+1,11),fill=gold)
  buf=io.BytesIO();im.save(buf,format='PNG');path='assets/minecraft/textures/gui/sprites/widget/'+name+'.png';updates[path]=buf.getvalue();updates[path+'.mcmeta']=json.dumps({'gui':{'scaling':{'type':'nine_slice','width':200,'height':20,'border':3}}}).encode()
 with zipfile.ZipFile(dst,'w',zipfile.ZIP_DEFLATED) as z:
  for n in a.namelist():z.writestr(n,updates.pop(n,a.read(n)))
  for n,v in updates.items():z.writestr(n,v)
with zipfile.ZipFile(dst) as z:
 for n in z.namelist():
  if n.endswith(('.json','.mcmeta')):json.loads(z.read(n))
 assert json.loads(z.read('assets/minecraft/font/default.json'))['providers'][0]['id']=='dunerealms:imperial_menus'
manifest={'file':dst.name,'sha1':hashlib.sha1(dst.read_bytes()).hexdigest(),'sha256':hashlib.sha256(dst.read_bytes()).hexdigest(),'clientEvidence':'Minecraft26.3 accepted and loaded v5; missing GUI atlas sprite warnings. Custom glyph absent in dialog screenshot.','changes':['default-font PUA fallback','explicit GUI atlas sprite registration','navy/gold nine-slice buttons','pack version range26.3']};(r/'compat-pack-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8');print(json.dumps(manifest))
