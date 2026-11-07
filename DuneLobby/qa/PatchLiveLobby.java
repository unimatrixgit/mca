import java.nio.file.*;
import java.util.jar.*;
import org.objectweb.asm.*;

/** Preserve every installed entry except the single handler and the new guard. */
public class PatchLiveLobby {
 public static void main(String[] args) throws Exception {
  try(var input=new JarFile(args[0]);var out=new JarOutputStream(Files.newOutputStream(Path.of(args[2])))) {
   var entries=input.entries();int patched=0;
   while(entries.hasMoreElements()) {
    var entry=entries.nextElement();byte[] bytes=input.getInputStream(entry).readAllBytes();
    if(entry.getName().equals("com/example/dunelobby/DuneLobby.class")) {
     var reader=new ClassReader(bytes);var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
     reader.accept(new ClassVisitor(Opcodes.ASM9,writer) {
      public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions) {
       var delegate=super.visitMethod(access,name,desc,signature,exceptions);
       if(!name.equals("interact")||!desc.equals("(Lorg/bukkit/event/player/PlayerInteractEvent;)V"))return delegate;
       return new MethodVisitor(Opcodes.ASM9,delegate) {
        public void visitCode(){super.visitCode();visitVarInsn(Opcodes.ALOAD,1);
         visitMethodInsn(Opcodes.INVOKESTATIC,"com/example/dunelobby/LobbyBuildAccess","allows","(Lorg/bukkit/event/player/PlayerInteractEvent;)Z",false);
         var original=new Label();visitJumpInsn(Opcodes.IFEQ,original);visitInsn(Opcodes.RETURN);visitLabel(original);
         visitFrame(Opcodes.F_SAME,0,null,0,null);
        }
       };
      }
     },0);bytes=writer.toByteArray();patched++;
    }
    out.putNextEntry(new JarEntry(entry.getName()));out.write(bytes);out.closeEntry();
   }
   if(patched!=1)throw new IllegalStateException("Expected one installed plugin class");
   out.putNextEntry(new JarEntry("com/example/dunelobby/LobbyBuildAccess.class"));out.write(Files.readAllBytes(Path.of(args[1])));out.closeEntry();
  }
 }
}
