import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public class StaffRoutePatch {
 static byte[] canonical(MethodNode m){ClassWriter w=new ClassWriter(0);w.visit(65,Opcodes.ACC_PUBLIC,"Check",null,"java/lang/Object",null);m.accept(w.visitMethod(m.access,m.name,m.desc,m.signature,m.exceptions.toArray(String[]::new)));w.visitEnd();return w.toByteArray();}
 public static void main(String[] args)throws Exception{
  if(args.length!=2)throw new IllegalArgumentException("before.class after.class required");
  ClassNode cls=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(cls,0);
  if(!cls.name.equals("com/example/dunerealms/ConfigurableGui"))throw new IllegalStateException("Wrong GUI class owner");
  Map<String,byte[]> original=new HashMap<>();for(MethodNode m:cls.methods)original.put(m.name+m.desc,canonical(m));
  int guard=0,route=0;Set<String> changed=new HashSet<>();
  for(MethodNode m:cls.methods)for(AbstractInsnNode node:m.instructions.toArray()){
   if(node instanceof MethodInsnNode call&&call.owner.equals("com/example/dunerealms/AdminGui")&&call.name.equals("canUse")&&call.desc.equals("(Lorg/bukkit/entity/Player;)Z")){
    if(!Set.of("open","execute").contains(m.name))throw new IllegalStateException("Unexpected guard target "+m.name);
    call.owner="com/example/dunerealms/StaffMenuBridge";call.name="available";guard++;changed.add(m.name);
   }
   if(node instanceof LdcInsnNode literal&&"dune admin".equals(literal.cst)){
    if(!m.name.equals("execute"))throw new IllegalStateException("Unexpected admin route");literal.cst="dunemenus:dunemenus staff";route++;changed.add(m.name);
   }
  }
  if(guard!=2||route!=1||!changed.equals(Set.of("open","execute")))throw new IllegalStateException("Unexpected patch counts "+guard+"/"+route);
  ClassWriter writer=new ClassWriter(0);cls.accept(writer);byte[] bytes=writer.toByteArray();
  ClassNode after=new ClassNode();new ClassReader(bytes).accept(after,0);int preserved=0;
  for(MethodNode m:after.methods)if(!changed.contains(m.name)){if(!Arrays.equals(original.get(m.name+m.desc),canonical(m)))throw new IllegalStateException("Unrelated method changed "+m.name);preserved++;}
  Files.write(Path.of(args[1]),bytes);System.out.println("Staff route patched; "+preserved+" unrelated methods preserved.");
 }
}
