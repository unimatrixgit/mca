"""Build a review artifact from a pinned installed JAR. Never installs or restarts anything."""
import argparse,copy,hashlib,json,pathlib,re,subprocess,zipfile
p=argparse.ArgumentParser();p.add_argument('--baseline',required=True,type=pathlib.Path);p.add_argument('--baseline-sha256',required=True);p.add_argument('--renderer',required=True,type=pathlib.Path);p.add_argument('--renderer-sha256',required=True);p.add_argument('--out',required=True,type=pathlib.Path);p.add_argument('--gradle',required=True);args=p.parse_args()
root=pathlib.Path(__file__).resolve().parent;baseline=args.baseline.resolve();out=args.out.resolve()
def require(condition,message):
 if not condition:raise RuntimeError(message)
require(re.fullmatch('[a-f0-9]{64}',args.baseline_sha256),'Explicit pinned SHA256 required')
require(hashlib.sha256(baseline.read_bytes()).hexdigest()==args.baseline_sha256,'Installed baseline changed')
renderer=args.renderer.resolve();require(re.fullmatch('[a-f0-9]{64}',args.renderer_sha256)and hashlib.sha256(renderer.read_bytes()).hexdigest()==args.renderer_sha256,'Installed renderer changed')
require(out!=baseline.parent and 'plugins' not in [s.lower()for s in out.parts],'Review output cannot be a plugin directory')
out.mkdir(parents=True,exist_ok=True)
require(baseline!=out/'DuneCore-case323-candidate.jar','Review output cannot overwrite the baseline')
def gradle(*tasks):subprocess.run([args.gradle,'--no-daemon','-p',str(root),*tasks],check=True)
gradle('test','-PqaMenusJar='+str(renderer))
core='com/example/dunerealms/DuneCore.class';gui='com/example/dunerealms/ConfigurableGui.class';helper='com/example/dunerealms/StaffMenuBridge.class';classes=root/'build/classes/java/main'
with zipfile.ZipFile(baseline)as old:
 require(len(old.namelist())==len(set(old.namelist())),'Duplicate input JAR entries')
 require(helper not in old.namelist(),'Staff bridge already installed: reconcile instead of replaying this patch')
 beforeCore=out/'before-Core.class';afterCore=out/'after-Core.class';beforeGui=out/'before-Gui.class';afterGui=out/'after-Gui.class'
 beforeCore.write_bytes(old.read(core));beforeGui.write_bytes(old.read(gui))
 for value in [beforeCore,afterCore,beforeGui,afterGui,classes/core]:require('|' not in str(value),'Path contains patch argument separator')
 gradle('mergeFacade','-PpatchArgs='+str(beforeCore)+'|'+str(classes/core)+'|'+str(afterCore))
 gradle('patchStaffRoute','-PpatchArgs='+str(beforeGui)+'|'+str(afterGui))
 descriptor=old.read('plugin.yml');version=re.search(rb'(?m)^version: *([^\r\n]+)',descriptor);require(version,'Native descriptor version required')
 require(b'case323' not in version[1],'Already patched descriptor')
 descriptor=descriptor[:version.start(1)]+version[1]+b'-case323'+descriptor[version.end(1):]
 replacement={core:afterCore.read_bytes(),gui:afterGui.read_bytes(),'plugin.yml':descriptor}
 candidate=out/'DuneCore-case323-candidate.jar'
 with zipfile.ZipFile(candidate,'w',zipfile.ZIP_DEFLATED)as result:
  for info in old.infolist():result.writestr(copy.copy(info),replacement.get(info.filename,old.read(info.filename)))
  helperInfo=copy.copy(old.getinfo(core));helperInfo.filename=helper;helperInfo.orig_filename=helper
  result.writestr(helperInfo,(classes/helper).read_bytes())
 with zipfile.ZipFile(candidate)as result:
  changed=sorted(name for name in old.namelist()if old.read(name)!=result.read(name));added=sorted(set(result.namelist())-set(old.namelist()));missing=sorted(set(old.namelist())-set(result.namelist()))
  require(changed==sorted(replacement)and added==[helper]and not missing,'Unexpected artifact changes')
  require(b'Donor checkpoint stub must never be merged' not in result.read(core),'Compilation stub leaked into Core')
  solar='com/example/dunerealms/SunWearPolicy.class';solarPreserved=solar in old.namelist()and old.read(solar)==result.read(solar)
 manifest={'baselineSha256':args.baseline_sha256,'rendererSha256':args.renderer_sha256,'candidateSha256':hashlib.sha256(candidate.read_bytes()).hexdigest(),'changedEntries':changed,'addedEntries':added,'missingEntries':missing,'unchangedEntries':len(old.namelist())-len(changed),'sunWearPolicyPreserved':solarPreserved,'sourceCoreSha256':hashlib.sha256((root/'../../DuneRealms-minecraft-only/src/main/java/com/example/dunerealms/DuneCore.java').read_bytes()).hexdigest(),'deployed':False,'actualArtifactTestsPassed':False}
 (out/'manifest.json').write_text(json.dumps(manifest,indent=2))
gradle('test','-PqaCoreJar='+str(candidate),'-PqaMenusJar='+str(renderer),'--rerun-tasks')
manifest['actualArtifactTestsPassed']=True;(out/'manifest.json').write_text(json.dumps(manifest,indent=2));print(json.dumps(manifest,indent=2))
