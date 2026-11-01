package com.example.duneess;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.math.*;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;

/** Deposit contact buys authenticated player spice; credit, audit and inventory share Core's transaction. */
public final class SpiceBuyer implements Listener {
 private static final NamespacedKey TAG=new NamespacedKey("dunerealms","spice-seller"),ITEM=new NamespacedKey("dunerealms","item");
 static final String SCHEMA="CREATE TABLE IF NOT EXISTS dune_npc_spice_sales (id VARCHAR(36) PRIMARY KEY,player_uuid VARCHAR(36) NOT NULL,npc_uuid VARCHAR(36) NOT NULL,quantity INT NOT NULL,unit_price DECIMAL(20,2) NOT NULL,total_price DECIMAL(20,2) NOT NULL,created_at BIGINT NOT NULL)";
 private static boolean ready;
 private final DuneEss plugin;
 private SpiceBuyer(DuneEss plugin){this.plugin=plugin;}
 public static void install(DuneEss plugin){
  Bukkit.getPluginManager().registerEvents(new SpiceBuyer(plugin),plugin);
  for(World world:Bukkit.getWorlds())for(Entity e:world.getEntities())rename(e);
  var config=YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder().getParentFile(),"DuneCore/config.yml"));
  if(!config.getBoolean("database.enabled",false))return;
  try(Connection c=SqlDialect.connect(config.getString("database.jdbc-url",""),config.getString("database.username",""),config.getString("database.password",""));Statement s=c.createStatement()){
   s.executeUpdate(SCHEMA);ready=true;plugin.getLogger().info("Spice Buyer ready: player spice -> Dunemarks, atomic network inventory settlement.");
  }catch(SQLException failure){ready=false;plugin.getLogger().warning("Spice Buyer storage unavailable: "+failure.getSQLState());}
 }
 private static boolean tagged(Entity e){return e.getWorld().getName().equalsIgnoreCase("arrakis")&&e.getPersistentDataContainer().has(TAG,PersistentDataType.BYTE);}
 private static void rename(Entity e){if(tagged(e))e.setCustomName(ChatColor.GOLD+"Spice Buyer · +35%");}
 @EventHandler public void loaded(EntitiesLoadEvent event){for(Entity e:event.getEntities())rename(e);}
 @EventHandler(ignoreCancelled=true) public void spawned(CreatureSpawnEvent event){Entity e=event.getEntity();if(!(e instanceof Villager))return;Bukkit.getScheduler().runTask(plugin,()->{if(e.isValid())rename(e);});}
 static BigDecimal unit(int base){return BigDecimal.valueOf(Math.max(1,base)).multiply(new BigDecimal("1.35")).setScale(2,RoundingMode.HALF_UP);}
 static boolean spice(ItemStack item){return item!=null&&item.getAmount()>0&&item.hasItemMeta()&&"spice".equals(item.getItemMeta().getPersistentDataContainer().get(ITEM,PersistentDataType.STRING));}
 static int quantity(Player player){int count=0;for(ItemStack item:player.getInventory().getStorageContents())if(spice(item))count+=item.getAmount();return Math.min(16,count);}
 static boolean near(Player p,Entity e){return p.isOnline()&&e.isValid()&&p.getWorld().equals(e.getWorld())&&tagged(e)&&p.getLocation().distanceSquared(e.getLocation())<=64;}
 static void remove(Player p,int count){
  if(count<1||count>16||quantity(p)<count)throw new IllegalArgumentException("Carry the quoted tagged spice before selling.");
  ItemStack[] items=p.getInventory().getStorageContents();int remaining=count;
  for(int slot=0;slot<items.length&&remaining>0;slot++)if(spice(items[slot])){ItemStack next=items[slot].clone();int taken=Math.min(remaining,next.getAmount());next.setAmount(next.getAmount()-taken);p.getInventory().setItem(slot,next.getAmount()==0?null:next);remaining-=taken;}
  if(remaining!=0)throw new IllegalStateException("Spice inventory changed");
 }
 static void credit(Connection c,UUID trade,UUID player,UUID npc,int quantity,BigDecimal price)throws SQLException{
  if(quantity<1||quantity>16||price==null||price.signum()<=0||price.scale()>2)throw new IllegalArgumentException("Invalid quote");
  BigDecimal total=price.multiply(BigDecimal.valueOf(quantity));
  try(PreparedStatement q=SqlDialect.prepare(c,"INSERT INTO dune_economy_balances(uuid,balance) VALUES (?,0) ON CONFLICT(uuid) DO NOTHING")){SqlDialect.uuid(q,1,player);q.executeUpdate();}
  try(PreparedStatement q=SqlDialect.prepare(c,"UPDATE dune_economy_balances SET balance=balance+? WHERE uuid=?")){q.setBigDecimal(1,total);SqlDialect.uuid(q,2,player);if(q.executeUpdate()!=1)throw new SQLException("Wallet credit not confirmed");}
  try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_npc_spice_sales(id,player_uuid,npc_uuid,quantity,unit_price,total_price,created_at) VALUES(?,?,?,?,?,?,?)")){q.setString(1,trade.toString());q.setString(2,player.toString());q.setString(3,npc.toString());q.setInt(4,quantity);q.setBigDecimal(5,price);q.setBigDecimal(6,total);q.setLong(7,System.currentTimeMillis());if(q.executeUpdate()!=1)throw new SQLException("Sale audit not confirmed");}
 }
 public static void open(DuneEss plugin,PlayerInteractEntityEvent event){
  Entity npc=event.getRightClicked();if(!tagged(npc))return;event.setCancelled(true);if(event.getHand()!=EquipmentSlot.HAND)return;
  Player player=event.getPlayer();rename(npc);if(!near(player,npc))return;
  if(!ready){player.sendMessage(ChatColor.RED+"The spice buyer is unavailable. No spice was taken.");return;}
  int amount=quantity(player);BigDecimal price=unit(plugin.getConfig().getInt("currency.spice-price",15));UUID trade=UUID.randomUUID();
  Inventory model=Bukkit.createInventory(null,9,"Spice Buyer");ItemStack offer=new ItemStack(Material.ORANGE_DYE,Math.max(1,amount));ItemMeta meta=offer.getItemMeta();
  meta.setDisplayName(amount==0?"Bring your mined spice":"Sell "+amount+" spice · Receive "+price.multiply(BigDecimal.valueOf(amount)).toPlainString()+" Dunemarks");
  meta.setLore(List.of("This NPC buys spice FROM YOU.","You give tagged spice; you receive Dunemarks.","Buyer pays 35% above the standard spice price.","Up to 16 spice per confirmed sale."));offer.setItemMeta(meta);model.setItem(4,offer);
  Map<Integer,Runnable> actions=new HashMap<>();
  if(amount>0)actions.put(4,()->{
   if(!near(player,npc)||!price.equals(unit(plugin.getConfig().getInt("currency.spice-price",15)))){player.sendMessage("The buyer or price changed. Reopen the menu.");return;}
   Plugin core=Bukkit.getPluginManager().getPlugin("DuneCore");if(core==null||!core.isEnabled()){player.sendMessage("Network trading is unavailable.");return;}
   try{
    Consumer<Connection> work=c->{try{remove(player,amount);credit(c,trade,player.getUniqueId(),npc.getUniqueId(),amount,price);}catch(SQLException e){throw new java.util.concurrent.CompletionException(e);}};
    core.getClass().getMethod("transactNetworkInventory",Player.class,Consumer.class).invoke(core,player,work);
    player.sendMessage(ChatColor.GOLD+"Sold "+amount+" spice to the buyer for "+price.multiply(BigDecimal.valueOf(amount)).toPlainString()+" Dunemarks.");
   }catch(ReflectiveOperationException|RuntimeException failure){player.sendMessage(ChatColor.RED+"Sale could not be confirmed. Reopen the menu; staff can check the trade record.");plugin.getLogger().warning("Spice Buyer sale unconfirmed for trade "+trade);}
  });
  com.example.dunegui.NativeMenus.inventory(plugin,player,"Spice Buyer · +35%",model,actions,()->near(player,npc));
 }
}
