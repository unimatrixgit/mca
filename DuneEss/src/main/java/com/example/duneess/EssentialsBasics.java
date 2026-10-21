package com.example.duneess;

import com.example.dunegui.NativeMenus;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Deliberately small essentials command surface; vanilla owns bed/anchor respawns. */
final class EssentialsBasics implements CommandExecutor, TabCompleter, Listener {
    private final DuneEss plugin;
    private final File file;
    private YamlConfiguration homes;
    private final Map<UUID,Request> requests=new HashMap<>();
    private record Request(UUID sender,long expires) {}
    EssentialsBasics(DuneEss plugin) {
        this.plugin=plugin;
        file=new File(plugin.getDataFolder(),"homes.yml");
        homes=YamlConfiguration.loadConfiguration(file);
    }
    void register() {
        for(String name:List.of("home","sethome","delhome","homes","tpa","tpaccept","tpdeny","spawn","server","essentials")) {
            PluginCommand command=Objects.requireNonNull(plugin.getCommand(name));
            command.setExecutor(this); command.setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    static String homeName(String[] args) {
        String name=args.length==0?"home":args[0].toLowerCase(Locale.ROOT);
        if(args.length>1 || !name.matches("[a-z0-9_-]{1,24}")) throw new IllegalArgumentException("Use a home name with 1–24 letters, numbers, underscores or hyphens.");
        return name;
    }
    private String key(Player p,String name) { return p.getUniqueId()+"."+name; }
    private Set<String> names(Player p) {
        var section=homes.getConfigurationSection(p.getUniqueId().toString());
        return section==null?Set.of():section.getKeys(false);
    }
    private void save() throws IOException {
        Path target=file.toPath(), temp=target.resolveSibling("homes.yml.tmp");
        Files.createDirectories(target.getParent());
        Files.writeString(temp,homes.saveToString());
        try { Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        catch(AtomicMoveNotSupportedException ex) { Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING); }
    }
    private void teleport(Player p,Location location) {
        // Local essentials never bypass interplanetary permits, fares, or arrival rules.
        if(location==null || location.getWorld()==null) { p.sendMessage("That destination is unavailable on this server."); return; }
        if(!p.getWorld().equals(location.getWorld())) { p.sendMessage("Use /server to travel between worlds, then /home at your destination."); return; }
        if(!location.getWorld().getWorldBorder().isInside(location) || location.getY()<location.getWorld().getMinHeight() || location.getY()>=location.getWorld().getMaxHeight()) {
            p.sendMessage("That destination is outside the world's playable bounds."); return;
        }
        p.teleportAsync(location).thenAccept(success->{ if(!success) p.sendMessage("Teleportation failed. Try again."); });
    }
    void menu(Player p) {
        List<NativeMenus.Choice> choices=new ArrayList<>();
        choices.add(new NativeMenus.Choice("Set home","Save your current position as home.",()->p.performCommand("sethome")));
        choices.add(new NativeMenus.Choice("Homes","Choose a saved home on this backend.",()->homeMenu(p)));
        choices.add(new NativeMenus.Choice("Spawn","Return to this world's spawn.",()->p.performCommand("spawn")));
        choices.add(new NativeMenus.Choice("Guild servers","Travel using existing Solaris fares and permissions.",()->p.performCommand("duneess travel")));
        choices.add(new NativeMenus.Choice("Solaris","Show your balance.",()->p.performCommand("duneess balance")));
        choices.add(new NativeMenus.Choice("CHOAM Exchange","Buy, list and reclaim items.",()->p.performCommand("exchange")));
        choices.add(new NativeMenus.Choice("Help","Open the Dune Field Guide.",()->p.performCommand("duneess help menu")));
        NativeMenus.show(plugin,p,"DuneRealms Essentials","Solaris: "+plugin.economy().format(plugin.economy().getBalance(p)),choices);
    }
    private void homeMenu(Player p) {
        List<NativeMenus.Choice> choices=names(p).stream().sorted().map(name->new NativeMenus.Choice(name,"Teleport to this saved home.",()->p.performCommand("home "+name))).toList();
        if(choices.isEmpty()) p.sendMessage("You have no homes. Use /sethome [name].");
        else NativeMenus.show(plugin,p,"Your homes",choices);
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!(sender instanceof Player p)) { sender.sendMessage("Use this command in-game."); return true; }
        String action=command.getName();
        if(!p.hasPermission("duneess.basics")) { p.sendMessage("You do not have permission to use essentials."); return true; }
        try {
            switch(action) {
                case "essentials" -> menu(p);
                case "homes" -> homeMenu(p);
                case "sethome" -> {
                    String name=homeName(args), path=key(p,name);
                    if(!homes.contains(path) && names(p).size()>=Math.max(1,plugin.getConfig().getInt("essentials.max-homes",3))) throw new IllegalArgumentException("Your home limit has been reached.");
                    homes.set(path,p.getLocation()); save(); p.sendMessage("Home saved: "+name);
                }
                case "home" -> teleport(p,homes.getLocation(key(p,homeName(args))));
                case "delhome" -> {
                    String name=homeName(args),path=key(p,name);
                    if(!homes.contains(path)) throw new IllegalArgumentException("That home does not exist.");
                    homes.set(path,null); save(); p.sendMessage("Home removed: "+name);
                }
                case "spawn" -> teleport(p,p.getWorld().getSpawnLocation());
                case "server" -> plugin.onCommand(p,Objects.requireNonNull(plugin.getCommand("duneess")),"duneess",args.length==0?new String[]{"travel"}:new String[]{"travel",args[0]});
                case "tpa" -> {
                    if(args.length!=1) throw new IllegalArgumentException("Usage: /tpa <online player>");
                    Player target=Bukkit.getPlayerExact(args[0]);
                    if(target==null || target.equals(p) || !p.canSee(target)) throw new IllegalArgumentException("Choose another online player on this server.");
                    if(!target.getWorld().equals(p.getWorld())) throw new IllegalArgumentException("Use Guild travel to reach that player's world first.");
                    requests.put(target.getUniqueId(),new Request(p.getUniqueId(),System.nanoTime()+60_000_000_000L));
                    target.sendMessage(p.getName()+" requests to teleport to you. /tpaccept or /tpdeny (60 seconds).");
                    p.sendMessage("Teleport request sent.");
                }
                case "tpaccept","tpdeny" -> {
                    Request request=requests.remove(p.getUniqueId());
                    if(request==null || request.expires()<System.nanoTime()) throw new IllegalArgumentException("No active teleport request.");
                    Player requester=Bukkit.getPlayer(request.sender());
                    if(requester==null || !requester.isOnline()) throw new IllegalArgumentException("That player is offline.");
                    if(action.equals("tpaccept")) teleport(requester,p.getLocation());
                    else requester.sendMessage("Your teleport request was declined.");
                }
                default -> { return false; }
            }
        } catch(IllegalArgumentException ex) { p.sendMessage(ex.getMessage()); }
        catch(IOException ex) {
            homes=YamlConfiguration.loadConfiguration(file);
            plugin.getLogger().warning("Could not persist homes: "+ex.getMessage());
            p.sendMessage("Could not save your homes. No change was committed.");
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        if(!(sender instanceof Player p) || args.length!=1) return List.of();
        Collection<String> values=switch(command.getName()) {
            case "home","delhome" -> names(p);
            case "tpa" -> Bukkit.getOnlinePlayers().stream().filter(p::canSee).map(Player::getName).toList();
            case "server" -> List.of("arrakis","giedi_prime","caladan","kaitain","sigma_draconis_iv","spacing_guild");
            default -> List.of();
        };
        return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        UUID id=event.getPlayer().getUniqueId();
        requests.remove(id); requests.values().removeIf(request->request.sender().equals(id));
    }
}
