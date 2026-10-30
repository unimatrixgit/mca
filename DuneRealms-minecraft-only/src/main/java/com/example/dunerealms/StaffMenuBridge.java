package com.example.dunerealms;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Keeps the main Navigator tied to the shared renderer's current permitted services. */
final class StaffMenuBridge {
 private StaffMenuBridge(){}
 static boolean available(Player player){
  Plugin renderer=Bukkit.getPluginManager().getPlugin("DuneMenus");
  if(renderer==null||!renderer.isEnabled())return false;
  try{return Boolean.TRUE.equals(renderer.getClass().getMethod("hasStaffTools",Player.class).invoke(renderer,player));}
  catch(ReflectiveOperationException unavailable){return false;}
 }
}
