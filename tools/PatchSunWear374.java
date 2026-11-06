import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Overlay only ticket374 methods onto the exact installed Core artifact.
 * Usage: original.jar compiled-main-directory output.jar
 * Refuses any original other than the recorded Alpha3 release. */
public final class PatchSunWear374 {
 public static void main(String[] args) throws Exception {
  Path original=Path.of(args[0]), classes=Path.of(args[1]), output=Path.of(args[2]);
  String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(original)));
  if(!hash.equals("cb2f4c21846c88bf0e11d2cf099c849357d4cbffe9b0ceb4e6b4b6c81236561d")) throw new IllegalStateException("Unexpected installed Core: "+hash);
  String entry="com/example/dunerealms/DuneCore.class";
  try(JarFile jar=new JarFile(original.toFile())) {
   ClassNode live=new ClassNode(), candidate=new ClassNode();
   new ClassReader(jar.getInputStream(jar.getJarEntry(entry))).accept(live,0);
   new ClassReader(Files.readAllBytes(classes.resolve(entry))).accept(candidate,0);
   if(live.fields.stream().noneMatch(f->f.name.equals("sunExposureSeconds")&&f.desc.equals("Ljava/util/Map;"))) throw new IllegalStateException("Exposure state missing");
   Set<String> replace=new HashSet<>(List.of("checkArrakisSun","wearSunExposedStillsuit"));
   candidate.methods.stream().filter(m->m.name.startsWith("lambda$wearSunExposedStillsuit$")).forEach(m->replace.add(m.name));
   if(candidate.methods.stream().filter(m->replace.contains(m.name)).count()!=replace.size()) throw new IllegalStateException("Candidate methods missing");
   if(live.methods.stream().noneMatch(m->m.name.equals("checkArrakisSun"))||live.methods.stream().noneMatch(m->m.name.equals("onSunDamage"))) throw new IllegalStateException("Unexpected live solar implementation");
   live.methods.removeIf(m->replace.contains(m.name)||m.name.equals("onSunDamage"));
   candidate.methods.stream().filter(m->replace.contains(m.name)).forEach(live.methods::add);
   ClassWriter writer=new ClassWriter(0);live.accept(writer);
   try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(output))) {
    Enumeration<JarEntry> entries=jar.entries();
    while(entries.hasMoreElements()) {
     JarEntry e=entries.nextElement();out.putNextEntry(new JarEntry(e.getName()));
     byte[] bytes=jar.getInputStream(e).readAllBytes();
     if(e.getName().equals(entry)) bytes=writer.toByteArray();
     if(e.getName().equals("plugin.yml")) bytes=new String(bytes,java.nio.charset.StandardCharsets.UTF_8).replace("version: 1.0.27-layout1-jobs1","version: 1.0.27-layout1-jobs1-sun374").getBytes(java.nio.charset.StandardCharsets.UTF_8);
     out.write(bytes);out.closeEntry();
    }
    String policy="com/example/dunerealms/SunWearPolicy.class";
    if(jar.getJarEntry(policy)!=null) throw new IllegalStateException("Policy already installed");
    out.putNextEntry(new JarEntry(policy));out.write(Files.readAllBytes(classes.resolve(policy)));out.closeEntry();
   }
   try(JarFile patched=new JarFile(output.toFile())) {
    for(JarEntry e:Collections.list(jar.entries())) {
     if(!e.getName().equals(entry)&&!e.getName().equals("plugin.yml")&&
        !Arrays.equals(jar.getInputStream(e).readAllBytes(),patched.getInputStream(patched.getJarEntry(e.getName())).readAllBytes()))
      throw new IllegalStateException("Unrelated entry changed: "+e.getName());
    }
    ClassNode verified=new ClassNode();new ClassReader(patched.getInputStream(patched.getJarEntry(entry))).accept(verified,0);
    for(MethodNode method:live.methods) {
     if(replace.contains(method.name)) continue;
     MethodNode actual=verified.methods.stream().filter(m->m.name.equals(method.name)&&m.desc.equals(method.desc)).findFirst().orElseThrow();
     if(!Arrays.equals(methodBytes(method),methodBytes(actual))) throw new IllegalStateException("Unrelated method changed: "+method.name);
    }
   }
   System.out.println("Verified: only solar methods, new policy class and plugin version changed; all unrelated archive entries and methods preserved.");
  }
 }
 private static byte[] methodBytes(MethodNode method) {
  ClassWriter writer=new ClassWriter(0);writer.visit(Opcodes.V21,Opcodes.ACC_PUBLIC,"MethodCheck",null,"java/lang/Object",null);method.accept(writer);writer.visitEnd();return writer.toByteArray();
 }
}
