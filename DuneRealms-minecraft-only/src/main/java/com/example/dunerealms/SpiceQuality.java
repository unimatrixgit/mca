package com.example.dunerealms;
import org.bukkit.*;import org.bukkit.block.Block;import org.bukkit.inventory.meta.ItemMeta;import org.bukkit.persistence.PersistentDataType;import java.util.*;
/** Cosmetic identity only: canonical spice tag, quantities, price and faction/job accounting remain unchanged. */
public final class SpiceQuality {
 private SpiceQuality(){}
 public static String grade(boolean licensed,boolean dense){return dense?"Rare":licensed?"Common":"Uncommon";}
 public static boolean dense(Location source) {
  if(source==null||source.getWorld()==null||!"arrakis".equalsIgnoreCase(source.getWorld().getName()))return false;
  Block block=source.getBlock();int encoded=(block.getY()+2048)*256+(block.getX()&15)*16+(block.getZ()&15);
  int[] ledger=block.getChunk().getPersistentDataContainer().get(new NamespacedKey("dunerealms","rich-spice-deposits"),PersistentDataType.INTEGER_ARRAY);
  if(ledger!=null)for(int key:ledger)if(key==encoded)return true;
  return new NamespacedKey("dunerealms","dense-spice").equals(block.getBiome().getKey());
 }
 public static void decorate(ItemMeta meta,boolean licensed,boolean dense,Location source) {
  String grade=grade(licensed,dense);String model=dense?"spice_dense":licensed?"spice_common":"spice_smuggled";
  meta.getPersistentDataContainer().set(new NamespacedKey("dunerealms","quality"),PersistentDataType.STRING,grade.toLowerCase(Locale.ROOT));
  meta.getPersistentDataContainer().set(new NamespacedKey("dunerealms","spice_provenance"),PersistentDataType.STRING,dense?"dense-deposit":licensed?"licensed-mining":"smuggled-mining");
  if(dense)meta.getPersistentDataContainer().set(new NamespacedKey("dunerealms","dense_spice"),PersistentDataType.BYTE,(byte)1);
  if(source!=null&&source.getWorld()!=null)meta.getPersistentDataContainer().set(new NamespacedKey("dunerealms","mined_source"),PersistentDataType.STRING,source.getWorld().getUID()+":"+source.getBlockX()+":"+source.getBlockY()+":"+source.getBlockZ());
  meta.setItemModel(new NamespacedKey("dunerealms",model));meta.setCustomModelData(dense?13112:licensed?13110:13111);
  List<String> lore=new ArrayList<>(meta.hasLore()?meta.getLore():List.of());lore.add((dense?ChatColor.BLUE:licensed?ChatColor.WHITE:ChatColor.GREEN)+grade);lore.add(ChatColor.DARK_GRAY+(dense?"Rich spice from a dense Arrakis deposit.":licensed?"Harvested under the Imperial spice charter.":"Unlicensed Arrakis spice, traded beyond the charter."));meta.setLore(lore);
 }
}
