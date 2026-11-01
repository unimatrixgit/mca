package com.dunerealms.staff;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.sql.*;
import java.util.function.Consumer;

/** Calls the actual Core inventory checkpoint; no independent inventory persistence or fallback. */
final class CoreFence {
 private final JavaPlugin core;
 CoreFence(JavaPlugin core){this.core=core;}
 void assignFaction(Player actor,Player target,String faction,Consumer<Connection> action)throws Exception{
  if(!core.isEnabled()||!CaseGuards.target(actor,target,true)||!CaseStore.FACTIONS.contains(faction))throw new SQLException("Current canonical case faction authority required");
  try{core.getClass().getMethod("adminAssignFaction",Player.class,Player.class,String.class,Consumer.class).invoke(core,actor,target,faction,action);}
  catch(InvocationTargetException e){if(e.getCause() instanceof Exception failure)throw failure;throw new IllegalStateException("Core faction checkpoint failed",e.getCause());}
 }
 void run(Player target,Consumer<Connection> action)throws Exception{
  if(!target.isOnline()||!core.isEnabled())throw new SQLException("Loaded target profile required");
  try{core.getClass().getMethod("transactNetworkInventory",Player.class,Consumer.class).invoke(core,target,action);}
  catch(InvocationTargetException e){if(e.getCause() instanceof Exception failure)throw failure;throw new IllegalStateException("Core checkpoint failed",e.getCause());}
 }
}

