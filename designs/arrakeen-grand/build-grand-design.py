"""Larger editable Arrakeen proposal, based on verified DuneGit template. Offline only."""
from pathlib import Path
import importlib.util,ast,json,hashlib,zipfile,io,math,gzip,struct,argparse
from collections import Counter,deque
import numpy as np
from PIL import Image,ImageDraw,ImageFont,ImageEnhance,ImageChops
FONT_SANS=next((str(p)for p in[Path('C:/Windows/Fonts/arial.ttf'),Path('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')]if p.is_file()),'DejaVuSans.ttf')
FONT_SERIF=next((str(p)for p in[Path('C:/Windows/Fonts/georgia.ttf'),Path('/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf')]if p.is_file()),'DejaVuSerif.ttf')
ROOT=Path(__file__).resolve().parents[2];DESIGN=Path(__file__).resolve().parent
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',type=Path,default=DESIGN);parser.add_argument('--client-jar',type=Path);parser.add_argument('--no-render',action='store_true');args=parser.parse_args();OUT=args.output;OUT.mkdir(parents=True,exist_ok=True)
spec=importlib.util.spec_from_file_location('arrakeen',ROOT/'tools/build-arrakeen.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
m.NAMES+=['calcite','waxed_cut_copper','stripped_dark_oak_log','dark_oak_planks','orange_terracotta','red_concrete']
A,S,SM,CUT,CARVED,GOLD,GREEN,WALLGREEN,GLASS,LIGHT,SAND,DARK,RED,BROWN,WHITE,COPPER,WOOD,PLANK,ORANGE,HAWK=range(20)
W,H,L=608,144,384;m.W,m.H,m.L=W,H,L;m.blocks=bytearray(W*H*L)
arr=np.frombuffer(m.blocks,dtype=np.uint8).reshape(H,L,W)
old=np.frombuffer(gzip.decompress((DESIGN/'baseline-v2.bin.gz').read_bytes()),dtype=np.uint8).reshape(88,256,416)
OX,OZ=192,64
# Original relative coordinates stay meaningful: world X=oldX-294, Z=oldZ-120.
def box(x0,y0,z0,x1,y1,z1,mat):m.box(x0+OX,y0,z0+OZ,x1+OX,y1,z1+OZ,mat)
def disk(x,y,z,r,mat):m.disk(x+OX,y,z+OZ,r,mat)
def dome(x,y,z,r,mat):m.dome(x+OX,y,z+OZ,r,mat)
def room(x,z,w,d,h,y=4,roof=False):m.room(x+OX,z+OZ,w,d,h,y=y,dome_roof=roof)
def ring(cx,y,cz,r,mat,t=1):
 for z in range(cz-r,cz+r+1):
  for x in range(cx-r,cx+r+1):
   if (r-t)**2<(x-cx)**2+(z-cz)**2<=r*r:box(x,y,z,x,y,z,mat)

m.box(0,0,0,W-1,2,L-1,S);m.box(0,3,0,W-1,3,L-1,SAND)
box(-176,3,-48,241,3,287,CUT)
# Retain the developed original eastern town quarter and starport as actual blocks.
arr[:88,24+OZ:233+OZ,249+OX:416+OX]=old[:,24:233,249:416]
arr[:88,25+OZ:232+OZ,114+OX:237+OX]=old[:,25:232,114:237]

def tower(cx,cz,height=39,r=6):
 for y in range(4,height):ring(cx,y,cz,r,CARVED if y%8==0 else SM,2)
 for y,rr,mat in [(height-3,r+1,CARVED),(height-2,r+2,GOLD),(height-1,r+2,GREEN),(height,r+1,CUT)]:ring(cx,y,cz,rr,mat,2)
 for xx,zz in [(cx-r,cz),(cx+r,cz),(cx,cz-r),(cx,cz+r)]:box(xx,8,zz,xx,height-5,zz,GREEN)
 dome(cx,height+1,cz,r,GOLD);box(cx,height+r+2,cz,cx,height+r+5,cz,COPPER)

# Enlarged perimeter: 418 x336 instead of226 x224, with walkable ramparts and ribs.
for x0,z0,x1,z1 in [(-176,-48,241,-44),(-176,283,241,287),(-176,-48,-172,287),(237,-48,241,287)]:
 box(x0,4,z0,x1,23,z1,SM);box(x0,19,z0,x1,19,z1,GOLD);box(x0,23,z0,x1,23,z1,GREEN)
for x in range(-168,238,7):
 for z in [-48,286]:box(x,24,z,x+2,26,z+1,CARVED)
for z in range(-40,284,7):
 for x in [-176,240]:box(x,24,z,x+1,26,z+2,CARVED)
for x in [-173,-64,64,238]:
 for z in [-45,284]:tower(x,z,39)
for z in [36,96,144,216]:tower(238,z,42 if z in[96,144] else 35)
for z in [32,120,208]:tower(-173,z,36)
for x in range(-160,232,24):
 for z in [-43,282]:box(x,4,z,x+2,21,z,CUT);box(x+1,7,z,x+1,17,z,GREEN)

# Palace is now225 x157, three full tiers, two throne galleries and a monumental dome.
tiers=[(-130,42,225,157,38,4),(-105,59,175,123,27,43),(-76,78,117,85,20,71)]
for x,z,w,d,h,y in tiers:
 room(x,z,w,d,h,y)
 top=y+h;x1=x+w-1;z1=z+d-1
 # Projecting cornices on all four sides, rhythmic pillars and recessed glazing.
 for yy,reach,mat in [(top-2,1,CARVED),(top-1,2,GOLD),(top,3,GREEN)]:
  box(x-reach,yy,z-reach,x1+reach,yy,z,mat);box(x-reach,yy,z1,x1+reach,yy,z1+reach,mat)
  box(x-reach,yy,z,x,yy,z1,mat);box(x1,yy,z,x1+reach,yy,z1,mat)
 for zz in range(z+3,z1-1,12):
  if y==4 and 107<=zz<=133:continue
  box(x1+1,y,zz,x1+4,top-3,zz+2,CUT);box(x1+4,y+3,zz+1,x1+4,top-5,zz+1,GREEN)
  box(x1,y+8,zz+5,x1,min(y+17,top-4),zz+7,GLASS)
 for xx in range(x+5,x1-2,12):
  for zz in [z-1,z1+1]:
   box(xx,y,zz,xx+2,top-3,zz,CUT);box(xx+1,y+3,zz,xx+1,top-5,zz,GREEN)
 for xx in range(x+4,x1-2,5):
  for zz in [z,z1]:box(xx,top+1,zz,xx,top+2,zz,CARVED)
for cx,cz in [(-137,35),(-137,205),(102,35),(102,205)]:tower(cx,cz,66,7)
dome(-18,92,120,30,GOLD)
for y,r in [(94,29),(100,28),(108,24),(114,19)]:ring(-18,y,120,r,GREEN)
box(-18,123,120,-18,129,120,COPPER)
# Ground-floor audience hall columns and floor mosaics.
for x in [-107,-68,-29,10,49]:
 for z in [73,167]:
  box(x-2,4,z-2,x+2,6,z+2,CARVED);box(x-1,7,z-1,x+1,37,z+1,CUT);box(x-3,38,z-3,x+3,40,z+3,GREEN)
for x in range(-115,84,20):
 for dx in range(-5,6):
  for dz in range(-5,6):
   if abs(dx)+abs(dz)<=5:box(x+dx,3,120+dz,x+dx,3,120+dz,GREEN if abs(dx)+abs(dz)<4 else GOLD)
# East entrance: forty-block-wide portico and a29-block clear arch.
box(92,4,108,100,26,132,A)
for z in range(99,142):
 d=abs(z-120);y=17+int(math.sqrt(max(0,21*21-d*d))*.6)
 box(97,y,z,111,y+2,z,CARVED)
 if d>=18:box(97,4,z,111,y,z,CUT)
box(97,32,99,111,33,141,GREEN);box(96,34,98,112,34,142,GOLD)
# Two connected staircases to the galleries, represented by ordinary blocks.
box(-126,41,52,-85,42,60,A)
for i in range(39):box(-128+i,4+i,55,-128+i,4+i,58,SM)
box(-94,43,58,-86,46,61,A)
box(-94,42,59,-86,42,63,SM)
box(-99,69,71,-69,70,76,A)
for i in range(28):box(-101+i,43+i,72,-101+i,43+i,75,SM)
box(-77,71,77,-70,75,80,A)
box(-78,70,75,-70,70,81,SM)

# Atreides hawk follows the polygon in DuneGit's faction-atreides.svg, as RED CONCRETE blocks.
wing=[(10,22),(25,25),(32,33),(39,25),(54,22),(44,36),(35,41),(32,54),(29,41),(20,36)]
head=[(28,18),(32,11),(36,18),(32,23)]
def hawk_mask(size):
 im=Image.new('1',(size,size));d=ImageDraw.Draw(im)
 for poly in [wing,head]:d.polygon([(round(x/64*(size-1)),round(y/64*(size-1)))for x,y in poly],fill=1)
 return im
def crest(x,bottom,cz,size):
 half=size//2;box(x-1,bottom-2,cz-half-2,x,bottom+size+1,cz+half+2,GOLD)
 box(x+1,bottom-1,cz-half-1,x+1,bottom+size,cz+half+1,DARK)
 box(x+2,bottom,cz-half,x+2,bottom+size-1,cz+half,GREEN)
 mask=hawk_mask(size)
 for row in range(size):
  for col in range(size):
   if mask.getpixel((col,row)):box(x+3,bottom+size-1-row,cz-half+col,x+3,bottom+size-1-row,cz-half+col,HAWK)
crest(72,42,120,31)

# Royal north/south quarters: sixteen more individual houses, total28 homes.
for row,z in enumerate([-25,236]):
 for col,x in enumerate([-145,-99,-53,-7,39,85,131,177]):
  h=13+(col%3)*3;room(x,z,31,27,h,roof=(row+col)%2==0);top=4+h
  for xx in [x+1,x+8,x+23,x+29]:
   for zz in [z-1,z+27]:box(xx,4,zz,xx,top,zz,CARVED)
  if (row+col)%2:
   room(x+5,z+7,14,13,6,top+1,True)
   for xx in [x+22,x+27]:box(xx,top+1,z+4,xx,top+5,z+4,WOOD);box(xx,top+1,z+21,xx,top+5,z+21,WOOD)
   for zz in range(z+3,z+23,3):box(x+21,top+6,zz,x+28,top+6,zz,PLANK)
  for zz in [z-2,z+28]:box(x+3,4,zz,x+9,4,zz,PLANK)
# Public north/south promenades and a western service street: floor only, no gates across lanes.
for z in [14,220]:
 box(-162,3,z-4,231,3,z+4,SM)
 for x in range(-156,232,18):
  for zz in [z-5,z+5]:box(x,4,zz,x,8,zz,CARVED);box(x,9,zz,x,9,zz,LIGHT)
box(-161,3,-35,-155,3,273,SM)
box(124,3,-35,130,3,273,SM)
box(95,3,109,409,3,131,SM)
for z in [109,131]:box(95,3,z,409,3,z,GOLD)
# Portico paving re-established after avenue pass; east gate is now an Atreides gatehouse.
box(237,4,106,245,25,134,A)
for z in range(102,139):
 d=abs(z-120);y=11+int(math.sqrt(max(0,18*18-d*d))*.7)
 box(242,y,z,248,y+2,z,CARVED)
 if d>=15:box(242,4,z,248,y,z,CUT)
crest(244,27,120,17)
for z in [102,138]:box(243,26,z,246,46,z,GREEN);box(243,47,z,246,47,z,GOLD)
disk(294,3,120,7,GOLD);disk(294,3,120,5,GREEN)

# Exact reachability using2-air-block clearance and one-block steps. No live world touched.
def validate():
 start=(294+OX,4,120+OZ);a=np.frombuffer(m.blocks,dtype=np.uint8).reshape(H,L,W)
 def walk(x,y,z):return 0<x<W-1 and 1<=y<H-1 and 0<z<L-1 and a[y-1,z,x]!=A and a[y,z,x]==A and a[y+1,z,x]==A
 assert walk(*start),'Spawn blocked'
 q=deque([start]);seen={start}
 while q:
  x,y,z=q.popleft()
  for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]:
   for dy in [0,1,-1]:
    p=(x+dx,y+dy,z+dz)
    if p not in seen and walk(*p):seen.add(p);q.append(p);break
 targets={'spawn':(294,4,120),'palace-hall':(60,4,120),'gallery-one':(-80,43,70),'gallery-two':(-45,71,90),'east-gate':(239,4,120),'market':(160,4,120),'terminal':(270,4,120),'frigate':(316,6,65),'north-promenade':(-120,4,14),'south-promenade':(-120,4,220)}
 for row,z in enumerate([-25,236]):
  for col,x in enumerate([-145,-99,-53,-7,39,85,131,177]):targets[f'new-home-{row}-{col}']=(x+15,4,z+12)
 for row,z in enumerate([32,69,150,187]):
  for col,x in enumerate([114,155,196]):targets[f'existing-home-{row}-{col}']=(x+14,4,z+12)
 for x,z in [(316,65),(374,65),(316,183),(374,183)]:targets[f'apron-{x}-{z}']=(x+18,4,z)
 result={k:(x+OX,y,z+OZ)in seen for k,(x,y,z)in targets.items()}
 if not all(result.values()):
  print(str({'debugMaxReachableY':max(p[1]for p in seen),'stairs1':[(i,walk(-128+i+OX,5+i,56+OZ),(-128+i+OX,5+i,56+OZ)in seen)for i in range(39)],'stairs2':[(i,walk(-101+i+OX,44+i,73+OZ),(-101+i+OX,44+i,73+OZ)in seen)for i in range(28)]}),flush=True)
 assert all(result.values()),{k:v for k,v in result.items()if not v}
 return {'reachable':result,'targetPositions':{k:[x+OX,y,z+OZ]for k,(x,y,z)in targets.items()},'walkablePositions':len(seen),'dimensions':[W,H,L],'spawnLocal':start,'originWorld':[-486,68,-184],'spawnWorld':[0.5,72,0.5],'cityFootprint':[418,336],'oldCityFootprint':[226,224],'palaceFootprint':[225,157],'oldPalaceFootprint':[65,88],'homes':28,'paletteCounts':dict(Counter(m.NAMES[v]for v in m.blocks)),'status':'Offline grand design, not installed','atreidesSource':'website/web/assets/faction-atreides.svg / DuneGit'}
