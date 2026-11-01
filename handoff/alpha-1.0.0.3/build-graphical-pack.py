from pathlib import Path
from PIL import Image,ImageDraw
import zipfile,json,hashlib,io
r=Path('output/gui-design-20261101');src=r/'DuneRealms-Imperial-GUI-1.21.8-v6.zip';out=r/'DuneRealms-Imperial-GUI-1.21.8-v7.zip';updates={}
# Code-native UI layout: exact Minecraft inventory coordinates, not a screenshot edit.
im=Image.new('RGBA',(176,222),(8,19,30,255));d=ImageDraw.Draw(im)
d.rectangle((0,0,175,221),outline=(208,169,72),width=2);d.rectangle((3,3,172,218),outline=(58,88,75));d.rectangle((5,4,170,15),fill=(17,45,37));d.line((7,16,168,16),fill=(183,146,58))
for x in [2,173]:
 for y in [2,219]:d.polygon([(x,y-2),(x+2,y),(x,y+2),(x-2,y)],fill=(82,184,204))
for row in [0,1]:
 for col in range(3):
  x=7+54*col;y=35+36*row;d.rounded_rectangle((x,y,x+52,y+34),radius=3,fill=(15,40,43),outline=(145,126,66));d.line((x+5,y+1,x+47,y+1),fill=(225,190,103));
  for cx,cy in [(x+2,y+2),(x+50,y+32)]:d.point((cx,cy),fill=(112,203,193))
d.line((7,108,168,108),fill=(74,108,84));d.rectangle((7,126,70,136),fill=(181,166,111))
for y in [138,156,174,196]:
 for col in range(9):
  x=7+18*col;d.rectangle((x,y,x+17,y+17),fill=(9,28,29),outline=(46,73,61));d.line((x+1,y+1,x+15,y+1),fill=(5,16,22))
buf=io.BytesIO();im.save(buf,format='PNG');updates['assets/dunerealms/textures/gui/imperial_inventory_v1.png']=buf.getvalue()
with zipfile.ZipFile(src) as old:
 fontpath='assets/dunerealms/font/imperial_menus.json';font=json.loads(old.read(fontpath));font['providers'] += [{'type':'bitmap','file':'dunerealms:gui/imperial_inventory_v1.png','height':222,'ascent':13,'chars':['\ue230']},{'type':'space','advances':{'\ue231':-177,'\ue232':-8,'\ue233':8}}];updates[fontpath]=json.dumps(font,ensure_ascii=False).encode()
 for model,texture in [('shop','imperial-shop-v1'),('jobs','imperial-jobs-v1'),('staff','imperial-portal'),('clan','faction-atreides-aerdrecht-heraldic')]:
  name='gui_nav_'+model
  updates['assets/dunerealms/items/'+name+'.json']=json.dumps({'model':{'type':'minecraft:model','model':'dunerealms:item/'+name}}).encode()
  updates['assets/dunerealms/models/item/'+name+'.json']=json.dumps({'parent':'minecraft:block/block','gui_light':'front','textures':{'art':'dunerealms:gui/'+texture,'particle':'dunerealms:gui/'+texture},'elements':[{'from':[0,0,7.99],'to':[16,9,8.01],'shade':False,'faces':{'north':{'uv':[0,0,16,16],'texture':'#art'},'south':{'uv':[16,0,0,16],'texture':'#art'}}}],'display':{'gui':{'rotation':[0,0,0],'translation':[0,-4,0],'scale':[2.6,2.6,1]}}}).encode()
 for atlas in ['blocks','items']:
  p='assets/minecraft/atlases/'+atlas+'.json';j=json.loads(old.read(p));j['sources'] += [{'type':'single','resource':'dunerealms:gui/'+t,'sprite':'dunerealms:gui/'+t} for t in ['imperial-shop-v1','imperial-jobs-v1','imperial-portal','faction-atreides-aerdrecht-heraldic']];updates[p]=json.dumps(j).encode()
 with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
  for n in old.namelist():z.writestr(n,updates.pop(n,old.read(n)))
  for n,data in updates.items():z.writestr(n,data)
with zipfile.ZipFile(out) as z:
 for n in z.namelist():
  if n.endswith(('.json','.mcmeta')):json.loads(z.read(n))
 assert Image.open(io.BytesIO(z.read('assets/dunerealms/textures/gui/imperial_inventory_v1.png'))).size==(176,222)
 # Persist all new source-native resources so the pack can be reproduced from Git.
 for n in z.namelist():
  if n.startswith('assets/') and (n not in zipfile.ZipFile(src).namelist() or z.read(n)!=zipfile.ZipFile(src).read(n)):
   p=Path('dunerealms-story-update/DuneResourcePack')/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(n))
manifest={'file':out.name,'sha1':hashlib.sha1(out.read_bytes()).hexdigest(),'sha256':hashlib.sha256(out.read_bytes()).hexdigest(),'layout':'6 graphical cards; 36 owner-bound hit slots; separate confirmation; no Java dialog route','vanillaContainersUnchanged':True};(r/'graphical-pack-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8');print(json.dumps(manifest))
