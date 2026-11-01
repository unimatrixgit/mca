package com.dunerealms.staff;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import org.bukkit.inventory.Inventory;
import java.util.function.BooleanSupplier;

/** Uses the existing shared renderer, so Java/Bedrock share the same guarded callbacks. */
public final class Screens {
 private Screens(){}
 public record Choice(String label,String description,Runnable action,String imageUrl){public Choice(String label,String description,Runnable action){this(label,description,action,null);}}
 static Inventory loading(JavaPlugin owner,Player player,String title){
  Inventory model=Bukkit.createInventory(null,9,title);Plugin renderer=Bukkit.getPluginManager().getPlugin("DuneMenus");
  try{if(renderer==null||!renderer.isEnabled()||!Boolean.TRUE.equals(renderer.getClass().getMethod("show",JavaPlugin.class,Player.class,String.class,Inventory.class,Map.class,BooleanSupplier.class).invoke(renderer,owner,player,title,model,Map.of(),(BooleanSupplier)()->CaseGuards.entry(player))))throw new IllegalStateException();}
  catch(ReflectiveOperationException|RuntimeException unavailable){player.sendMessage("The shared case renderer is unavailable.");}
  return model;
 }
 static boolean current(Player player,Inventory model){Plugin renderer=Bukkit.getPluginManager().getPlugin("DuneMenus");try{return renderer!=null&&renderer.isEnabled()&&Boolean.TRUE.equals(renderer.getClass().getMethod("isCurrent",Player.class,Inventory.class).invoke(renderer,player,model));}catch(ReflectiveOperationException|RuntimeException unavailable){return false;}}
 static void show(JavaPlugin owner,Player player,String title,String body,List<Choice> choices){
  Plugin renderer=Bukkit.getPluginManager().getPlugin("DuneMenus");
  try{if(renderer==null||!renderer.isEnabled()||!Boolean.TRUE.equals(renderer.getClass().getMethod("showChoices",JavaPlugin.class,Player.class,String.class,String.class,List.class).invoke(renderer,owner,player,title,body,choices)))throw new IllegalStateException();}
  catch(ReflectiveOperationException|RuntimeException unavailable){player.sendMessage("The shared staff menu is unavailable. No case action was performed.");}
 }
}

