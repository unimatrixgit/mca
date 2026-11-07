"""Actual-cube software renderer; used by build-grand-design.py with its explicit texture/context globals."""

def texture(name,face):
 n=texture_alias.get(name,name)
 if face=='top':n={'sandstone':'sandstone_top','cut_sandstone':'sandstone_top','chiseled_sandstone':'sandstone_top','red_sandstone':'red_sandstone_top','stripped_dark_oak_log':'stripped_dark_oak_log_top'}.get(name,n)
 if face!='top' and name=='sandstone':n='sandstone'
 key='assets/minecraft/textures/block/'+n+'.png'
 data=jar.read(key);texture_meta[key]=hashlib.sha256(data).hexdigest()
 img=Image.open(io.BytesIO(data)).convert('RGBA');return img.crop((0,0,16,16))

def tile(mat,face,s):
 tex=textures[mat,face];k=16/s
 if face=='top':coef=(k/2,k,-8,-k/2,k,8);poly=[(s,0),(2*s,s//2),(s,s),(0,s//2)];shade=1.03
 elif face=='east':coef=(-k,0,32,k/2,k,-24);poly=[(2*s,s//2),(s,s),(s,2*s),(2*s,3*s//2)];shade=.82
 else:coef=(k,0,0,-k/2,k,-8);poly=[(0,s//2),(s,s),(s,2*s),(0,3*s//2)];shade=.65
 img=tex.transform((2*s+1,2*s+1),Image.Transform.AFFINE,coef,Image.Resampling.NEAREST)
 mask=Image.new('L',img.size);ImageDraw.Draw(mask).polygon(poly,fill=255)
 img=ImageEnhance.Brightness(img).enhance(shade);img.putalpha(mask);return img

def render(data,name,bounds=None,s=6,label='BLOCK DESIGN V2'):
 arr=np.frombuffer(data,dtype=np.uint8).reshape(H,L,W)
 if bounds:
  x0,z0,x1,z1=bounds;arr=arr[:,z0:z1,x0:x1]
 hh,ll,ww=arr.shape;faces=[];solid=arr!=0
 for face,axis in [('top',0),('east',2),('south',1)]:
  neighbor=np.zeros_like(arr)
  a=[slice(None)]*3;b=a.copy();a[axis]=slice(None,-1);b[axis]=slice(1,None);neighbor[tuple(a)]=arr[tuple(b)]
  yy,zz,xx=np.where(solid&(neighbor==0));faces.append((face,yy,zz,xx,arr[yy,zz,xx]))
 # Draw each actual exposed cube in view-depth order, not just a height map.
 voxels={}
 for face,ys,zs,xs,mats in faces:
  for y,z,x,mat in zip(ys.tolist(),zs.tolist(),xs.tolist(),mats.tolist()):voxels.setdefault((x,y,z),[mat,[]])[1].append(face)
 keys=sorted(voxels,key=lambda p:sum(p))
 width=(ww+ll)*s+100;height=(ww+ll)*s//2+hh*s+160
 canvas=Image.new('RGB',(width,height),'#102b25');ox=ll*s+50;oy=hh*s+65
 tiles={(mat,face):tile(mat,face,s)for mat in range(1,len(m.NAMES))for face in ['east','south','top']}
 for x,y,z in keys:
  mat,shown=voxels[(x,y,z)];px=ox+(x-z)*s-s;py=oy+(x+z)*s//2-y*s-s
  for face in ['east','south','top']:
   if face in shown:im=tiles[mat,face];canvas.paste(im,(px,py),im)
 extent=ImageChops.difference(canvas,Image.new('RGB',canvas.size,'#102b25')).getbbox()
 cropped=canvas.crop((0,max(0,extent[1]-20),width,min(height,extent[3]+25)))
 canvas=Image.new('RGB',(width,cropped.height+160),'#102b25');canvas.paste(cropped,(0,85));height=canvas.height
 d=ImageDraw.Draw(canvas);font=ImageFont.truetype(FONT_SANS,max(18,width//100));title=ImageFont.truetype(FONT_SERIF,max(28,width//65))
 d.text((42,20),'DUNEREALMS  /  ARRAKEEN + STARPORT',font=title,fill='#e4ca86')
 d.text((42,height-65),label+'  |  Actual Minecraft block geometry + client textures  |  Offline proposal',font=font,fill='#e4d8b8')
 # Trim empty space around block geometry while preserving the designed label/header.
 canvas.save(OUT/name)
 print(json.dumps({'phase':'rendered','file':name,'faces':sum(len(v[1])for v in voxels.values()),'size':canvas.size}),flush=True)
 return canvas
