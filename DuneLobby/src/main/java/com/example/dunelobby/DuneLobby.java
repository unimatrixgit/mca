package com.example.dunelobby;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Separate optional lobby backend; never changes planet terrain or existing world blocks. */
public final class DuneLobby extends JavaPlugin implements Listener, CommandExecutor {
    private World lobby;
    private NamespacedKey roleKey;
    private final Map<UUID,Long> travelCooldown = new HashMap<>();
    private static final class TravelMenu implements InventoryHolder {
        final String planet; final Inventory inventory;
        TravelMenu(String planet) { this.planet=planet; inventory=Bukkit.createInventory(this,9,"Guild departure: "+planet); }
        public Inventory getInventory() { return inventory; }
    }
    public static final class VoidGenerator extends ChunkGenerator {
        @Override public void generateNoise(WorldInfo info, Random random,int x,int z,ChunkData data) {}
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }
    @Override public void onEnable() {
        saveDefaultConfig(); roleKey=new NamespacedKey(this,"role");
        getCommand("lobby").setExecutor(this); getCommand("dunelobby").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this,this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this,"BungeeCord");
        Bukkit.getScheduler().runTask(this,()-> {
            File folder=new File(Bukkit.getWorldContainer(),getConfig().getString("world","guild_lobby"));
            if(new File(folder,"level.dat").isFile()) {
                loadWorld(); ensureNpcs();
                if(Bukkit.getPluginManager().isPluginEnabled("ImageFrame")) Bukkit.getScheduler().runTaskLater(this,()->new LobbyArtwork(this,lobby).restore(),80);
                getLogger().info("Existing Guild lobby loaded; structure left unchanged.");
            } else getLogger().info("No lobby imported. Console: dunelobby build to create a NEW world.");
        });
    }
    private void loadWorld() {
        String name=getConfig().getString("world","guild_lobby");
        if(!name.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("world must be a simple folder name");
        lobby=Bukkit.createWorld(new WorldCreator(name).environment(World.Environment.THE_END).generator(new VoidGenerator()).generateStructures(false));
        disableDragonBattle();
        lobby.setSpawnLocation(new Location(lobby,.5,81,10.5,180,0));
        lobby.setGameRule(GameRule.DO_MOB_SPAWNING,false); lobby.setGameRule(GameRule.DO_FIRE_TICK,false);
        lobby.setGameRule(GameRule.MOB_GRIEFING,false); lobby.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,false);
        lobby.setGameRule(GameRule.DO_WEATHER_CYCLE,false); lobby.setGameRule(GameRule.DO_INSOMNIA,false);
        lobby.setGameRule(GameRule.KEEP_INVENTORY,true); lobby.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS,false);
        lobby.setGameRule(GameRule.SPAWN_RADIUS,0); lobby.setTime(18000); lobby.setDifficulty(Difficulty.PEACEFUL);
        lobby.setPVP(false); lobby.getWorldBorder().setCenter(-16,16); lobby.getWorldBorder().setSize(330);
        lobby.setKeepSpawnInMemory(false);
        // Keep the bounded lobby footprint loaded: NPC/art restoration must see all rooms.
        for(int cx=-8;cx<=6;cx++) for(int cz=-7;cz<=8;cz++) lobby.addPluginChunkTicket(cx,cz,this);
        for(EnderDragon dragon:lobby.getEntitiesByClass(EnderDragon.class)) dragon.remove();
    }
    private void disableDragonBattle() {
        // Paper 1.21.4 exposes ServerLevel.setDragonFight. Clearing the controller prevents
        // both a boss bar and vanilla portal/dragon generation after a real player joins.
        try {
            Object handle=lobby.getClass().getMethod("getHandle").invoke(lobby);
            var setter=Arrays.stream(handle.getClass().getMethods())
                .filter(m->m.getName().equals("setDragonFight")&&m.getParameterCount()==1).findFirst().orElseThrow();
            setter.invoke(handle,new Object[]{null});
        } catch(ReflectiveOperationException|NoSuchElementException e) {
            throw new IllegalStateException("This lobby requires the tested Paper 1.21.4 dragon-controller API",e);
        }
    }
    private boolean inLobby(World world) { return lobby!=null && world.equals(lobby); }
    private void ensureNpcs() {
        for(int x=-8;x<=6;x++) for(int z=-6;z<=8;z++) lobby.getChunkAt(x,z).load();
        npc("liaison","Vaerkring Routekeeper Liaison",13,87,-11,Villager.Profession.CLERIC);
        npc("exchange","Koopkring Exchange Broker",-82,81,23,Villager.Profession.LIBRARIAN);
        npc("water","Discreet Welvaarder",-78,81,110,Villager.Profession.FISHERMAN);
        npc("spice","Discreet Spice Seller",-54,81,110,Villager.Profession.LEATHERWORKER);
    }
    private void npc(String role,String name,int x,int y,int z,Villager.Profession profession) {
        for(Villager entity:lobby.getEntitiesByClass(Villager.class)) if(role.equals(entity.getPersistentDataContainer().get(roleKey,PersistentDataType.STRING))) return;
        lobby.spawn(new Location(lobby,x+.5,y,z+.5,180,0),Villager.class,v->{
            v.setCustomName(ChatColor.GOLD+name); v.setCustomNameVisible(true); v.setAI(false); v.setSilent(true);
            v.setInvulnerable(true); v.setPersistent(true); v.setRemoveWhenFarAway(false); v.setProfession(profession);
            v.setVillagerType(role.equals("water")||role.equals("spice")?Villager.Type.SWAMP:Villager.Type.DESERT);
            v.getPersistentDataContainer().set(roleKey,PersistentDataType.STRING,role); v.setRecipes(List.of());
        });
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(command.getName().equals("lobby")) {
            if(sender instanceof Player p) { if(lobby==null) p.sendMessage("The Guild lobby is not loaded."); else p.teleport(lobby.getSpawnLocation()); }
            return true;
        }
        if(args.length==0) { sender.sendMessage("/dunelobby build | artwork | verify | reload"); return true; }
        switch(args[0].toLowerCase(Locale.ROOT)) {
            case "build" -> {
                if(sender instanceof Player) { sender.sendMessage("World creation is console-only."); return true; }
                File target=new File(Bukkit.getWorldContainer(),getConfig().getString("world","guild_lobby"));
                if(target.exists() || lobby!=null) { sender.sendMessage("Refusing to overwrite an existing world. Import it or choose a new world name."); return true; }
                loadWorld(); long n=new LobbyBuilder(lobby).build(); ensureNpcs(); lobby.save();
                try { Files.writeString(lobby.getWorldFolder().toPath().resolve("DUNE-LOBBY.txt"),"DuneLobby 1.1.0 / Minecraft Java 1.21.4 / End lighting, opaque enclosure / spawn 0.5 81 10.5 yaw 180\n"); }
                catch(IOException e) { getLogger().warning(e.toString()); }
                sender.sendMessage("GUILD_BUILD_COMPLETE: "+n+" placements. Run dunelobby artwork, then dunelobby verify.");
            }
            case "artwork" -> { if(lobby!=null) new LobbyArtwork(this,lobby).install(); }
            case "verify" -> { if(lobby==null) sender.sendMessage("Lobby not loaded."); else Bukkit.getScheduler().runTaskLater(this,()->verify(sender),10); }
            case "reload" -> { reloadConfig(); sender.sendMessage("Rules, notices, trade prices and routing reloaded."); }
            default -> sender.sendMessage("Unknown lobby operation.");
        }
        return true;
    }
    private void verify(CommandSender sender) {
        List<String> failures=new ArrayList<>();
        Location s=lobby.getSpawnLocation();
        if(lobby.getEnvironment()!=World.Environment.THE_END) failures.add("environment");
        if(lobby.getEnderDragonBattle()!=null) failures.add("dragon battle controller active");
        if(!lobby.getBlockAt(s.getBlockX(),80,s.getBlockZ()).getType().isSolid() || !lobby.getBlockAt(s.getBlockX(),81,s.getBlockZ()).isPassable() || !lobby.getBlockAt(s.getBlockX(),82,s.getBlockZ()).isPassable()) failures.add("spawn clearance");
        // Flood-fill two-block-high walkable cells with step height <= 1.
        Set<String> seen=new HashSet<>(); ArrayDeque<int[]> todo=new ArrayDeque<>(); todo.add(new int[]{0,81,10});
        while(!todo.isEmpty()) {
            int[] p=todo.removeFirst(); String k=p[0]+","+p[1]+","+p[2];
            if(p[0]<-120||p[0]>92||p[2]<-94||p[2]>128||p[1]<79||p[1]>101||!seen.add(k)) continue;
            if(!walkable(p[0],p[1],p[2])) { seen.remove(k); continue; }
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) for(int dy:new int[]{0,1,-1}) {
                int x=p[0]+d[0],y=p[1]+dy,z=p[2]+d[1];
                if(!seen.contains(x+","+y+","+z) && walkable(x,y,z)) { todo.add(new int[]{x,y,z}); break; }
            }
        }
        int[][] targets={{0,81,10},{-66,81,35},{62,81,-36},{66,81,30},{-66,81,100},{-78,81,-32},{-78,81,109},{-54,81,109}};
        for(int[] p:targets) if(!seen.contains(p[0]+","+p[1]+","+p[2])) failures.add("unreachable "+Arrays.toString(p));
        for(int[] p:LobbyBuilder.PADS) if(!seen.contains(p[0]+",83,"+p[1])) failures.add("launch pad "+Arrays.toString(p));
        for(int[] p:new int[][]{{0,10},{-78,-58},{-66,40},{62,-42},{62,37},{-66,101},{-30,-21},{31,21}})
            if(!lobby.getBlockAt(p[0],128,p[1]).getType().isOccluding()) failures.add("missing flat ceiling "+Arrays.toString(p));
        for(int y:new int[]{81,96,120}) for(int[] p:new int[][]{{-126,0},{96,0},{0,-98},{0,130}})
            if(!lobby.getBlockAt(p[0],y,p[1]).getType().isOccluding()) failures.add("visible sky boundary "+Arrays.toString(p));
        for(String role:List.of("liaison","exchange","water","spice")) if(lobby.getEntitiesByClass(Villager.class).stream().noneMatch(v->role.equals(v.getPersistentDataContainer().get(roleKey,PersistentDataType.STRING)))) failures.add("missing NPC "+role);
        long art=lobby.getEntitiesByClass(ItemFrame.class).stream().filter(v->v.getPersistentDataContainer().has(new NamespacedKey(this,"art"),PersistentDataType.STRING)).count();
        if(art!=LobbyArtwork.tileCount()) failures.add("art frames "+art+" != "+LobbyArtwork.tileCount());
        String result="GUILD_VERIFY "+(failures.isEmpty()?"PASS":"FAIL")+" walkable="+seen.size()+" artwork="+art+" "+failures;
        sender.sendMessage(result);
        try { Files.writeString(getDataFolder().toPath().resolve("verification.txt"),result+"\n"); } catch(IOException e) { getLogger().warning(e.toString()); }
    }
    private boolean walkable(int x,int y,int z) {
        return lobby.getBlockAt(x,y-1,z).getType().isSolid() && lobby.getBlockAt(x,y,z).isPassable() && !lobby.getBlockAt(x,y,z).isLiquid() && lobby.getBlockAt(x,y+1,z).isPassable();
    }
    @EventHandler public void join(PlayerJoinEvent e) {
        if(lobby!=null && getConfig().getBoolean("join-at-lobby",true)) Bukkit.getScheduler().runTask(this,()->e.getPlayer().teleport(lobby.getSpawnLocation()));
    }
    @EventHandler public void respawn(PlayerRespawnEvent e) { if(lobby!=null && getConfig().getBoolean("respawn-at-lobby",true)) e.setRespawnLocation(lobby.getSpawnLocation()); }
    @EventHandler public void move(PlayerMoveEvent e) {
        if(inLobby(e.getPlayer().getWorld()) && e.getTo()!=null && e.getTo().getY()<65) e.setTo(lobby.getSpawnLocation());
    }
    @EventHandler public void damage(EntityDamageEvent e) { if(inLobby(e.getEntity().getWorld())) e.setCancelled(true); }
    @EventHandler public void spawn(CreatureSpawnEvent e) {
        if(inLobby(e.getLocation().getWorld()) && (e.getEntity() instanceof EnderDragon || e.getSpawnReason()!=CreatureSpawnEvent.SpawnReason.CUSTOM)) e.setCancelled(true);
    }
    @EventHandler public void breakBlock(BlockBreakEvent e) { if(inLobby(e.getBlock().getWorld())&&!e.getPlayer().hasPermission("dunelobby.build")) e.setCancelled(true); }
    @EventHandler public void placeBlock(BlockPlaceEvent e) { if(inLobby(e.getBlock().getWorld())&&!e.getPlayer().hasPermission("dunelobby.build")) e.setCancelled(true); }
    @EventHandler public void explode(EntityExplodeEvent e) { if(inLobby(e.getLocation().getWorld())) e.blockList().clear(); }
    @EventHandler public void blockExplode(BlockExplodeEvent e) { if(inLobby(e.getBlock().getWorld())) e.blockList().clear(); }
    @EventHandler public void hanging(HangingBreakEvent e) { if(inLobby(e.getEntity().getWorld())) e.setCancelled(true); }
    @EventHandler public void entityInteract(PlayerInteractEntityEvent e) {
        if(!inLobby(e.getPlayer().getWorld())) return;
        if(e.getRightClicked() instanceof ItemFrame) { e.setCancelled(true); return; }
        String role=e.getRightClicked().getPersistentDataContainer().get(roleKey,PersistentDataType.STRING);
        if(role==null) return; e.setCancelled(true); if(e.getHand()!=EquipmentSlot.HAND) return;
        switch(role) {
            case "liaison" -> { if(Bukkit.getPluginCommand("duneess")!=null) e.getPlayer().performCommand("duneess travel"); else departure(e.getPlayer(),"arrakis"); }
            case "exchange" -> { if(Bukkit.getPluginCommand("dune")!=null) e.getPlayer().performCommand("dune exchange"); else e.getPlayer().sendMessage(ChatColor.GOLD+"Koopkring market integration requires DuneCore on this backend."); }
            case "water","spice" -> trade(e.getPlayer(),role);
        }
    }
    private void trade(Player player,String role) {
        boolean water=role.equals("water");
        ItemStack result=new ItemStack(water?Material.POTION:Material.ORANGE_DYE);
        ItemMeta meta=result.getItemMeta(); meta.setDisplayName(ChatColor.GOLD+(water?"Smuggled drinking water":"Smuggler spice sample"));
        meta.setLore(List.of("Guild lobby barter goods","Not redeemable for Solaris or faction rewards"));
        if(meta instanceof PotionMeta potion) potion.setBasePotionType(org.bukkit.potion.PotionType.WATER);
        result.setItemMeta(meta);
        int price=Math.max(1,Math.min(64,getConfig().getInt(water?"water-price-emeralds":"spice-price-emeralds",water?4:8)));
        MerchantRecipe recipe=new MerchantRecipe(result,0,999,false,0,0f); recipe.addIngredient(new ItemStack(Material.EMERALD,price));
        Merchant merchant=Bukkit.createMerchant(water?"Discreet Welvaarder":"Discreet Spice Seller"); merchant.setRecipes(List.of(recipe)); player.openMerchant(merchant,true);
    }
    @EventHandler public void interact(PlayerInteractEvent e) {
        if(!inLobby(e.getPlayer().getWorld()) || e.getClickedBlock()==null || e.getAction()!=Action.RIGHT_CLICK_BLOCK) return;
        var b=e.getClickedBlock(); e.setCancelled(true); if(e.getHand()!=EquipmentSlot.HAND) return;
        int x=b.getX(),z=b.getZ();
        for(int i=0;i<LobbyBuilder.PADS.length;i++) { int[] p=LobbyBuilder.PADS[i]; if(Math.abs(x-p[0])<=3 && z>=p[1]-9 && z<=p[1]-7) { departure(e.getPlayer(),LobbyBuilder.PLANETS.get(i)); return; } }
        if(b.getType()==Material.LECTERN) {
            boolean rules=z>0; List<String> text=getConfig().getStringList(rules?"rules":"news");
            com.example.dunegui.NativeMenus.show(this,e.getPlayer(),rules?"Guild Charter":"Guild News",String.join("\n\n",text),List.of(
                new com.example.dunegui.NativeMenus.Choice("Close","",e.getPlayer()::closeInventory)));

        }
    }
    private void departure(Player player,String planet) {
        TravelMenu menu=new TravelMenu(planet); ItemStack confirm=new ItemStack(Material.LIME_CONCRETE); ItemMeta meta=confirm.getItemMeta();
        meta.setDisplayName("Confirm departure: "+planet); meta.setLore(List.of("DuneEss fares and access rules apply when installed.")); confirm.setItemMeta(meta);
        menu.inventory.setItem(4,confirm);
        com.example.dunegui.NativeMenus.show(this,player,"Guild departure",List.of(
            new com.example.dunegui.NativeMenus.Choice("Confirm departure: "+planet,"DuneEss fares and access rules apply.",()->depart(player,planet)),
            new com.example.dunegui.NativeMenus.Choice("Cancel","",player::closeInventory)));
    }
    private void depart(Player player,String planet) {
        long now=System.currentTimeMillis(); if(now-travelCooldown.getOrDefault(player.getUniqueId(),0L)<5000) return;
        travelCooldown.put(player.getUniqueId(),now);
        Bukkit.getScheduler().runTask(this,()-> {
            player.closeInventory();
            if(Bukkit.getPluginCommand("duneess")!=null) { player.performCommand("duneess travel "+planet); return; }
            World target=Bukkit.getWorld(planet);
            if(target!=null) { player.teleport(target.getSpawnLocation()); return; }
            String server=getConfig().getString("servers."+planet,"");
            if(server.isBlank()) { player.sendMessage("No destination configured."); return; }
            try {
                ByteArrayOutputStream bytes=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(bytes); out.writeUTF("Connect"); out.writeUTF(server);
                player.sendPluginMessage(this,"BungeeCord",bytes.toByteArray()); player.sendMessage("Guild departure requested. If you remain here, the destination or proxy is unavailable.");
            } catch(IOException ex) { player.sendMessage("Guild departure could not be requested."); }
        });
    }
    @EventHandler public void drag(InventoryDragEvent e) { if(e.getView().getTopInventory().getHolder() instanceof TravelMenu) e.setCancelled(true); }
    @EventHandler public void quit(PlayerQuitEvent e) { travelCooldown.remove(e.getPlayer().getUniqueId()); }
    @Override public void onDisable() { if(lobby!=null) lobby.save(); }
}
