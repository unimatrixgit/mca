from pathlib import Path
import zipfile,hashlib,subprocess,copy,json,re
root=Path(__file__).resolve().parents[2];work=Path(__file__).parent;build=work/'build';build.mkdir(exist_ok=True)
baseline=root/'output/atelier-menu-20261029/release/DuneCore-1.0.27-atelier-menu.jar'
assert hashlib.sha256(baseline.read_bytes()).hexdigest()=='1aba67f491fe470469465470b599055100f64eb5c6866cf375359663673fd327'
jvm=root/'.tools/jdk-21.0.12.1+1/bin';gradle=root/'.tools/gradle-8.14/lib';asm=';'.join(str(gradle/name) for name in ('asm-9.7.1.jar','asm-tree-9.7.1.jar'))
subprocess.run([str(jvm/'javac.exe'),'-cp',asm,'-d',str(build),str(work/'StaffRoutePatch.java')],check=True)
cache=root/'.tools/gradle-cache/caches/modules-2/files-2.1'
dependencies=[]
for group,name,version in [('io.papermc.paper','paper-api','1.21.4-R0.1-SNAPSHOT'),('net.kyori','adventure-api','4.20.0'),('org.jetbrains','annotations','26.0.2')]:
 paths=list((cache/group/name/version).glob('*/*.jar'))
 if paths:dependencies.append(str(paths[0]))
source=root/'dunerealms-story-update/DuneRealms-minecraft-only/src/main/java/com/example/dunerealms/StaffMenuBridge.java'
subprocess.run([str(jvm/'javac.exe'),'--release','21','-cp',';'.join(dependencies),'-d',str(build),str(source)],check=True)
with zipfile.ZipFile(baseline) as old:
 name='com/example/dunerealms/ConfigurableGui.class';before=build/'before.class';after=build/'after.class';before.write_bytes(old.read(name))
 subprocess.run([str(jvm/'java.exe'),'-cp',str(build)+';'+asm,'StaffRoutePatch',str(before),str(after)],check=True)
 descriptor,n=re.subn(rb'(version: *)1\.0\.27',rb'\g<1>1.0.28',old.read('plugin.yml'),count=1);assert n==1
 helper='com/example/dunerealms/StaffMenuBridge.class';replacement={name:after.read_bytes(),'plugin.yml':descriptor,helper:(build/helper).read_bytes()}
 target=work/'DuneCore-1.0.28-alpha-final.jar'
 with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as out:
  for info in old.infolist():out.writestr(copy.copy(info),replacement.get(info.filename,old.read(info.filename)))
  out.writestr(helper,replacement[helper])
 with zipfile.ZipFile(target) as out:
  changed=[name for name in old.namelist() if old.read(name)!=out.read(name)]
  assert set(changed)=={'plugin.yml','com/example/dunerealms/ConfigurableGui.class'}
  assert set(out.namelist())-set(old.namelist())=={helper}
 manifest={'file':str(target),'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'changedEntries':changed,'added':[helper],'unchangedEntries':len(old.namelist())-2,'baselineSha256':hashlib.sha256(baseline.read_bytes()).hexdigest()}
 (work/'manifest.json').write_text(json.dumps(manifest,indent=2));print(json.dumps(manifest))
