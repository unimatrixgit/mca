package com.example.duneess;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Vector3f;
import java.util.*;

/** Public nameplates. Cargo is shown only when the carrier is on that smuggler path. */
final class PlayerLabels implements Listener,AutoCloseable {
    private final DuneEss plugin;
    private final RankBridge ranks;
    private final Map<UUID,TextDisplay> displays=new HashMap<>();
    private final NamespacedKey marker,spice,water;
    PlayerLabels(DuneEss plugin,RankBridge ranks){
        this.plugin=plugin;this.ranks=ranks;marker=new NamespacedKey(plugin,"player_label");
        spice=new NamespacedKey("dunerealms","item");water=new NamespacedKey("dunerealms","finite_arrakis_water");
        for(World world:Bukkit.getWorlds())for(TextDisplay d:world.getEntitiesByClass(TextDisplay.class))if(d.getPersistentDataContainer().has(marker,PersistentDataType.BYTE))d.remove();
        Bukkit.getPluginManager().registerEvents(this,plugin);Bukkit.getScheduler().runTaskTimer(plugin,this::update,40L,40L);
    }
    static boolean publicCargo(boolean spicePath,boolean waterPath){return spicePath||waterPath;}
    private void update(){
        for(Player player:Bukkit.getOnlinePlayers()){
            if(!player.isValid()||player.isDead()||player.hasPermission("duneess.labels.hidden")){remove(player.getUniqueId());continue;}
            TextDisplay display=displays.get(player.getUniqueId());
            if(display==null||!display.isValid()||display.getWorld()!=player.getWorld()){
                remove(player.getUniqueId());
                display=player.getWorld().spawn(player.getLocation(),TextDisplay.class,d->{
                    d.setPersistent(false);d.setInvulnerable(true);d.setGravity(false);d.setBillboard(Display.Billboard.CENTER);
                    d.setAlignment(TextDisplay.TextAlignment.CENTER);d.setShadowed(true);d.setLineWidth(300);d.setViewRange(.8f);
                    d.getPersistentDataContainer().set(marker,PersistentDataType.BYTE,(byte)1);
                    var transformation=d.getTransformation();transformation.getTranslation().set(new Vector3f(0,.65f,0));transformation.getScale().set(.75f);d.setTransformation(transformation);
                });player.addPassenger(display);displays.put(player.getUniqueId(),display);
            }
            String role=ranks.access(player.getUniqueId());boolean emperor=ranks.hasRole(player.getUniqueId(),"emperor"),leader=ranks.hasRole(player.getUniqueId(),"house_leader");
            NamedTextColor color=role.equals("owners")?NamedTextColor.RED:emperor?NamedTextColor.GOLD:role.equals("corestaff")?NamedTextColor.LIGHT_PURPLE:role.equals("staff")?NamedTextColor.AQUA:leader?NamedTextColor.YELLOW:NamedTextColor.WHITE;
            String house=ranks.house(player.getUniqueId());if(!house.isBlank())house=house.substring(0,1).toUpperCase(Locale.ROOT)+house.substring(1);
            Component text=Component.text(player.getName(),color).append(Component.newline()).append(Component.text(house.isBlank()?"Traveller":house,NamedTextColor.GRAY));
            text=text.append(Component.newline()).append(Component.text(plugin.balanceLabel(player),NamedTextColor.GOLD));
            boolean spicePath=ranks.hasRole(player.getUniqueId(),"spice_smuggler"),waterPath=ranks.hasRole(player.getUniqueId(),"water_smuggler");
            if(publicCargo(spicePath,waterPath)){
                int spiceCount=0,waterCount=0;for(ItemStack item:player.getInventory().getContents()){
                    if(item==null||!item.hasItemMeta())continue;var tags=item.getItemMeta().getPersistentDataContainer();
                    if("spice".equals(tags.get(spice,PersistentDataType.STRING)))spiceCount+=item.getAmount();
                    if(tags.has(water,PersistentDataType.BYTE))waterCount+=item.getAmount();
                }
                if(spicePath)text=text.append(Component.newline()).append(Component.text("Smuggled spice: "+spiceCount,NamedTextColor.DARK_RED));
                if(waterPath)text=text.append(Component.newline()).append(Component.text("Smuggled water: "+waterCount,NamedTextColor.DARK_AQUA));
            }
            display.text(text);
        }
    }
    private void remove(UUID uuid){TextDisplay d=displays.remove(uuid);if(d!=null)d.remove();}
    @EventHandler public void quit(PlayerQuitEvent event){remove(event.getPlayer().getUniqueId());}
    @EventHandler public void changedWorld(PlayerChangedWorldEvent event){remove(event.getPlayer().getUniqueId());}
    @Override public void close(){displays.values().forEach(Entity::remove);displays.clear();}
}
