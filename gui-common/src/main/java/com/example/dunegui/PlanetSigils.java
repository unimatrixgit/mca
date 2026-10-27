package com.example.dunegui;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import java.util.Map;
public final class PlanetSigils {
 private static final Map<String,String> IDS=Map.of("arrakis","serevan","caladan","pelareth","giedi_prime","varkesh","kaitain","orivane","sigma_draconis_iv","nivareth");
 private static final Map<String,String> LABELS=Map.of("arrakis","Arrakduin","caladan","Kaladam","giedi_prime","Giedermark","kaitain","Kaiterhof","sigma_draconis_iv","Sigmadraken IV");
 public static String label(String id){String name=LABELS.getOrDefault(id,id);return name.isEmpty()?name:Character.toUpperCase(name.charAt(0))+name.substring(1);}
 public static ItemStack apply(ItemStack item,String id){String name=IDS.get(id);if(name!=null){var meta=item.getItemMeta();meta.setItemModel(new NamespacedKey("dunerealms","planet_"+name));item.setItemMeta(meta);}return item;}
 private PlanetSigils(){}
}