report=validate();data=bytes(m.blocks);schem=m.schematic();(OUT/'Arrakeen-Grand-Atreides-Design.schem').write_bytes(schem);report['schematicSha256']=hashlib.sha256(schem).hexdigest()
positions=list(report['targetPositions'].values());raw=struct.pack('>iiii',0x44524131,W,H,L)+data+struct.pack('>i',len(positions))+b''.join(struct.pack('>iii',*p)for p in positions);runtime=gzip.compress(raw,mtime=0);(OUT/'grand-plan.bin.gz').write_bytes(runtime);report['runtimePlanSha256']=hashlib.sha256(runtime).hexdigest();(OUT/'grand-design-validation.json').write_text(json.dumps(report,indent=2),encoding='utf8')
print(json.dumps({'phase':'grand model built','dimensions':report['dimensions'],'homes':28,'reachable':len(report['reachable'])}),flush=True)

if args.no_render:
 (OUT/'grand-design-blocks.bin').write_bytes(data);raise SystemExit(0)

# Reuse only the audited renderer functions, without executing the earlier design builder.
tree=ast.parse((DESIGN/'block-renderer.py').read_text(encoding='utf8'));functions=[n for n in tree.body if isinstance(n,ast.FunctionDef)and n.name in ['texture','tile','render']];exec(compile(ast.Module(body=functions,type_ignores=[]),'renderer-functions','exec'))
client=args.client_jar
if client is None:raise SystemExit('Use --client-jar with your own Minecraft1.21.8 client, or --no-render for block artifacts only.')
jar=zipfile.ZipFile(client);texture_meta={};texture_alias={'smooth_sandstone':'sandstone_top','waxed_cut_copper':'cut_copper'}
textures={(i,f):texture(n,f)for i,n in enumerate(m.NAMES)if i for f in ['top','east','south']}
render(data,'06-grand-arrakeen-atreides.png',s=4,label='GRAND ATREIDES DESIGN / 28 HOMES / 418 x336 CITY / BUILDABLE BLOCKS')
render(data,'07-grand-palace-detail.png',bounds=(OX-147,OZ+25,OX+114,OZ+216),s=8,label='GRAND PALACE / 225 x157 BLOCKS / ATREIDES HAWK MOSAIC')
render(data,'08-grand-gate-and-city.png',bounds=(OX+104,OZ+23,OX+252,OZ+230),s=10,label='ATREIDES GATE / CITY QUARTERS / ACTUAL SCHEMATIC BLOCKS')

