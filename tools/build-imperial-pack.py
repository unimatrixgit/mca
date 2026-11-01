from pathlib import Path
import zipfile,hashlib,sys
root=Path(__file__).resolve().parents[1]/'DuneResourcePack'
out=Path(sys.argv[1]) if len(sys.argv)>1 else Path('build/DuneRealms-Imperial-GUI.zip')
out.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
 for p in sorted(root.rglob('*')):
  if p.is_file() and p.suffix.lower() in ['.png','.json','.mcmeta','.ogg','.txt']:
   info=zipfile.ZipInfo(p.relative_to(root).as_posix(),(2026,11,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,p.read_bytes())
print(out);print('SHA-1',hashlib.sha1(out.read_bytes()).hexdigest())
