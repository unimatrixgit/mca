package com.dunerealms.atelier;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import java.util.*;
class KitEnchantmentsTest {
 @Test void vanillaCommonHasNoEnchantments(){for(String piece:List.of("HELMET","CHESTPLATE","LEGGINGS","BOOTS"))assertTrue(KitEnchantments.profile("common","LEATHER_"+piece).isEmpty());}
 @Test void protectionProgressesByQuality(){int n=0;for(String q:List.of("uncommon","rare","very_rare","legendary"))assertEquals(++n,KitEnchantments.profile(q,"LEATHER_CHESTPLATE").get("protection"));}
 @Test void legendaryIsFullCompatibleArmour(){var b=KitEnchantments.profile("legendary","LEATHER_BOOTS");assertEquals(Map.of("protection",4,"unbreaking",3,"mending",1,"thorns",3,"feather_falling",4,"depth_strider",3,"soul_speed",3),b);var h=KitEnchantments.profile("legendary","LEATHER_HELMET");assertEquals(3,h.get("respiration"));assertEquals(1,h.get("aqua_affinity"));assertEquals(3,KitEnchantments.profile("legendary","LEATHER_LEGGINGS").get("swift_sneak"));assertFalse(b.containsKey("frost_walker"));assertFalse(b.containsKey("blast_protection"));}
 @Test void utilityAndDurabilityNeverDecrease(){for(String piece:List.of("HELMET","CHESTPLATE","LEGGINGS","BOOTS")){Map<String,Integer> before=Map.of();for(String q:List.of("common","uncommon","rare","very_rare","legendary")){var next=KitEnchantments.profile(q,"LEATHER_"+piece);for(var e:before.entrySet())assertTrue(next.getOrDefault(e.getKey(),0)>=e.getValue());before=next;}}}
 @Test void rejectsUnknownQualityAndNonArmour(){assertThrows(IllegalArgumentException.class,()->KitEnchantments.profile("admin","LEATHER_BOOTS"));assertThrows(IllegalArgumentException.class,()->KitEnchantments.profile("legendary","DIAMOND_SWORD"));}
}
