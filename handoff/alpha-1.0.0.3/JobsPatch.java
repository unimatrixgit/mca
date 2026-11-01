import java.nio.file.*;import java.util.*;import java.util.jar.*;import java.util.zip.*;import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
public class JobsPatch {
 public static void main(String[] args)throws Exception{
  try(JarFile old=new JarFile(args[0])){String target="com/example/dunerealms/ConfigurableGui.class";ClassNode c=new ClassNode();new ClassReader(old.getInputStream(old.getJarEntry(target))).accept(c,0);int edits=0;
   for(MethodNode m:c.methods)if(m.name.equals("openServerJobs")&&m.desc.equals("(Lorg/bukkit/entity/Player;)V")){
    m.instructions.clear();m.tryCatchBlocks.clear();m.localVariables=null;var n=m.instructions;
    n.add(new VarInsnNode(Opcodes.ALOAD,0));n.add(new FieldInsnNode(Opcodes.GETFIELD,c.name,"plugin","Lcom/example/dunerealms/DuneCore;"));n.add(new VarInsnNode(Opcodes.ALOAD,0));n.add(new FieldInsnNode(Opcodes.GETFIELD,c.name,"serverJobs","Lcom/example/dunerealms/ServerJobs;"));n.add(new VarInsnNode(Opcodes.ALOAD,0));n.add(new VarInsnNode(Opcodes.ALOAD,1));n.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"com/example/dunerealms/JobsScreens","open","(Lcom/example/dunerealms/DuneCore;Lcom/example/dunerealms/ServerJobs;Lcom/example/dunerealms/ConfigurableGui;Lorg/bukkit/entity/Player;)V",false));n.add(new InsnNode(Opcodes.RETURN));m.maxLocals=2;m.maxStack=4;edits++;
   }
   if(edits!=1)throw new IllegalStateException("Expected single jobs entry point");ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);
   try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))){
    for(var e:Collections.list(old.entries())){out.putNextEntry(new ZipEntry(e.getName()));byte[] data=old.getInputStream(e).readAllBytes();if(e.getName().equals(target))data=w.toByteArray();if(e.getName().equals("plugin.yml"))data=new String(data,java.nio.charset.StandardCharsets.UTF_8).replace("version: 1.0.27-layout1","version: 1.0.27-layout1-jobs1").getBytes(java.nio.charset.StandardCharsets.UTF_8);out.write(data);out.closeEntry();}
    out.putNextEntry(new ZipEntry("com/example/dunerealms/JobsScreens.class"));out.write(Files.readAllBytes(Path.of(args[2])));out.closeEntry();
   }
  }System.out.println("One jobs entry point replaced; retained original inventory and contract engine.");
 }
}
