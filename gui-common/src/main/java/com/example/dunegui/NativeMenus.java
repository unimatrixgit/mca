package com.example.dunegui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Binary-compatible bridge to the shared virtual renderer. No chest fallback. */
public final class NativeMenus {
 private NativeMenus(){}
 public record Choice(String label,String description,Runnable action,String imageUrl){
  public Choice(String label,String description,Runnable action){this(label,description,action,null);}
 }
 private static Plugin renderer(){Plugin p=Bukkit.getPluginManager().getPlugin("DuneMenus");return p!=null&&p.isEnabled()?p:null;}
 private static Object call(String name,Class<?>[] types,Object...args){
  Plugin p=renderer();if(p==null)return null;
  try{return p.getClass().getMethod(name,types).invoke(p,args);}catch(ReflectiveOperationException error){return null;}
 }
 public static void inventory(JavaPlugin plugin,Player player,String title,Inventory model,Map<Integer,Runnable> actions){inventory(plugin,player,title,model,actions,()->true);}
 public static void inventory(JavaPlugin plugin,Player player,String title,Inventory model,Map<Integer,Runnable> actions,BooleanSupplier allowed){
  Object result=call("show",new Class<?>[]{JavaPlugin.class,Player.class,String.class,Inventory.class,Map.class,BooleanSupplier.class},plugin,player,title,model,actions,allowed);
  if(!Boolean.TRUE.equals(result))player.sendMessage("The Imperial Navigator is unavailable. Try again after the server finishes starting.");
 }
 public static boolean isCurrent(Player player,Inventory model){return Boolean.TRUE.equals(call("isCurrent",new Class<?>[]{Player.class,Inventory.class},player,model));}
 public static void close(Player player,Inventory model){call("close",new Class<?>[]{Player.class,Inventory.class},player,model);}
 public static void show(JavaPlugin plugin,Player player,String title,List<Choice> choices){show(plugin,player,title,"",choices);}
 public static void show(JavaPlugin plugin,Player player,String title,String content,List<Choice> choices){
  Object result=call("showChoices",new Class<?>[]{JavaPlugin.class,Player.class,String.class,String.class,List.class},plugin,player,title,content,choices);
  if(!Boolean.TRUE.equals(result))player.sendMessage("The Imperial Navigator is unavailable. Try again shortly.");
 }
 static int protocol(Player player){return 769;}
}
