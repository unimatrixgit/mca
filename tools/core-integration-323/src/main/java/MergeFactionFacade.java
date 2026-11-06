import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;

/** Adds only the new public facade and its namespaced implementation lambda. Never copies a constructor or checkpoint stub. */
public final class MergeFactionFacade {
 static final String OWNER="com/example/dunerealms/DuneCore";
 static final String API="(Lorg/bukkit/entity/Player;Lorg/bukkit/entity/Player;Ljava/lang/String;Ljava/util/function/Consumer;)V";
 static final String TX="(Lorg/bukkit/entity/Player;Ljava/util/function/Consumer;)V";
 static ClassNode read(byte[] data){ClassNode n=new ClassNode();new ClassReader(data).accept(n,0);return n;}
 static byte[] methodBytes(MethodNode method){ClassWriter out=new ClassWriter(0);out.visit(Opcodes.V21,Opcodes.ACC_PUBLIC,OWNER,null,"java/lang/Object",null);method.accept(out);out.visitEnd();return out.toByteArray();}
 static Handle remap(Handle h,Map<String,String> names){return h.getOwner().equals(OWNER)&&names.containsKey(h.getName())?new Handle(h.getTag(),h.getOwner(),names.get(h.getName()),h.getDesc(),h.isInterface()):h;}
 public static void main(String[] args)throws Exception{
  if(args.length!=3)throw new IllegalArgumentException("before.class donor.class after.class required");
  ClassNode before=read(Files.readAllBytes(Path.of(args[0]))),donor=read(Files.readAllBytes(Path.of(args[1])));
  if(!before.name.equals(OWNER)||!donor.name.equals(OWNER))throw new IllegalStateException("Wrong class owner");
  if(before.fields.stream().noneMatch(f->f.name.equals("selected")&&f.desc.equals("Ljava/util/Map;")))throw new IllegalStateException("Authoritative faction map missing");
  if(before.methods.stream().noneMatch(m->m.name.equals("transactNetworkInventory")&&m.desc.equals(TX)&&(m.access&Opcodes.ACC_PUBLIC)!=0&&(m.access&Opcodes.ACC_STATIC)==0))throw new IllegalStateException("Authoritative public instance checkpoint missing");
  if(before.methods.stream().anyMatch(m->m.name.equals("adminAssignFaction")||m.name.startsWith("alpha$staffFaction$")))throw new IllegalStateException("Facade or reserved lambda already present");
  Map<String,byte[]> original=new LinkedHashMap<>();for(MethodNode m:before.methods)original.put(m.name+m.desc,methodBytes(m));
  List<MethodNode> added=new ArrayList<>();Map<String,String> names=new LinkedHashMap<>();
  for(MethodNode m:donor.methods)if(m.name.equals("adminAssignFaction")&&m.desc.equals(API)||m.name.startsWith("lambda$adminAssignFaction$")){added.add(m);if(m.name.startsWith("lambda$"))names.put(m.name,"alpha$staffFaction$"+names.size());}
  if(added.size()!=2||names.size()!=1)throw new IllegalStateException("Expected exactly public facade plus its own lambda");
  for(MethodNode m:added){
   if(names.containsKey(m.name))m.name=names.get(m.name);
   for(AbstractInsnNode i:m.instructions){
    if(i instanceof MethodInsnNode call&&call.owner.equals(OWNER)&&names.containsKey(call.name))call.name=names.get(call.name);
    if(i instanceof InvokeDynamicInsnNode indy)for(int n=0;n<indy.bsmArgs.length;n++)if(indy.bsmArgs[n] instanceof Handle h)indy.bsmArgs[n]=remap(h,names);
    if(i instanceof LdcInsnNode ldc&&ldc.cst instanceof Handle h)ldc.cst=remap(h,names);
   }
   before.methods.add(m);
  }
  ClassWriter writer=new ClassWriter(0);before.accept(writer);byte[] result=writer.toByteArray();ClassNode after=read(result);
  if(after.methods.size()!=original.size()+2)throw new IllegalStateException("Unexpected method count");
  for(MethodNode m:after.methods){byte[] old=original.get(m.name+m.desc);if(old!=null&&!Arrays.equals(old,methodBytes(m)))throw new IllegalStateException("Existing method changed: "+m.name+m.desc);}
  Path target=Path.of(args[2]);Files.createDirectories(target.toAbsolutePath().getParent());Files.write(target,result);
  System.out.println("Added adminAssignFaction + namespaced lambda; "+original.size()+" existing methods structurally byte-identical.");
 }
}
