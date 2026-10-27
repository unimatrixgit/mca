package com.example.dunerealms;

import org.bukkit.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.*;

/** Data-driven chest GUI with allowlisted actions; never executes arbitrary console commands. */
final class ConfigurableGui {
    private final DuneCore plugin;
    private final ServerJobs serverJobs;
    private final Economy economy;
    private final Map<UUID,?> jobs;
    private final String[] planets, factions;
    private final Map<UUID,String> selected;
    private YamlConfiguration config;
    private final Map<UUID, String> opened = new HashMap<>();
    private final Map<UUID, Map<Integer,String>> actions = new HashMap<>();

    ConfigurableGui(DuneCore plugin, ServerJobs serverJobs, Economy economy,
                    Map<UUID,?> jobs, String[] planets, String[] factions, Map<UUID,String> selected) {
        this.plugin=plugin; this.serverJobs=serverJobs; this.economy=economy; this.jobs=jobs;
        this.planets=planets; this.factions=factions; this.selected=selected;
        if(!new File(plugin.getDataFolder(),"gui.yml").exists()) plugin.saveResource("gui.yml",false);
        reload();
    }
    void reload() { config=YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"gui.yml")); }
    private String color(String input) { return ChatColor.translateAlternateColorCodes('&',input); }
    private String replace(String text, Player p) {
        return color(com.example.dunegui.PublicNames.text(text.replace("Solaris","Dunemarks").replace("%player%",p.getName())
            .replace("%balance%",String.valueOf((int)economy.getBalance(p)))
            .replace("%travel_cost%",String.valueOf(plugin.travelPrice()))
            .replace("%active_job%",serverJobs.activeDescription(p))));
    }
    private ItemStack item(Player p, String icon, String title, List<String> lore) {
        if(icon.equalsIgnoreCase("DUNEMARK")) return com.example.dunegui.DunemarkItems.icon(com.example.dunegui.DunemarkItems.denomination(economy.getBalance(p)),replace(title,p),lore.stream().map(line->replace(line,p)).toList());
        Material material=Material.matchMaterial(icon.toUpperCase(Locale.ROOT));
        if(material==null || !material.isItem()) material=Material.PAPER;
        ItemStack stack=new ItemStack(material);
        ItemMeta meta=stack.getItemMeta();
        meta.setDisplayName(com.example.dunegui.PublicNames.text(replace(title,p)));
        meta.setLore(lore.stream().map(line->replace(line,p)).toList());
        stack.setItemMeta(meta); return stack;
    }
    void open(Player p,String page) {
        if(page.equals("appointments")){openAppointments(p);return;}
        ConfigurationSection section=config.getConfigurationSection("menus."+page);
        if(section==null) { p.sendMessage(com.example.dunegui.PublicNames.text(ChatColor.RED+"Unknown GUI page: "+page)); return; }
        int size=section.getInt("size",54);
        if(size<9 || size>54 || size%9!=0) { plugin.getLogger().warning("Invalid GUI size for "+page); return; }
        String title=replace(section.getString("title","Dune | "+page),p);
        Inventory inv=Bukkit.createInventory(null,size,title);
        // Fill the inventory first; interactive icons are placed on top.
        ConfigurationSection backgrounds=config.getConfigurationSection("backgrounds");
        if(backgrounds!=null && config.getBoolean("menus."+page+".faction-background",true)) {
            String faction=selected.getOrDefault(p.getUniqueId(),"");
            String key=faction.toLowerCase(Locale.ROOT);
            String icon=backgrounds.getString(key,backgrounds.getString("default","GRAY_STAINED_GLASS_PANE"));
            for(int i=0;i<size;i++) inv.setItem(i,item(p,icon," ",List.of()));
        }
        Map<Integer,String> slots=new HashMap<>();
        ConfigurationSection items=section.getConfigurationSection("items");
        if(items!=null) for(String id:items.getKeys(false)) {
            String path=id+".";
            int slot=items.getInt(path+"slot",-1);
            if(slot<0 || slot>=size) continue;
            String icon=items.getString(path+"icon","PAPER");
            // Recognise the existing wallet entry without replacing the operator's gui.yml.
            if(id.equals("wallet")&&icon.equalsIgnoreCase("GOLD_INGOT"))icon="DUNEMARK";
            inv.setItem(slot,item(p,icon,items.getString(path+"name",id),items.getStringList(path+"lore")));
            slots.put(slot,items.getString(path+"action","none"));
        }
        ConfigurationSection dynamic=section.getConfigurationSection("dynamic");
        if(dynamic!=null) {
            String type=dynamic.getString("type","");
            List<String> ids=new ArrayList<>();
            if(type.equals("server_jobs")) ids.addAll(serverJobs.offerIds());
            if(type.equals("factions")) ids.addAll(Arrays.asList(factions));
            if(type.equals("planets")) ids.addAll(Arrays.asList(planets));
            int start=dynamic.getInt("start-slot",10), step=Math.max(1,dynamic.getInt("step",1)), max=Math.min(54,dynamic.getInt("max-items",27));
            for(int i=0;i<ids.size() && i<max;i++) {
                int slot=start+i*step;
                if(slot<0 || slot>=size || slots.containsKey(slot)) continue;
                String id=ids.get(i);
                String label=type.equals("server_jobs")?serverJobs.describe(id):id;
                String displayId=type.equals("planets")?com.example.dunegui.PlanetSigils.label(id):publicFaction(id);
                String name=dynamic.getString("name","&e%id%").replace("%id%",displayId);
                List<String> lore=dynamic.getStringList("lore").stream().map(line->line.replace("%description%",label).replace("%id%",id)).toList();
                String icon=dynamic.getString("icon","PAPER");
                if(type.equals("factions")) {
                    String key=id.toLowerCase(Locale.ROOT);
                    icon=config.getString("backgrounds."+key,icon);
                    
                }
                ItemStack displayed=item(p,icon,name,lore);
                if(type.equals("planets"))com.example.dunegui.PlanetSigils.apply(displayed,id);
                inv.setItem(slot,displayed);
                slots.put(slot,dynamic.getString("action","none").replace("%id%",id).replace("%index%",String.valueOf(i)));
            }
        }
        if(page.equals("main")&&size>=54&&!slots.containsKey(46)){
            inv.setItem(46,officeItem("crown","Landsraad appointments",List.of("Emperor-granted spice and water offices","View current appointments")));
            slots.put(46,"open:appointments");
        }
        opened.put(p.getUniqueId(),page);
        actions.put(p.getUniqueId(),slots);
        Map<Integer,Runnable> nativeActions=new HashMap<>(); slots.forEach((slot,action)->nativeActions.put(slot,()->execute(p,action))); com.example.dunegui.NativeMenus.inventory(plugin,p,title,inv,nativeActions);
    }
    void click(InventoryClickEvent e) {
        if(!(e.getWhoClicked() instanceof Player p)) return;
        String page=opened.get(p.getUniqueId());
        if(page==null) return;
        String title=page.equals("appointments")?"&6Landsraad appointments":config.getString("menus."+page+".title","Dune | "+page);
        if(!e.getView().getTitle().equals(replace(title,p))) return;
        e.setCancelled(true);
        if(e.getClickedInventory()!=e.getView().getTopInventory()) return;
        String action=actions.getOrDefault(p.getUniqueId(),Map.of()).get(e.getRawSlot());
        if(action==null || action.equals("none")) return;
        // Defer inventory changes until after InventoryClickEvent.
        Bukkit.getScheduler().runTask(plugin,()-> execute(p,action));
    }
    private String publicFaction(String id){return switch(id.toLowerCase(Locale.ROOT)){case "atreides"->"House Aerdrecht";case "harkonnen"->"House Harkveen";case "corrino"->"House Korenholt";case "ordos"->"House Oordwijk";case "fremen"->"Vrijduin";case "spacing_guild","guild"->"Vaerkring";default->id;};}
    private ItemStack officeItem(String model,String name,List<String> lore){ItemStack item=new ItemStack(Material.PAPER);ItemMeta meta=item.getItemMeta();meta.setDisplayName(com.example.dunegui.PublicNames.text(ChatColor.GOLD+name));meta.setLore(lore.stream().map(line->ChatColor.GRAY+line).toList());meta.setItemModel(new NamespacedKey("dunerealms","office_"+model));item.setItemMeta(meta);return item;}
    private void openAppointments(Player player){
        Inventory inventory=Bukkit.createInventory(null,27,color("&6Landsraad appointments"));
        Map<String,Object> offices=plugin.politics().websiteSnapshot();
        inventory.setItem(11,officeItem("spice","Specerhuis",List.of("Landsraad appointment · spice rights",officeHolder(offices,"spiceSteward","Not appointed"))));
        inventory.setItem(15,officeItem("water","Welvaart House",List.of("Landsraad appointment · water rights",officeHolder(offices,"houseOfWater","Not appointed"))));
        inventory.setItem(13,officeItem("crown","By appointment of the Emperor",List.of(officeHolder(offices,"emperor","Not elected"),"Appointments retain their existing election rules")));
        inventory.setItem(22,item(player,"ARROW","&eBack",List.of()));
        opened.put(player.getUniqueId(),"appointments");actions.put(player.getUniqueId(),Map.of(22,"open:main"));com.example.dunegui.NativeMenus.inventory(plugin,player,color("&6Landsraad appointments"),inventory,Map.of(22,()->open(player,"main")));
    }
    private String officeHolder(Map<String,Object> offices,String key,String empty){Object value=offices.get(key);return value==null?empty:com.example.dunegui.PublicNames.text(String.valueOf(value));}
    void drag(InventoryDragEvent e) {
        if(!(e.getWhoClicked() instanceof Player p)) return;
        String page=opened.get(p.getUniqueId());
        if(page==null) return;
        if(!e.getView().getTitle().equals(replace(page.equals("appointments")?"&6Landsraad appointments":config.getString("menus."+page+".title","Dune | "+page),p))) return;
        if(e.getRawSlots().stream().anyMatch(slot->slot<e.getView().getTopInventory().getSize())) e.setCancelled(true);
    }
    private void execute(Player p,String action) {
        if(action.startsWith("open:")) { open(p,action.substring(5).trim()); return; }
        if(action.equals("close")) { p.closeInventory(); return; }
        if(action.startsWith("dune:")) {
            String cmd=action.substring(5).trim();
            if(cmd.startsWith("guireload") || cmd.startsWith("guildnpc") || cmd.startsWith("giveSpice")) {
                if(!p.hasPermission("dune.admin")) { p.sendMessage(com.example.dunegui.PublicNames.text(ChatColor.RED+"Admin only.")); return; }
            }
            p.closeInventory(); plugin.runGuiCommand(p,cmd); return;
        }
        p.sendMessage(com.example.dunegui.PublicNames.text(ChatColor.RED+"Unsupported GUI action: "+action));
    }
}
