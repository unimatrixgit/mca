package com.dunerealms.atelier;
import java.util.*;
/** Server-owned quality policy. Blueprint input never supplies enchantments. */
final class KitEnchantments {
 static final String VERSION="pvp-v1";
 static Map<String,Integer> profile(String quality,String material){
  int tier=List.of("common","uncommon","rare","very_rare","legendary").indexOf(quality);
  if(tier<0)throw new IllegalArgumentException("Unknown kit quality");
  if(!Set.of("LEATHER_HELMET","LEATHER_CHESTPLATE","LEATHER_LEGGINGS","LEATHER_BOOTS").contains(material))throw new IllegalArgumentException("Expected stillsuit armour");
  Map<String,Integer> e=new LinkedHashMap<>();
  // Common is the unenchanted vanilla starter kit.
  if(tier==0)return Map.of();
  e.put("protection",tier);e.put("unbreaking",Math.min(tier,3));
  if(material.endsWith("BOOTS")){e.put("feather_falling",tier);if(tier>=2)e.put("depth_strider",tier-1);if(tier==4)e.put("soul_speed",3);}
  if(tier>=2&&material.endsWith("HELMET")){e.put("respiration",tier-1);e.put("aqua_affinity",1);}
  if(tier>=3){e.put("mending",1);if(material.endsWith("LEGGINGS"))e.put("swift_sneak",tier-1);}
  if(tier==4)e.put("thorns",3);
  return Collections.unmodifiableMap(e);
 }
}
