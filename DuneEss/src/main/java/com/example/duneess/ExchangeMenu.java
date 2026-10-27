package com.example.duneess;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

/** Native menus share the existing SQL-backed trading operations. */
@SuppressWarnings("deprecation")
final class ExchangeMenu implements Listener, CommandExecutor, TabCompleter {
    private static final List<String> CATEGORIES=List.of("ores","spice","building","equipment","farming","misc");
    private static final List<String> PLANETS=List.of("all","arrakis","giedi_prime","caladan","kaitain","sigma_draconis_iv");
    private static final List<Material> ICONS=List.of(Material.IRON_PICKAXE,Material.ORANGE_DYE,Material.BRICKS,Material.IRON_SWORD,Material.WHEAT,Material.CHEST);
    private final DuneEss plugin;
    private final ExchangeStore store;
    private final Map<UUID,String> planets=new HashMap<>();
    private boolean ready;
    static final class Screen implements InventoryHolder {
        final UUID owner;
        final Map<Integer,Runnable> actions=new HashMap<>();
        Inventory inventory;
        String title;
        Screen(UUID owner) { this.owner=owner; }
        @Override public Inventory getInventory() { return inventory; }
    }
    ExchangeMenu(DuneEss plugin) {
        this.plugin=plugin;
        this.store=new ExchangeStore(plugin::connectEconomyDatabase);
    }
    void register() {
        PluginCommand command=Objects.requireNonNull(plugin.getCommand("exchange"));
        command.setExecutor(this); command.setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        try { store.initialize(); ready=true; }
        catch(SQLException ex) { plugin.getLogger().warning("Koopkring is offline until the shared PostgreSQL economy is connected."); }
    }
    private boolean connected(Player p) {
        // Retry after a transient startup outage; never trade against a fallback local wallet.
        if(!ready) try { store.initialize(); ready=true; } catch(SQLException ignored) { }
        if(!ready) p.sendMessage(ChatColor.YELLOW+"Koopkring is not connected. Ask an operator to configure DuneCore PostgreSQL and restart DuneEss. No items or Dunemarks were taken.");
        return ready;
    }
    private String faction(Player p) {
        Plugin core=Bukkit.getPluginManager().getPlugin("DuneCore");
        if(core!=null) try {
            String house=String.valueOf(core.getClass().getMethod("factionOf",Player.class).invoke(core,p));
            if(List.of("Fremen","Atreides","Harkonnen","Corrino","Ordos").contains(house)) return house;
        } catch(ReflectiveOperationException ignored) { }
        return "Vaerkring";
    }
    private ChatColor tint(Player p) {
        return switch(faction(p)) {
            case "Atreides" -> ChatColor.AQUA;
            case "Harkonnen" -> ChatColor.RED;
            case "Corrino" -> ChatColor.GOLD;
            case "Fremen" -> ChatColor.YELLOW;
            case "Ordos" -> ChatColor.GREEN;
            default -> ChatColor.LIGHT_PURPLE;
        };
    }
    private ItemStack icon(Material material,String name,String... lore) {
        ItemStack item=new ItemStack(material); ItemMeta meta=item.getItemMeta();
        meta.setDisplayName(name); meta.setLore(Arrays.asList(lore)); item.setItemMeta(meta); return item;
    }
    private Screen screen(Player p,String title) {
        Screen s=new Screen(p.getUniqueId());
        s.title="Koopkring | "+title;
        s.inventory=Bukkit.createInventory(s,54,tint(p)+"Koopkring | "+title);
        Material glass=switch(faction(p)) {
            case "Atreides" -> Material.CYAN_STAINED_GLASS_PANE;
            case "Harkonnen" -> Material.RED_STAINED_GLASS_PANE;
            case "Corrino" -> Material.YELLOW_STAINED_GLASS_PANE;
            case "Ordos" -> Material.GREEN_STAINED_GLASS_PANE;
            case "Fremen" -> Material.ORANGE_STAINED_GLASS_PANE;
            default -> Material.PURPLE_STAINED_GLASS_PANE;
        };
        for(int i=0;i<54;i++) if(i<9||i>=45||i%9==0||i%9==8)
            s.inventory.setItem(i,icon(glass," "));
        s.inventory.setItem(47,com.example.dunegui.DunemarkItems.icon(ChatColor.GOLD+"Dunemarks",List.of(ChatColor.GRAY+"Wallet: "+plugin.balanceLabel(p),ChatColor.GRAY+"Display icon only; balances stay in the ledger.")));
        button(s,49,Material.BARRIER,"Back to Exchange",()->home(p)); return s;
    }
    private void button(Screen s,int slot,Material material,String title,Runnable action,String... lore) {
        s.inventory.setItem(slot,icon(material,ChatColor.GOLD+title,lore)); s.actions.put(slot,action);
    }
    private void home(Player p) {
        Screen s=screen(p,faction(p));
        for(int i=0;i<CATEGORIES.size();i++) {
            String category=CATEGORIES.get(i);
            button(s,10+i,ICONS.get(i),category.toUpperCase(Locale.ROOT),()->browse(p,category,0,false,false));
        }
        s.inventory.setItem(29,com.example.dunegui.DunemarkItems.icon(ChatColor.GOLD+"Buy / All Items",List.of(ChatColor.GRAY+"Pay with Dunemarks from your wallet.")));
        s.actions.put(29,()->browse(p,"all",0,false,false));
        button(s,31,Material.CHEST,"Sell Held Item",()->{
            p.closeInventory(); p.sendMessage(ChatColor.YELLOW+"Hold the item to list, then /exchange sell <amount> <price-each>. Example: /exchange sell 16 25");
        },"Tagged Dune items and their attributes are preserved.","Price is for ONE item.");
        button(s,33,Material.BOOK,"My Listings",()->browse(p,"all",0,true,false),"Cancel and reclaim your unsold items.");
        button(s,40,Material.COMPASS,"Planet: "+planets.getOrDefault(p.getUniqueId(),"all"),()->planetMenu(p));
        button(s,38,Material.PAPER,"Trading Guide",()->{
            p.sendMessage(ChatColor.YELLOW+"/exchange sell <amount> <price> · /exchange buy <id> · /exchange cancel <id> · /exchange mine");
            p.sendMessage(ChatColor.GRAY+"Purchases buy one item. Listings reserve the offered items. Website demo offers cannot be purchased.");
        });
        if(p.hasPermission("dune.admin")) button(s,42,Material.COMMAND_BLOCK,"Exchange Administration",()->admin(p));
        button(s,49,Material.BARRIER,"Close",p::closeInventory);
        com.example.dunegui.NativeMenus.inventory(plugin,p,s.title,s.inventory,s.actions);
    }
    private void planetMenu(Player p) {
        Screen s=screen(p,"Planet Filter");
        for(int i=0;i<PLANETS.size();i++) {
            String planet=PLANETS.get(i);
            button(s,11+i,Material.COMPASS,planet,()->{planets.put(p.getUniqueId(),planet); browse(p,"all",0,false,false);});
        }
        com.example.dunegui.NativeMenus.inventory(plugin,p,s.title,s.inventory,s.actions);
    }
    private void admin(Player p) {
        if(!p.hasPermission("dune.admin")) return;
        Screen s=screen(p,"Administration");
        button(s,20,Material.BOOK,"All Listings",()->browse(p,"all",0,false,true),"All planets. Read-only inspection; normal ownership rules apply.");
        button(s,24,Material.REDSTONE,"Check Database",()->{
            if(connected(p)) p.sendMessage(ChatColor.GREEN+"Shared Koopkring database connected.");
        });
        com.example.dunegui.NativeMenus.inventory(plugin,p,s.title,s.inventory,s.actions);
    }
    private ItemStack decode(ExchangeStore.Listing offer) {
        ItemStack item;
        if(offer.payload()!=null&&!offer.payload().isBlank()) item=ItemStack.deserializeBytes(Base64.getDecoder().decode(offer.payload()));
        else {
            Material material=Material.matchMaterial(offer.material());
            if(material==null||material.isAir()||!material.isItem()) throw new IllegalArgumentException("This item is unsupported on this server; contact an operator.");
            item=new ItemStack(material);
        }
        item.setAmount(1); return item;
    }
    private void browse(Player p,String category,int page,boolean mine,boolean admin) {
        if(admin&&!p.hasPermission("dune.admin")) return;
        if(!connected(p)) return;
        execute(p,()->{
            int current=Math.max(0,page);
            String planet=mine||admin?"all":planets.getOrDefault(p.getUniqueId(),"all");
            List<ExchangeStore.Listing> rows=store.browse(category,planet,mine?p.getUniqueId():null,current);
            Screen s=screen(p,(mine?"My Listings":admin?"All Listings":category)+" · "+(current+1));
            int slot=10;
            for(ExchangeStore.Listing row:rows.subList(0,Math.min(28,rows.size()))) {
                while(slot%9==0||slot%9==8) slot++;
                ItemStack item;
                try { item=decode(row); } catch(RuntimeException ex) { item=new ItemStack(Material.PAPER); }
                ItemMeta meta=item.getItemMeta();
                meta.setDisplayName(ChatColor.GOLD+"#"+row.id()+" "+(row.displayName()==null?row.material():row.displayName()));
                meta.setLore(List.of("Quantity: "+row.quantity(),"Each: "+row.price()+" Dunemarks","Seller: "+row.sellerName(),"Planet: "+row.origin(),
                    row.seller().equals(p.getUniqueId())?"Click to reclaim unsold items":"Click to review purchase"));
                item.setItemMeta(meta); s.inventory.setItem(slot,item);
                s.actions.put(slot,()->confirm(p,row.id(),row.seller().equals(p.getUniqueId()))); slot++;
            }
            if(rows.isEmpty()) s.inventory.setItem(22,icon(Material.PAPER,"No matching listings","Try another planet or list a held item."));
            if(current>0) button(s,45,Material.ARROW,"Previous",()->browse(p,category,current-1,mine,admin));
            if(rows.size()>28) button(s,53,Material.ARROW,"Next",()->browse(p,category,current+1,mine,admin));
            com.example.dunegui.NativeMenus.inventory(plugin,p,s.title,s.inventory,s.actions);
        });
    }
    private void confirm(Player p,long id,boolean reclaim) {
        execute(p,()->{
            ExchangeStore.Listing row=store.find(id);
            if(reclaim&&!row.seller().equals(p.getUniqueId())) throw new IllegalArgumentException("You can reclaim only your own listings.");
            Screen s=screen(p,reclaim?"Reclaim Listing":"Confirm Purchase");
            s.inventory.setItem(22,icon(decode(row).getType(),"#"+id+" "+(row.displayName()==null?row.material():row.displayName()),
                "Seller: "+row.sellerName(),reclaim?"Return "+row.quantity()+" unsold items":"Buy ONE for "+row.price()+" Dunemarks"));
            button(s,30,Material.LIME_CONCRETE,reclaim?"Confirm Reclaim":"Confirm Purchase",()->{
                p.closeInventory(); if(reclaim) cancel(p,id); else buy(p,id);
            });
            button(s,32,Material.RED_CONCRETE,"Never Mind",()->home(p)); com.example.dunegui.NativeMenus.inventory(plugin,p,s.title,s.inventory,s.actions);
        });
    }
    private boolean fits(Player p,ItemStack item,int amount) {
        int remaining=amount;
        for(ItemStack slot:p.getInventory().getStorageContents()) {
            if(slot==null||slot.getType().isAir()) remaining-=item.getMaxStackSize();
            else if(slot.isSimilar(item)) remaining-=Math.max(0,item.getMaxStackSize()-slot.getAmount());
            if(remaining<=0) return true;
        }
        return false;
    }
    private void deliver(Player p,ItemStack item,int amount) {
        item.setAmount(amount);
        Map<Integer,ItemStack> excess=p.getInventory().addItem(item);
        // Capacity is checked immediately before the transaction on this same server thread.
        if (!excess.isEmpty()) throw new IllegalArgumentException("Inventory changed; the trade was rolled back. Make room and retry.");
    }
    private void sell(Player p,int amount,BigDecimal price) throws SQLException {
        ExchangeStore.validate(amount,price);
        ItemStack held=p.getInventory().getItemInMainHand();
        if(held.getType().isAir()||amount>held.getAmount()) throw new IllegalArgumentException("Hold at least that many items in your main hand.");
        // Never let operator tools become tradeable through the public exchange.
        if(held.hasItemMeta()) for(NamespacedKey key:held.getItemMeta().getPersistentDataContainer().getKeys())
            if(key.getKey().contains("admin")) throw new IllegalArgumentException("Administrative tools cannot be listed.");
        ItemStack unit=held.clone(); unit.setAmount(1);
        String name=unit.hasItemMeta()&&unit.getItemMeta().hasDisplayName()?ChatColor.stripColor(unit.getItemMeta().getDisplayName()):unit.getType().name();
        if(name.length()>120) name=name.substring(0,120);
        final String displayName=name;
        atomic(p, scoped -> {
            scoped.list(p.getUniqueId(),p.getName(),origin(p),category(unit),unit.getType().name(),amount,price,
                Base64.getEncoder().encodeToString(unit.serializeAsBytes()),displayName);
            held.setAmount(held.getAmount()-amount);
        });
        p.sendMessage(ChatColor.GREEN+"Listed "+amount+" "+name+" at "+price+" Dunemarks each.");
    }
    private String origin(Player p) {
        String world=p.getWorld().getName().toLowerCase(Locale.ROOT).replace('-','_');
        for(String planet:PLANETS) if(!planet.equals("all")&&world.contains(planet)) return planet;
        return plugin.localPlanet();
    }
    static String category(ItemStack item) {
        if(item.hasItemMeta()) {
            String kind=item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey("dunerealms","item"),PersistentDataType.STRING);
            if(kind!=null&&kind.toLowerCase(Locale.ROOT).contains("spice")) return "spice";
        }
        String n=item.getType().name();
        if(n.contains("ORE")||n.contains("INGOT")||n.contains("DEBRIS")||n.contains("DIAMOND")||n.contains("EMERALD")||n.contains("COAL")||n.contains("REDSTONE")||n.contains("RAW_")||n.contains("LAPIS")||n.contains("QUARTZ")) return "ores";
        if(n.contains("SWORD")||n.contains("PICKAXE")||n.endsWith("AXE")||n.contains("HELMET")||n.contains("CHESTPLATE")||n.contains("LEGGINGS")||n.contains("BOOTS")||n.contains("ELYTRA")||n.contains("BOW")||n.contains("SHOVEL")||n.contains("HOE")||n.equals("SHIELD")||n.equals("TRIDENT")||n.equals("FISHING_ROD")||n.equals("ENCHANTED_BOOK")) return "equipment";
        if(item.getType().isEdible()||n.contains("WHEAT")||n.contains("SEED")||n.contains("SAPLING")) return "farming";
        return item.getType().isBlock()?"building":"misc";
    }
    private void buy(Player p,long id) {
        if(!connected(p)) return;
        execute(p,()->{
            ExchangeStore.Listing before=store.find(id); ItemStack item=decode(before);
            if(!fits(p,item,1)) throw new IllegalArgumentException("Make room in your inventory first.");
            atomic(p, scoped -> {
                scoped.buy(p.getUniqueId(),p.getName(),id);
                deliver(p,item,1);
            });
            p.sendMessage(ChatColor.GREEN+"Purchased one item for "+before.price()+" Dunemarks.");
        });
    }
    private void cancel(Player p,long id) {
        if(!connected(p)) return;
        execute(p,()->{
            ExchangeStore.Listing before=store.find(id); ItemStack item=decode(before);
            if(!fits(p,item,before.quantity())) throw new IllegalArgumentException("Make room for all "+before.quantity()+" items first.");
            atomic(p, scoped -> {
                ExchangeStore.Listing cancelled=scoped.cancel(p.getUniqueId(),id);
                deliver(p,item,cancelled.quantity());
            });
            p.sendMessage(ChatColor.GREEN+"Listing cancelled. Unsold items returned.");
        });
    }
    private interface Trade { void run(ExchangeStore scoped) throws SQLException; }
    private void atomic(Player p, Trade trade) throws SQLException {
        Plugin core=Bukkit.getPluginManager().getPlugin("DuneCore");
        if (core==null) throw new SQLException("DuneCore network profiles are required");
        try {
            java.util.function.Consumer<java.sql.Connection> work = connection -> {
                try { trade.run(ExchangeStore.bound(connection)); }
                catch (SQLException ex) { throw new java.util.concurrent.CompletionException(ex); }
            };
            core.getClass().getMethod("transactNetworkInventory",Player.class,java.util.function.Consumer.class).invoke(core,p,work);
        } catch (ReflectiveOperationException ex) {
            Throwable cause=ex instanceof java.lang.reflect.InvocationTargetException ? ex.getCause() : ex;
            if (cause instanceof java.util.concurrent.CompletionException && cause.getCause()!=null) cause=cause.getCause();
            if (cause instanceof IllegalArgumentException invalid) throw invalid;
            if (cause instanceof SQLException sql) throw sql;
            throw new SQLException("Network inventory transaction unavailable; install matching Core and Essentials",cause);
        }
    }
    private interface Operation { void run() throws SQLException; }
    private void execute(Player p,Operation action) {
        try { action.run(); }
        catch(IllegalArgumentException ex) { p.sendMessage(ChatColor.YELLOW+ex.getMessage()); }
        catch(SQLException ex) {
            ready=false; plugin.getLogger().warning("Koopkring SQL operation failed: "+ex.getSQLState());
            p.sendMessage(ChatColor.RED+"The market database is unavailable. Please try again later.");
        }
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!(sender instanceof Player p)) { sender.sendMessage("Open the Koopkring exchange in-game with /exchange."); return true; }
        if(args.length==0) { home(p); return true; }
        String action=args[0].toLowerCase(Locale.ROOT);
        if(action.equals("admin")) { if(p.hasPermission("dune.admin")) admin(p); else p.sendMessage(ChatColor.RED+"Operators only."); return true; }
        if(action.equals("help")) { home(p); return true; }
        if(!connected(p)) return true;
        execute(p,()->{
            try {
                switch(action) {
                    case "sell" -> { if(args.length!=3) throw new IllegalArgumentException("/exchange sell <amount> <price-each>"); sell(p,Integer.parseInt(args[1]),new BigDecimal(args[2])); }
                    case "buy","cancel" -> { if(args.length!=2) throw new IllegalArgumentException("/exchange "+action+" <listing-id>"); long id=Long.parseLong(args[1]); if(id<1) throw new IllegalArgumentException("Listing IDs are positive numbers."); confirm(p,id,action.equals("cancel")); }
                    case "mine" -> browse(p,"all",0,true,false);
                    default -> p.sendMessage(ChatColor.YELLOW+"Use /exchange, /exchange sell <amount> <price>, /exchange buy <id>, /exchange cancel <id>, or /exchange mine.");
                }
            } catch(NumberFormatException ex) { throw new IllegalArgumentException("Enter a valid amount, price and listing ID."); }
        });
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        if(args.length!=1) return List.of();
        return List.of("help","sell","buy","cancel","mine","admin").stream()
            .filter(s->!s.equals("admin")||sender.hasPermission("dune.admin"))
            .filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
    @EventHandler public void click(InventoryClickEvent e) {
        if(!(e.getView().getTopInventory().getHolder() instanceof Screen screen)) return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p)||!screen.owner.equals(p.getUniqueId())
                ||e.getClickedInventory()!=e.getView().getTopInventory()||e.getClick()==ClickType.DOUBLE_CLICK) return;
        Runnable action=screen.actions.get(e.getRawSlot());
        if(action!=null) Bukkit.getScheduler().runTask(plugin,()->{
            if(p.isOnline()&&p.getOpenInventory().getTopInventory()==screen.inventory) action.run();
        });
    }
    @EventHandler public void drag(InventoryDragEvent e) {
        if(e.getView().getTopInventory().getHolder() instanceof Screen) e.setCancelled(true);
    }
    @EventHandler public void quit(PlayerQuitEvent e) { planets.remove(e.getPlayer().getUniqueId()); }
}