# Full top-down block plan with existing Arrakis world coordinates and a32-block grid.
a=np.frombuffer(data,dtype=np.uint8).reshape(H,L,W);highest=H-1-np.argmax((a!=0)[::-1],axis=0);zz,xx=np.indices((L,W));ids=a[highest,zz,xx]
means=np.array([[16,43,37]]+[np.asarray(textures[i,'top'])[:,:,:3].mean(axis=(0,1)).astype(int).tolist()for i in range(1,len(m.NAMES))],dtype=np.uint8)
top=Image.fromarray(means[ids]).resize((W*4,L*4),Image.Resampling.NEAREST);board=Image.new('RGB',(W*4+140,L*4+180),'#102b25');board.paste(top,(70,75));d=ImageDraw.Draw(board);f=ImageFont.truetype(FONT_SANS,20);tf=ImageFont.truetype(FONT_SERIF,34)
d.text((70,20),'GRAND ARRAKEEN / ATREIDES / INGAME BUILD PLAN',font=tf,fill='#e4ca86')
for x in range(0,W,32):d.line((70+x*4,75,70+x*4,75+L*4),fill='#8e8560');d.text((70+x*4,53),str(x-486),font=f,fill='#e4ca86')
for z in range(0,L,32):d.line((70,75+z*4,70+W*4,75+z*4),fill='#8e8560');d.text((7,75+z*4),str(z-184),font=f,fill='#e4ca86')
for name,x,z in [('PALACE',-18,120),('MARKET',166,120),('GATE',240,120),('SPAWN',294,120),('PORT',374,183),('NORTH QUARTER',-95,-13),('SOUTH QUARTER',-95,249)]:
 text=f'{name}: {x-294}, {z-120}';px=70+(x+OX)*4;py=75+(z+OZ)*4;b=d.textbbox((0,0),text,font=f);d.rectangle((px-5,py-5,px+b[2]+5,py+b[3]+5),fill='#102b25',outline='#ddbf79',width=2);d.text((px,py),text,font=f,fill='#efd89b')
