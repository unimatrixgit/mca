from pathlib import Path
import zipfile,json,hashlib,shutil
root=Path('output/gui-design-20261101');old=Path('output/water-vial-pack-20261029/DuneRealms-Imperial-GUI-1.21.4-v4.zip');out=root/'DuneRealms-Imperial-GUI-1.21.8-v5.zip';repo=Path('dunerealms-story-update');added={}
with zipfile.ZipFile(old) as a:
 fontpath='assets/dunerealms/font/imperial_menus.json';font=json.loads(a.read(fontpath))
 for code,name,height in [(0xe220,'imperial-shop-v1.png',56),(0xe221,'imperial-jobs-v1.png',56),(0xe222,'imperial-portal.png',48),(0xe223,'faction-atreides-aerdrecht-heraldic.png',48),(0xe224,'imperial-portal.png',48)]:
  path='assets/dunerealms/textures/gui/'+name;data=(repo/'website/web/assets/gui'/name).read_bytes();added[path]=data
  font['providers'].append({'type':'bitmap','file':'dunerealms:gui/'+name,'height':height,'ascent':7,'chars':[chr(code)]})
 added[fontpath]=json.dumps(font,indent=2,ensure_ascii=False).encode('utf-8')
 with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as b:
  for n in a.namelist():b.writestr(n,added.pop(n,a.read(n)))
  for n,data in added.items():b.writestr(n,data)
with zipfile.ZipFile(old) as a,zipfile.ZipFile(out) as b:
 changed=[n for n in a.namelist() if a.read(n)!=b.read(n)];assert changed==[fontpath]
 for n in b.namelist():
  if n.endswith('.json') or n=='pack.mcmeta':json.loads(b.read(n))
 for provider in font['providers']:
  if provider['type']=='bitmap':assert 'assets/'+provider['file'].replace(':','/textures/') in b.namelist()
p=repo/'DuneResourcePack'/fontpath;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(font,indent=2,ensure_ascii=False),encoding='utf-8')
for name in ['imperial-shop-v1.png','imperial-jobs-v1.png']:shutil.copyfile(repo/'website/web/assets/gui'/name,repo/'DuneResourcePack/assets/dunerealms/textures/gui'/name)
manifest={'file':out.name,'sha1':hashlib.sha1(out.read_bytes()).hexdigest(),'sha256':hashlib.sha256(out.read_bytes()).hexdigest(),'baselineSha1':hashlib.sha1(old.read_bytes()).hexdigest(),'changedExistingEntries':changed,'allItemModelsPreserved':True}
(root/'pack-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8');print(json.dumps(manifest))
