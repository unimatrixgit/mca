package com.example.dunerealms;

import org.bukkit.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.*;

/** Data-driven chest GUI with allowlisted actions; never executes arbitrary console commands. */
final class ConfigurableGui implements Listener {
    private final DuneCore plugin;
    private final ServerJobs serverJobs;
    private final Economy economy;
    private final Map<UUID,?> jobs;
    private final String[] planets, factions;
    private final Map<UUID,String> selected;
    private YamlConfiguration config;
    private final Map<UUID, String> opened = new HashMap<>();
    private final Map<UUID, Map<Integer,String>> actions = new HashMap<>();
    private static final class JobScreen {
        final UUID owner;final Inventory inventory;final Map<Integer,Runnable> buttons;
        boolean claimed,expired;
        JobScreen(UUID owner,Inventory inventory,Map<Integer,Runnable> buttons){this.owner=owner;this.inventory=inventory;this.buttons=Map.copyOf(buttons);}
    }
    private final Map<UUID,JobScreen> jobScreens=new HashMap<>();
    private final Map<Inventory,JobScreen> jobInventories=new IdentityHashMap<>();

    ConfigurableGui(DuneCore plugin, ServerJobs serverJobs, Economy economy,
                    Map<UUID,?> jobs, String[] planets, String[] factions, Map<UUID,String> selected) {
        this.plugin=plugin; this.serverJobs=serverJobs; this.economy=economy; this.jobs=jobs;
        this.planets=planets; this.factions=factions; this.selected=selected;
        if(!new File(plugin.getDataFolder(),"gui.yml").exists()) plugin.saveResource("gui.yml",false);
        reload();
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    void reload() { config=YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"gui.yml")); }
    private String color(String input) { return ChatColor.translateAlternateColorCodes('&',input); }
    private String replace(String text, Player p) {
        String result=text.replace("Solaris","Dunemarks");
        if(result.contains("%player%"))result=result.replace("%player%",p.getName());
        if(result.contains("%balance%"))result=result.replace("%balance%",String.valueOf((int)displayBalance(p)));
        if(result.contains("%travel_cost%"))result=result.replace("%travel_cost%",String.valueOf(plugin.travelPrice()));
        if(result.contains("%active_job%"))result=result.replace("%active_job%",serverJobs.activeDescription(p));
        return color(com.example.dunegui.PublicNames.text(result));
    }
    private double displayBalance(Player player){
        return GuiBalanceSnapshot.get(player.getUniqueId(),Bukkit.getCurrentTick(),()->economy.getBalance(player));
    }

    private ItemStack item(Player p, String icon, String title, List<String> lore) {
        if(icon.equalsIgnoreCase("DUNEMARK")) return com.example.dunegui.DunemarkItems.icon(com.example.dunegui.DunemarkItems.denomination(displayBalance(p)),replace(title,p),lore.stream().map(line->replace(line,p)).toList());
        Material material=Material.matchMaterial(icon.toUpperCase(Locale.ROOT));
        if(material==null || !material.isItem()) material=Material.PAPER;
        ItemStack stack=new ItemStack(material);
        ItemMeta meta=stack.getItemMeta();
        meta.setDisplayName(com.example.dunegui.PublicNames.text(replace(title,p)));
        meta.setLore(lore.stream().map(line->replace(line,p)).toList());
        // Only GUI-created icons pass here. Wallet, planet and political models keep their own selectors.
        String model=guiModel(replace(title,p),icon,lore);
        if(model!=null&&!meta.hasItemModel())meta.setItemModel(new NamespacedKey("dunerealms",model));
        stack.setItemMeta(meta); return stack;
    }
    static String guiModel(String title,String icon,List<String> lore){
        String label=ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',title)).toLowerCase(Locale.ROOT);
        return label.equals("confirm")?"gui_confirm":label.contains("item exchange")?"gui_market":
            label.contains("ticket")||label.contains("job")||label.contains("contract")||label.contains("listing")||lore.stream().anyMatch(line->line.toLowerCase(Locale.ROOT).contains("spice remaining:"))?"gui_contract":
            label.contains("staff desk")||label.contains("player menu")||label.contains("travel")||icon.equalsIgnoreCase("COMPASS")?"gui_compass":null;
    }
    void open(Player p,String page) {
        if(page.equals("appointments")){openAppointments(p);return;}
        if(page.equals("serverjobs")){openServerJobs(p);return;}
        if(page.equals("exchange")){openPlayerJobs(p,0);return;}
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
        if(page.equals("main")&&!slots.containsValue("itemexchange")){
            for(int slot:new int[]{40,42,38,24,22})if(slot<size&&!slots.containsKey(slot)){
                inv.setItem(slot,item(p,"CHEST","&6Item Exchange",List.of("&7Buy player-listed items","&7List items from your inventory","&eClick to open the marketplace")));
                slots.put(slot,"itemexchange");break;
            }
        }
        if(page.equals("main")&&!slots.containsValue("tickets")){
            for(int slot:new int[]{41,43,39,25,23})if(slot<size&&!slots.containsKey(slot)){
                inv.setItem(slot,item(p,"WRITABLE_BOOK","&6Support Tickets",List.of("&7Your live support and appeal records","&7Staff queues require current permissions","&eClick to open the ticket desk")));
                slots.put(slot,"tickets");break;
            }
        }
        if(page.equals("main")&&StaffMenuBridge.available(p)&&!slots.containsValue("staffdesk")){
            for(int slot:new int[]{42,44,38,26,24})if(slot<size&&!slots.containsKey(slot)){
                inv.setItem(slot,item(p,"SHIELD","&6Staff Desk",List.of("&7Moderation, bans and staff tickets","&7Only your permitted tools are shown","&eClick to open staff controls")));
                slots.put(slot,"staffdesk");break;
            }
        }
        if(page.equals("main")&&size>=54&&!slots.containsKey(46)){
            inv.setItem(46,officeItem("crown","Landsraad appointments",List.of("Emperor-granted spice and water offices","View current appointments")));
            slots.put(46,"open:appointments");
        }
        if(page.equals("main")) {
            String[][] modules={{"DuneClans","clanshop","CHEST","&6Clan Shop","&7Dunemark clan bank and clan stillsuits","dune.clans.view"},
                {"DuneItems","officekits","LEATHER_CHESTPLATE","&6Office Stillsuits","&7Regalia for your current elected offices",""},
                {"DuneQuests","quests","WRITABLE_BOOK","&6My Quests","&7Your discovered encounters and quest history","dune.quests.view"},
                {"DuneAtelier","atelier","LOOM","&6Kits Exchange","&7Paid deliveries, designs and sales quests","dune.atelier.view"}};
            for(String[] module:modules) {
                org.bukkit.plugin.Plugin target=Bukkit.getPluginManager().getPlugin(module[0]);
                if(target==null||!target.isEnabled()||(!module[5].isEmpty()&&!p.hasPermission(module[5]))||slots.containsValue(module[1]))continue;
                for(int slot:new int[]{45,46,47,49,50,51})if(slot<size&&!slots.containsKey(slot)) {
                    inv.setItem(slot,item(p,module[2],module[3],List.of(module[4],"&eClick to open")));
                    slots.put(slot,module[1]);break;
                }
            }
        }
        if(page.equals("main")&&p.hasPermission("dunebank.use")&&!slots.containsValue("personalbank")) {
            org.bukkit.plugin.Plugin bank=Bukkit.getPluginManager().getPlugin("DuneBank");
            if(bank!=null&&bank.isEnabled())for(int slot:new int[]{48,52,53,44})if(slot<size&&!slots.containsKey(slot)) {
                inv.setItem(slot,item(p,"ENDER_CHEST","&6Personal Bank",List.of("&7Your private Dunemark savings","&eClick to open your account")));
                slots.put(slot,"personalbank");break;
            }
        }
        releaseJobScreen(jobScreens.get(p.getUniqueId()));
        opened.put(p.getUniqueId(),page);
        actions.put(p.getUniqueId(),slots);
        Map<Integer,Runnable> nativeActions=new HashMap<>(); slots.forEach((slot,action)->nativeActions.put(slot,()->execute(p,action))); com.example.dunegui.NativeMenus.inventory(plugin,p,title,inv,nativeActions);
    }
    void click(InventoryClickEvent e) {
        JobScreen jobScreen=jobInventories.get(e.getView().getTopInventory());
        if(jobScreen!=null){
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player player)||!jobScreen.owner.equals(player.getUniqueId())
                    ||jobScreens.get(jobScreen.owner)!=jobScreen||jobScreen.expired||jobScreen.claimed
                    ||e.getClickedInventory()!=jobScreen.inventory||e.getClick()!=ClickType.LEFT)return;
            Runnable action=jobScreen.buttons.get(e.getRawSlot());if(action==null)return;
            jobScreen.claimed=true;
            Bukkit.getScheduler().runTask(plugin,()->{
                if(!player.isOnline()||jobScreen.expired||jobScreens.get(jobScreen.owner)!=jobScreen
                        ||!com.example.dunegui.NativeMenus.isCurrent(player,jobScreen.inventory))return;
                releaseJobScreen(jobScreen);player.closeInventory();action.run();
            });return;
        }
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
    private Inventory jobScreen(Player player,String title) {
        Inventory screen=Bukkit.createInventory(null,54,color(title));
        for(int slot=0;slot<54;slot++)screen.setItem(slot,item(player,"GRAY_STAINED_GLASS_PANE"," ",List.of()));
        return screen;
    }
    private void jobScreen(Player player,String title,Inventory screen,Map<Integer,Runnable> buttons) {
        // Server-only inventory models are rendered by the shared owner-bound virtual navigator.
        releaseJobScreen(jobScreens.get(player.getUniqueId()));
        opened.remove(player.getUniqueId());actions.remove(player.getUniqueId());
        JobScreen session=new JobScreen(player.getUniqueId(),screen,buttons);
        jobScreens.put(session.owner,session);jobInventories.put(screen,session);com.example.dunegui.NativeMenus.inventory(plugin,player,title,screen,buttons,()->!session.expired&&jobScreens.get(session.owner)==session);
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            if(jobScreens.get(session.owner)!=session)return;
            if(player.isOnline()&&com.example.dunegui.NativeMenus.isCurrent(player,screen))player.closeInventory();
            releaseJobScreen(session);
        },2400L);
    }
    private void releaseJobScreen(JobScreen session){
        if(session==null)return;session.expired=true;jobScreens.remove(session.owner,session);jobInventories.remove(session.inventory,session);
    }
    @EventHandler public void closeJobScreen(InventoryCloseEvent event){
        releaseJobScreen(jobInventories.get(event.getInventory()));
    }
    private void confirmJobAction(Player player,String title,List<String> details,Runnable confirm,Runnable back) {
        Inventory screen=jobScreen(player,title);Map<Integer,Runnable> buttons=new HashMap<>();
        screen.setItem(22,item(player,"WRITABLE_BOOK","&6"+title,details));
        screen.setItem(30,item(player,"LIME_DYE","&aConfirm",List.of("&7Rechecks the current job before acting")));buttons.put(30,confirm);
        screen.setItem(32,item(player,"ARROW","&eBack",List.of("&7No job changes")));buttons.put(32,back);
        jobScreen(player,title,screen,buttons);
    }
    private void runJobAction(Player player,String command,Runnable returnTo) {
        player.closeInventory();plugin.runGuiCommand(player,command);returnTo.run();
    }
    private void openPlayerJobs(Player player,int requestedPage) {
        List<JobMenuData.Listing> listings=JobMenuData.listings(plugin.websiteExchangeSnapshot());
        int page=JobMenuData.page(requestedPage,listings.size()),start=page*21,end=Math.min(start+21,listings.size());
        Inventory screen=jobScreen(player,"&6Player Spice Jobs");Map<Integer,Runnable> buttons=new HashMap<>();
        screen.setItem(4,item(player,"WRITABLE_BOOK","&6Player Spice Jobs",List.of("&7Harvesting jobs sponsored by players","&7Worker: 15% / sponsor: 85%","&7Listings: "+listings.size()+" · Page "+(page+1))));
        for(int index=start;index<end;index++){
            JobMenuData.Listing listing=listings.get(index);boolean own=listing.owner().equals(player.getUniqueId());
            int slot=JobMenuData.cardSlot(index-start);
            List<String> lore=new ArrayList<>(List.of("&7Spice remaining: &f"+listing.remaining(),"&7Worker share: &a15%","&7Sponsor share: &685%","&7Status: "+(listing.available()?"&aAvailable":"&eWorker assigned")));
            lore.add(own?"&eYour listing":listing.available()?"&eClick to review and accept":"&7Currently unavailable");
            screen.setItem(slot,item(player,own?"GOLD_INGOT":listing.available()?"PAPER":"GRAY_DYE","&e"+listing.name(),lore));
            if(!own&&listing.available())buttons.put(slot,()->confirmJobAction(player,"Accept Spice Job",List.of("&7Sponsor: &f"+listing.name(),"&7Remaining: &f"+listing.remaining()+" spice","&7Worker receives 15% of earnings","&7Accepting travels to Arrakduin","&7The job is rechecked when you confirm"),()->runJobAction(player,"job accept "+listing.owner(),()->openPlayerJobs(player,page)),()->openPlayerJobs(player,page)));
        }
        if(listings.isEmpty())screen.setItem(22,item(player,"PAPER","&eNo player jobs listed",List.of("&7Post a spice quota using the buttons below","&7Other players can accept your listing")));
        for(var option:Map.of(45,100,46,500,47,1000).entrySet()){
            int quota=option.getValue();screen.setItem(option.getKey(),item(player,"PAPER","&ePost "+quota+" spice",List.of("&7Create one player-sponsored harvest job","&7Your share: 85% / worker: 15%","&eClick to review")));
            buttons.put(option.getKey(),()->confirmJobAction(player,"Post Spice Job",List.of("&7Quota: &f"+quota+" spice","&7One active listing per sponsor","&7Worker receives 15%, you receive 85%","&7No funds or items are taken by this menu"),()->runJobAction(player,"job post "+quota,()->openPlayerJobs(player,0)),()->openPlayerJobs(player,page)));
        }
        screen.setItem(48,item(player,"BARRIER","&cCancel your listing",List.of("&7Unavailable while a worker is assigned")));buttons.put(48,()->confirmJobAction(player,"Cancel Your Listing",List.of("&7Cancels only your own available listing","&7An assigned worker prevents cancellation"),()->runJobAction(player,"job cancel",()->openPlayerJobs(player,0)),()->openPlayerJobs(player,page)));
        screen.setItem(50,item(player,"OAK_DOOR","&eLeave your job",List.of("&7Leave the player job you accepted")));buttons.put(50,()->confirmJobAction(player,"Leave Spice Job",List.of("&7Stops your current player-job assignment","&7Already earned payments are unchanged"),()->runJobAction(player,"job finish",()->openPlayerJobs(player,page)),()->openPlayerJobs(player,page)));
        if(page>0){screen.setItem(36,item(player,"ARROW","&ePrevious page",List.of()));buttons.put(36,()->openPlayerJobs(player,page-1));}
        if(end<listings.size()){screen.setItem(44,item(player,"ARROW","&eNext page",List.of()));buttons.put(44,()->openPlayerJobs(player,page+1));}
        screen.setItem(49,item(player,"CLOCK","&eRefresh jobs",List.of("&7Read current listings and availability")));buttons.put(49,()->openPlayerJobs(player,page));
        screen.setItem(51,item(player,"EMERALD","&eServer contracts",List.of("&7Hourly Koopkring offers")));buttons.put(51,()->openServerJobs(player));
        screen.setItem(53,item(player,"ARROW","&ePlayer menu",List.of()));buttons.put(53,()->open(player,"main"));jobScreen(player,"&6Player Spice Jobs",screen,buttons);
    }
    private void openServerJobs(Player player) {
        List<ServerJobs.OfferView> offers=serverJobs.offerViews();ServerJobs.ActiveView active=serverJobs.activeView(player);
        Inventory screen=jobScreen(player,"&6Koopkring Contracts");Map<Integer,Runnable> buttons=new HashMap<>();
        screen.setItem(4,item(player,"EMERALD","&6Koopkring Contracts",List.of("&7Hourly server harvesting contracts","&7One active contract at a time","&7Review pay, duration and failure fine")));
        for(int index=0;index<offers.size()&&index<21;index++){
            ServerJobs.OfferView offer=offers.get(index);int slot=JobMenuData.cardSlot(index);
            screen.setItem(slot,item(player,"PAPER","&e"+offer.title(),List.of("&7Harvest: &f"+offer.required()+" spice","&7Pay: &6"+offer.pay()+" Dunemarks","&7Time limit: &f"+offer.minutes()+" minutes","&7Failure fine: &c"+offer.fine()+" Dunemarks (5%)",active==null?"&eClick to review and accept":"&7Finish your current contract first")));
            if(active==null)buttons.put(slot,()->confirmJobAction(player,"Accept Server Contract",List.of("&6"+offer.title(),"&7Harvest "+offer.required()+" spice in "+offer.minutes()+" minutes","&7Completion pay: &6"+offer.pay()+" Dunemarks","&cFailure fine: "+offer.fine()+" Dunemarks (5%)","&7Current offer is rechecked on confirmation"),()->runJobAction(player,"serverjobs accept "+offer.id(),()->openServerJobs(player)),()->openServerJobs(player)));
        }
        if(offers.isEmpty())screen.setItem(22,item(player,"PAPER","&eNo contracts available",List.of("&7Check again after the next hourly rotation")));
        screen.setItem(49,item(player,"CLOCK",active==null?"&eNo active contract":"&6"+active.title(),active==null?List.of("&7Choose an available contract above"):List.of("&7Progress: &f"+active.harvested()+" / "+active.required()+" spice","&7Time left: &f"+(active.secondsLeft()/60)+"m "+(active.secondsLeft()%60)+"s","&7Completion pay: &6"+active.pay()+" Dunemarks","&eClick to refresh progress")));buttons.put(49,()->openServerJobs(player));
        screen.setItem(45,item(player,"WRITABLE_BOOK","&ePlayer spice jobs",List.of("&7Browse player-sponsored harvesting work")));buttons.put(45,()->openPlayerJobs(player,0));
        screen.setItem(53,item(player,"ARROW","&ePlayer menu",List.of()));buttons.put(53,()->open(player,"main"));jobScreen(player,"&6Koopkring Contracts",screen,buttons);
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
        releaseJobScreen(jobScreens.get(player.getUniqueId()));opened.put(player.getUniqueId(),"appointments");actions.put(player.getUniqueId(),Map.of(22,"open:main"));com.example.dunegui.NativeMenus.inventory(plugin,player,color("&6Landsraad appointments"),inventory,Map.of(22,()->open(player,"main")));
    }
    private String officeHolder(Map<String,Object> offices,String key,String empty){Object value=offices.get(key);return value==null?empty:com.example.dunegui.PublicNames.text(String.valueOf(value));}
    void drag(InventoryDragEvent e) {
        if(jobInventories.containsKey(e.getView().getTopInventory())){e.setCancelled(true);return;}
        if(!(e.getWhoClicked() instanceof Player p)) return;
        String page=opened.get(p.getUniqueId());
        if(page==null) return;
        if(!e.getView().getTitle().equals(replace(page.equals("appointments")?"&6Landsraad appointments":config.getString("menus."+page+".title","Dune | "+page),p))) return;
        if(e.getRawSlots().stream().anyMatch(slot->slot<e.getView().getTopInventory().getSize())) e.setCancelled(true);
    }
    private void execute(Player p,String action) {
        if(action.equals("clanshop")||action.equals("officekits")||action.equals("quests")||action.equals("personalbank")||action.equals("atelier")) {
            if(!p.isOnline())return;
            String targetName=action.equals("atelier")?"DuneAtelier":action.equals("personalbank")?"DuneBank":action.equals("clanshop")?"DuneClans":action.equals("officekits")?"DuneItems":"DuneQuests";
            org.bukkit.plugin.Plugin target=Bukkit.getPluginManager().getPlugin(targetName);
            if(target==null||!target.isEnabled()){p.sendMessage(ChatColor.RED+"This navigator service is unavailable. Please try again later.");return;}
            String permission=action.equals("atelier")?"dune.atelier.view":action.equals("personalbank")?"dunebank.use":action.equals("clanshop")?"dune.clans.view":action.equals("quests")?"dune.quests.view":"";
            if(!permission.isEmpty()&&!p.hasPermission(permission)){p.sendMessage(ChatColor.RED+"You do not have permission to open this service.");return;}
            String command=action.equals("atelier")?"duneatelier":action.equals("personalbank")?"dunebank":action.equals("clanshop")?"duneclan":action.equals("officekits")?"duneitems offices":"dunequests";
            p.closeInventory();if(!p.performCommand(command))p.sendMessage(ChatColor.RED+"This navigator service is unavailable. Please try again later.");return;
        }
        if(action.startsWith("open:")) { open(p,action.substring(5).trim()); return; }
        if(action.equals("itemexchange")) { p.closeInventory();if(!p.performCommand("exchange"))p.sendMessage(ChatColor.RED+"The item marketplace is unavailable. Please try again later.");return; }
        if(action.equals("tickets")) { ModerationGui.ticketsFor(plugin,p);return; }
        if(action.equals("staffdesk")) { if(!StaffMenuBridge.available(p)){p.sendMessage(ChatColor.RED+"Staff permissions required.");return;}p.closeInventory();p.performCommand("dunemenus:dunemenus staff");return; }
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

