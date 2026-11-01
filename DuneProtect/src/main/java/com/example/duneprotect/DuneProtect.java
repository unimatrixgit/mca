package com.example.duneprotect;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

/** Command facade: original plugin identity, command checks and audit history are retained. */
public final class DuneProtect extends JavaPlugin implements CommandExecutor,TabCompleter {
    private record Route(String plugin,String command,String permission) {}
    private static final Map<String,Route> ROUTES=Map.of(
        "inspect",new Route("CoreProtect","coreprotect:coreprotect inspect","duneprotect.inspect"),
        "lookup",new Route("CoreProtect","coreprotect:coreprotect lookup","duneprotect.lookup"),
        "rollback",new Route("CoreProtect","coreprotect:coreprotect rollback","duneprotect.rollback"),
        "restore",new Route("CoreProtect","coreprotect:coreprotect restore","duneprotect.restore"),
        "worldedit",new Route("WorldEdit","worldedit:worldedit","duneprotect.worldedit"),
        "worldguard",new Route("WorldGuard","worldguard:region","duneprotect.regions"));
    @Override public void onEnable(){Objects.requireNonNull(getCommand("duneprotect")).setExecutor(this);getCommand("duneprotect").setTabCompleter(this);}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(args.length==0||args[0].equalsIgnoreCase("help")){sender.sendMessage("/duneprotect inspect | lookup <filters> | rollback <filters> | restore <filters> | worldedit <args> | worldguard <args>");sender.sendMessage("CoreProtect, WorldEdit and WorldGuard retain their native permission checks.");return true;}
        Route route=ROUTES.get(args[0].toLowerCase(Locale.ROOT));
        if(route==null){sender.sendMessage("Unknown protection feature. Use /duneprotect help.");return true;}
        if(!sender.hasPermission(route.permission)){sender.sendMessage("Permission required: "+route.permission);return true;}
        if(!Bukkit.getPluginManager().isPluginEnabled(route.plugin)){sender.sendMessage(route.plugin+" is unavailable on this server.");return true;}
        // Execute as the original sender. Never elevate a player to the server console.
        Bukkit.dispatchCommand(sender,route.command+(args.length>1?" "+String.join(" ",Arrays.copyOfRange(args,1,args.length)):""));return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length!=1)return List.of();
        return ROUTES.entrySet().stream().filter(e->sender.hasPermission(e.getValue().permission)&&e.getKey().startsWith(args[0].toLowerCase(Locale.ROOT))).map(Map.Entry::getKey).sorted().toList();
    }
}