d.text((70,L*4+100),'World X / Z shown. Grid32blocks. Feet at ground floor Y72. Palace gallery feet Y111 and139.',font=f,fill='#e4d8b8');d.text((70,L*4+134),'Offline proposal. Spawn stays (0.5,72,0.5); full schematic origin (-486,68,-184).',font=f,fill='#e4d8b8');board.save(OUT/'09-grand-build-plan.png')
# Pixel-by-pixel crest reference: each square is one real block.
mask=hawk_mask(31);crest_img=Image.new('RGB',(31,31),'#1d5148');cd=ImageDraw.Draw(crest_img)
for y in range(31):
 for x in range(31):
  if mask.getpixel((x,y)):cd.point((x,y),fill='#a72b25')
crest_img=crest_img.resize((620,620),Image.Resampling.NEAREST);cd=ImageDraw.Draw(crest_img)
for p in range(0,621,20):cd.line((p,0,p,620),fill='#65704e');cd.line((0,p,620,p),fill='#65704e')
crest_img.save(OUT/'10-atreides-hawk-block-grid.png')
report['textures']={'version':'Minecraft1.21.8 existing client','jarSha256':hashlib.sha256(client.read_bytes()).hexdigest(),'files':texture_meta};(OUT/'grand-design-validation.json').write_text(json.dumps(report,indent=2),encoding='utf8');(OUT/'grand-design-blocks.bin').write_bytes(data)
print(json.dumps({'phase':'grand complete','sha256':report['schematicSha256'],'cityAreaFactor':418*336/(226*224),'palaceAreaFactor':225*157/(65*88)}),flush=True)

