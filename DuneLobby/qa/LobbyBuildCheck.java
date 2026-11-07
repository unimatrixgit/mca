package com.example.dunelobby;
import java.lang.reflect.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;

public class LobbyBuildCheck {
 static Object proxy(Class<?> type,java.util.function.Function<Method,Object> call) {
  return Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->m.getName().equals("equals")?p==a[0]:m.getName().equals("hashCode")?System.identityHashCode(p):call.apply(m));
 }
 public static void main(String[] args)throws Exception {
  World world=(World)proxy(World.class,m->m.getName().equals("getName")?"guild_lobby":null);
  Block block=(Block)proxy(Block.class,m->switch(m.getName()){case "getWorld"->world;case "getType"->Material.STONE;case "getX","getZ"->10000;default->null;});
  var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
  var unsafe=(sun.misc.Unsafe)f.get(null);
  var plugin=(DuneLobby)unsafe.allocateInstance(DuneLobby.class);
  var lobby=DuneLobby.class.getDeclaredField("lobby");lobby.setAccessible(true);lobby.set(plugin,world);
  int count=0;
  for(boolean permission:new boolean[]{false,true})for(boolean creative:new boolean[]{false,true})for(boolean sneak:new boolean[]{false,true})for(boolean cancelled:new boolean[]{false,true}) {
   Player player=(Player)proxy(Player.class,m->switch(m.getName()){case "hasPermission"->permission;case "getGameMode"->creative?GameMode.CREATIVE:GameMode.SURVIVAL;case "isSneaking"->sneak;case "getWorld"->world;default->null;});
   var event=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,null,block,BlockFace.UP,EquipmentSlot.HAND);
   if(cancelled)event.setCancelled(true);
   plugin.interact(event);
   boolean allowed=permission&&creative&&!sneak;
   if(event.isCancelled()!=(!allowed||cancelled))throw new AssertionError("Policy mismatch "+permission+creative+sneak+cancelled);
   count++;
  }
  System.out.println(count+" installed-handler cases passed: creative building / sneaking retains menus, visitor/survival rejection, existing cancellations preserved.");
 }
}


