package com.example.duneitems;

import com.example.dunegui.NativeMenus;
import com.example.dunegui.PublicNames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;

/** Office items are bound to an elected person AND one exact authoritative term. */
final class OfficeGear implements Listener {
 static final NamespacedKey OFFICE=new NamespacedKey("dunerealms","political_office"),TERM=new NamespacedKey("dunerealms","political_term"),OWNER=new NamespacedKey("dunerealms","political_owner"),QUALITY=new NamespacedKey("dunerealms","quality");
 private final DuneItems plugin;
 private boolean ready;
 private record Snapshot(int tick,Map<String,String> offices){}
 private final Map<UUID,Snapshot> snapshots=new HashMap<>();
 OfficeGear(DuneItems plugin){this.plugin=plugin;}
 private Plugin core(){Plugin p=plugin.getServer().getPluginManager().getPlugin("DuneCore");return p!=null&&p.isEnabled()?p:null;}
 void start(){
  plugin.getServer().getPluginManager().registerEvents(this,plugin);
  plugin.getServer().getScheduler().runTaskTimer(plugin,()->{for(Player p:plugin.getServer().getOnlinePlayers())audit(p);},1L,100L);
  Plugin core=core();if(core==null)return;
  try {
   Object db=core.getClass().getMethod("sharedDatabase").invoke(core);
   try(Connection c=(Connection)db.getClass().getMethod("connect").invoke(db);Statement s=c.createStatement()){
    OfficeBlueprint.initialize(s);
    s.executeUpdate("CREATE TABLE IF NOT EXISTS dune_office_kit_claims (office_key VARCHAR(96) NOT NULL,term_id VARCHAR(190) NOT NULL,owner_uuid VARCHAR(36) NOT NULL,claimed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),PRIMARY KEY(office_key,term_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
   }
   ready=true;
  }catch(Exception ex){plugin.getLogger().warning("Office kit receipts unavailable; claims disabled.");}

 }
 @SuppressWarnings("unchecked") Map<String,String> offices(Player player,boolean fresh){
  int tick=Bukkit.getCurrentTick();Snapshot old=snapshots.get(player.getUniqueId());
  if(!fresh&&old!=null&&old.tick()==tick)return old.offices();
  Map<String,String> answer=Map.of();Plugin core=core();
  if(core!=null)try{
   Object raw=core.getClass().getMethod("currentPoliticalOffices",UUID.class).invoke(core,player.getUniqueId());
   if(raw instanceof Map<?,?> map){Map<String,String> valid=new LinkedHashMap<>();for(var e:map.entrySet())if(e.getKey() instanceof String key&&e.getValue() instanceof String term&&validOffice(key)&&!term.isBlank()&&term.length()<=190)valid.put(key,term);answer=Map.copyOf(valid);}
  }catch(Exception ignored){}
  snapshots.put(player.getUniqueId(),new Snapshot(tick,answer));return answer;
 }
 static boolean validOffice(String office){return office.equals("emperor")||office.matches("(house-leader|water-steward|spice-steward):(Fremen|Harkonnen|Corrino|Atreides|Ordos)");}
 static boolean tagged(ItemStack item){return item!=null&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(OFFICE,PersistentDataType.STRING);}
 boolean eligible(Player player,ItemStack item){return !tagged(item)||eligible(player,item,offices(player,false));}
 static boolean eligible(Player player,ItemStack item,Map<String,String> offices){
  if(item==null||!item.hasItemMeta())return true;
  var pdc=item.getItemMeta().getPersistentDataContainer();
  if(!pdc.has(OFFICE,PersistentDataType.STRING))return true;
  String office=pdc.get(OFFICE,PersistentDataType.STRING),term=pdc.get(TERM,PersistentDataType.STRING),owner=pdc.get(OWNER,PersistentDataType.STRING);
  return player.getUniqueId().toString().equals(owner)&&term!=null&&term.equals(offices.get(office));
 }
 void menu(Player player){
  Map<String,String> offices=offices(player,true);List<NativeMenus.Choice> choices=new ArrayList<>();
  for(var office:offices.entrySet())choices.add(new NativeMenus.Choice(officeTitle(office.getKey())+" - "+PublicNames.text("Stillsuit"),"Uncommon - Four pieces - Bound to your elected term - One kit per term",()->claim(player,office.getKey(),office.getValue()),officeArt(office.getKey())));
  choices.add(new NativeMenus.Choice("Review worn office gear","Recheck your current office authority",()->{audit(player);menu(player);}));
  NativeMenus.show(plugin,player,"Imperial Regalia",offices.isEmpty()?"No current elected office. House stewardship requires its current elected leader and an active imperial appointment.":"Current elected offices only. Regalia cannot be used after the term ends.",choices);
 }
 static String officeTitle(String office){
  if(office.equals("emperor"))return "Emperor";
  String[] parts=office.split(":",2);if(parts.length!=2||!validOffice(office))throw new IllegalArgumentException("Unknown office");
  String title=switch(parts[0]){case "house-leader"->"House Leader";case "water-steward"->"Water Steward";case "spice-steward"->"Spice Steward";default->throw new IllegalArgumentException("Unknown office");};return title+" - "+PublicNames.text(parts[1]);
 }
 static Color officeColor(String office){return office.equals("emperor")?Color.fromRGB(87,31,130):office.startsWith("water-steward:")?Color.fromRGB(34,145,185):office.startsWith("spice-steward:")?Color.fromRGB(210,135,25):null;}
 private static String officeArt(String office){
  String house=office.contains(":")?office.substring(office.indexOf(':')+1):"";
  String file=switch(house){case "Fremen"->"faction-fremen-vrijduin-heraldic.png";case "Atreides"->"faction-atreides-aerdrecht-heraldic.png";case "Harkonnen"->"faction-harkonnen-harkveen-heraldic.png";case "Corrino"->"faction-corrino-korenholt-heraldic.png";case "Ordos"->"faction-ordos-oordwijk-heraldic.png";default->"imperial-portal.png";};return "https://www.dunerealms.com/assets/gui/"+file;
 }
 static String canonicalFaction(Object value){
  if(value instanceof String faction)for(String canonical:List.of("Fremen","Harkonnen","Corrino","Atreides","Ordos"))if(canonical.equalsIgnoreCase(faction))return canonical;
  throw new IllegalStateException("Authoritative faction unavailable");
 }
 static String kitFaction(Object actual,String office){try{return canonicalFaction(actual);}catch(IllegalStateException invalid){if("emperor".equals(office))return "Corrino";throw invalid;}}
 private List<ItemStack> kit(Player player,String office,String term,Optional<OfficeBlueprint> blueprint)throws Exception{
  Plugin core=Objects.requireNonNull(core());Object actualFaction=core.getClass().getMethod("factionOf",Player.class).invoke(core,player);
  String faction=kitFaction(actualFaction,office);
  Object raw=core.getClass().getMethod("createFactionKit",String.class).invoke(core,faction);
  if(!(raw instanceof List<?> base))throw new IllegalStateException("Kit unavailable");
  List<ItemStack> pieces=new ArrayList<>();
  for(Object value:base)if(value instanceof ItemStack original&&original.getItemMeta() instanceof LeatherArmorMeta){
   ItemStack copy=original.clone();LeatherArmorMeta meta=(LeatherArmorMeta)copy.getItemMeta();
   Color dye=blueprint.map(OfficeBlueprint::color).orElseGet(()->officeColor(office));if(dye!=null)meta.setColor(dye);
   meta.displayName(Component.text(blueprint.map(OfficeBlueprint::name).orElseGet(()->officeTitle(office)+" "+PublicNames.text("Stillsuit"))+" "+copy.getType().name().replace("LEATHER_", "").toLowerCase(Locale.ROOT),NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false));
   List<Component> lore=new ArrayList<>(meta.lore()==null?List.of():meta.lore());lore.removeIf(line->PlainTextComponentSerializer.plainText().serialize(line).toLowerCase(Locale.ROOT).startsWith("quality:"));blueprint.ifPresent(definition->definition.lore().forEach(line->lore.add(Component.text(line,NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false))));lore.add(Component.text("Quality: Uncommon",NamedTextColor.GREEN).decoration(TextDecoration.ITALIC,false));lore.add(Component.text("Elected office: "+officeTitle(office),NamedTextColor.GRAY));lore.add(Component.text("Bound to "+player.getName()+" and this elected term.",NamedTextColor.DARK_GRAY));meta.lore(lore);
   var pdc=meta.getPersistentDataContainer();pdc.set(OFFICE,PersistentDataType.STRING,office);pdc.set(TERM,PersistentDataType.STRING,term);pdc.set(OWNER,PersistentDataType.STRING,player.getUniqueId().toString());pdc.set(QUALITY,PersistentDataType.STRING,"uncommon");copy.setItemMeta(meta);pieces.add(copy);
  }
  if(pieces.size()!=4||pieces.stream().map(ItemStack::getType).distinct().count()!=4)throw new IllegalStateException("Incomplete faction armor factory");return pieces;
 }
 static void insertReceipt(Connection c,String office,String term,UUID owner)throws SQLException{
  try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_office_kit_claims(office_key,term_id,owner_uuid) VALUES(?,?,?)")){q.setString(1,office);q.setString(2,term);q.setString(3,owner.toString());if(q.executeUpdate()!=1)throw new SQLException("Receipt insert did not affect one row");}
 }
 private void claim(Player player,String office,String term){
  if(!ready||!player.isOnline()||!term.equals(offices(player,true).get(office))){player.sendMessage("Current elected authority and shared receipts are required.");return;}
  int empty=0;for(ItemStack i:player.getInventory().getStorageContents())if(i==null||i.getType().isAir())empty++;
  if(empty<4){player.sendMessage("Make four empty inventory slots before claiming.");return;}
  try{
   Plugin core=Objects.requireNonNull(core());
   Consumer<Connection> operation=c->{
    try{
     // Freeze the central authority row through receipt+inventory commit; election writers wait.
     try(PreparedStatement authority=c.prepareStatement("SELECT id FROM dune_politics WHERE id=1 FOR SHARE");ResultSet row=authority.executeQuery()){if(!row.next())throw new IllegalStateException("Political authority unavailable");}
     if(!term.equals(offices(player,true).get(office)))throw new IllegalStateException("Office changed");
     // Match website lock order: politics -> player -> definition. Avoid edit/claim deadlocks.
     try(PreparedStatement profile=c.prepareStatement("SELECT uuid FROM dune_players WHERE uuid=? FOR UPDATE")){profile.setString(1,player.getUniqueId().toString());try(ResultSet row=profile.executeQuery()){if(!row.next())throw new IllegalStateException("Shared profile unavailable");}}
     List<ItemStack> kit=kit(player,office,term,OfficeBlueprint.load(c,player.getUniqueId(),office,term));
     insertReceipt(c,office,term,player.getUniqueId());
     for(ItemStack piece:kit)if(!player.getInventory().addItem(piece).isEmpty())throw new IllegalStateException("Inventory capacity changed");
    }catch(Exception ex){throw new IllegalStateException("Kit transaction refused",ex);}
   };
   core.getClass().getMethod("transactNetworkInventory",Player.class,Consumer.class).invoke(core,player,operation);
   player.sendMessage("Your elected-term stillsuit has been claimed.");
  }catch(Exception ex){player.sendMessage("Kit unavailable or already claimed for this term. No additional kit was issued.");}
 }
 void audit(Player player){audit(player,true);}
 private void audit(Player player,boolean fresh){
  ItemStack[] armor=player.getInventory().getArmorContents();if(Arrays.stream(armor).noneMatch(OfficeGear::tagged))return;
  Map<String,String> offices=offices(player,fresh);boolean changed=false;
  for(int i=0;i<armor.length;i++)if(armor[i]!=null&&!eligible(player,armor[i],offices)){
   ItemStack piece=armor[i];armor[i]=null;changed=true;
   for(ItemStack overflow:player.getInventory().addItem(piece).values())bindDrop(player.getWorld().dropItem(player.getLocation(),overflow));
  }
  if(changed){player.getInventory().setArmorContents(armor);player.sendMessage("Office regalia was unequipped because its elected authority is no longer current.");}
 }
 private void bindDrop(Item entity){
  ItemStack item=entity.getItemStack();if(!item.hasItemMeta())return;
  String owner=item.getItemMeta().getPersistentDataContainer().get(OWNER,PersistentDataType.STRING);
  if(owner!=null)try{entity.setOwner(UUID.fromString(owner));entity.setCanMobPickup(false);entity.setUnlimitedLifetime(true);entity.setPersistent(true);}catch(IllegalArgumentException ignored){}
 }
 @EventHandler(priority=EventPriority.LOWEST) public void join(PlayerJoinEvent event){audit(event.getPlayer());}
 @EventHandler public void quit(PlayerQuitEvent event){snapshots.remove(event.getPlayer().getUniqueId());}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void click(InventoryClickEvent event){
  if(!(event.getWhoClicked() instanceof Player p))return;
  ItemStack candidate=event.getCursor();if(event.isShiftClick())candidate=event.getCurrentItem();if(event.getHotbarButton()>=0)candidate=p.getInventory().getItem(event.getHotbarButton());
  if(!tagged(candidate)&&Arrays.stream(p.getInventory().getArmorContents()).noneMatch(OfficeGear::tagged))return;
  Map<String,String> offices=offices(p,true);
  boolean equip=event.isShiftClick()||event.getSlotType()==InventoryType.SlotType.ARMOR;
  if(equip&&!eligible(p,candidate,offices))event.setCancelled(true);
  plugin.getServer().getScheduler().runTask(plugin,()->{if(p.isOnline())audit(p);});
 }
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drag(InventoryDragEvent event){if(event.getWhoClicked() instanceof Player p){if(tagged(event.getOldCursor()))offices(p,true);if(!eligible(p,event.getOldCursor()))event.setCancelled(true);plugin.getServer().getScheduler().runTask(plugin,()->{if(p.isOnline())audit(p);});}}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interact(PlayerInteractEvent event){Player p=event.getPlayer();if(tagged(event.getItem()))offices(p,true);if(!eligible(p,event.getItem()))event.setCancelled(true);}
 @EventHandler public void armorChanged(PlayerArmorChangeEvent event){
  if(!tagged(event.getNewItem()))return;
  // Post-change event: remove invalid gear immediately. Null replacement does not recurse.
  // Same-tick snapshot prevents four-piece programmatic equips from repeating authority SQL.
  audit(event.getPlayer(),false);
 }
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void dispense(BlockDispenseArmorEvent event){if(event.getTargetEntity() instanceof Player p){if(tagged(event.getItem()))offices(p,true);if(!eligible(p,event.getItem()))event.setCancelled(true);}}
 @EventHandler(priority=EventPriority.LOWEST) public void damage(EntityDamageEvent event){if(event.getEntity() instanceof Player p)audit(p);else if(event.getEntity() instanceof Item item&&tagged(item.getItemStack()))event.setCancelled(true);}
 @EventHandler(priority=EventPriority.LOWEST) public void attack(EntityDamageByEntityEvent event){if(event.getDamager() instanceof Player p)audit(p);}
 @EventHandler public void drop(PlayerDropItemEvent event){bindDrop(event.getItemDrop());}
 @EventHandler public void death(PlayerDeathEvent event){plugin.getServer().getScheduler().runTask(plugin,()->{for(Entity entity:event.getEntity().getNearbyEntities(4,4,4))if(entity instanceof Item item)bindDrop(item);});}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pickup(EntityPickupItemEvent event){ItemStack item=event.getItem().getItemStack();if(item.hasItemMeta()){String owner=item.getItemMeta().getPersistentDataContainer().get(OWNER,PersistentDataType.STRING);if(owner!=null&&!event.getEntity().getUniqueId().toString().equals(owner))event.setCancelled(true);}}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hopper(InventoryPickupItemEvent event){if(tagged(event.getItem().getItemStack()))event.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void despawn(ItemDespawnEvent event){if(event.getEntity().getItemStack().hasItemMeta()&&event.getEntity().getItemStack().getItemMeta().getPersistentDataContainer().has(OFFICE,PersistentDataType.STRING))event.setCancelled(true);}
}
