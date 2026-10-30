package com.example.dunemenus;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.UUID;

/** No newer Paper classes are resolved on the existing 1.21.4 server. */
final class DialogSupport {
 private DialogSupport(){}
 static boolean serverSupported(){try{Class.forName("io.papermc.paper.dialog.Dialog",false,DialogSupport.class.getClassLoader());return true;}catch(ClassNotFoundException|LinkageError unavailable){return false;}}
 static boolean supported(Player player){
  try{
   Class.forName("io.papermc.paper.dialog.Dialog",false,DialogSupport.class.getClassLoader());
   return protocol(player)>=771;
  }catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable){return false;}
 }
 static int protocol(Player player){
  if(Bukkit.getPluginManager().isPluginEnabled("ViaVersion"))try{
   Class<?> via=Class.forName("com.viaversion.viaversion.api.Via");
   Object api=via.getMethod("getAPI").invoke(null);
   return (int)Class.forName("com.viaversion.viaversion.api.ViaAPI").getMethod("getPlayerVersion",UUID.class).invoke(api,player.getUniqueId());
  }catch(ReflectiveOperationException|LinkageError unavailable){return -1;}
  return player.getProtocolVersion();
 }
}
