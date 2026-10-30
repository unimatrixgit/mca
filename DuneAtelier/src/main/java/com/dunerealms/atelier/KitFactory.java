package com.dunerealms.atelier;
import org.bukkit.*;import org.bukkit.inventory.*;import org.bukkit.inventory.meta.LeatherArmorMeta;import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;import net.kyori.adventure.text.format.NamedTextColor;
import java.util.*;
final class KitFactory {
 static List<ItemStack> decorate(List<?> base,DeliveryStore.Design d,UUID order){
  if(!DeliveryStore.TIERS.contains(d.quality()))throw new IllegalArgumentException("Invalid cosmetic rarity");List<ItemStack> pieces=new ArrayList<>();
  for(Object value:base)if(value instanceof ItemStack original&&original.getItemMeta() instanceof LeatherArmorMeta){
   ItemStack copy=original.clone();LeatherArmorMeta meta=(LeatherArmorMeta)copy.getItemMeta();meta.setColor(Color.fromRGB(Integer.parseInt(d.color().substring(1),16)));
   meta.displayName(Component.text(d.name()+" "+copy.getType().name().replace("LEATHER_","").toLowerCase(Locale.ROOT),NamedTextColor.GOLD));
   String label=Arrays.stream(d.quality().split("_")).map(s->Character.toUpperCase(s.charAt(0))+s.substring(1)).collect(java.util.stream.Collectors.joining(" "));
   List<Component> lore=new ArrayList<>();lore.add(Component.text("Quality: "+label,NamedTextColor.GREEN));for(String line:d.lore())lore.add(Component.text(line,NamedTextColor.GRAY));meta.lore(lore);
   meta.getPersistentDataContainer().set(new NamespacedKey("dunerealms","quality"),PersistentDataType.STRING,d.quality());
   meta.getPersistentDataContainer().set(new NamespacedKey("dunerealms","kit_sale_id"),PersistentDataType.STRING,order.toString());
   meta.getPersistentDataContainer().set(new NamespacedKey("dunerealms","kit_design_id"),PersistentDataType.STRING,d.id());copy.setItemMeta(meta);pieces.add(copy);
  }
  if(pieces.size()!=4||pieces.stream().map(ItemStack::getType).distinct().count()!=4)throw new IllegalStateException("Incomplete faction kit");return pieces;
 }
 static Object trim(org.bukkit.inventory.meta.ItemMeta meta){return meta instanceof org.bukkit.inventory.meta.ArmorMeta armor?armor.getTrim():null;}
 static void verify(List<?> base,DeliveryStore.Design d){
  List<ItemStack> originals=base.stream().filter(v->v instanceof ItemStack i&&i.getItemMeta() instanceof LeatherArmorMeta).map(v->(ItemStack)v).toList();
  List<byte[]> before=originals.stream().map(ItemStack::serializeAsBytes).toList();List<ItemStack> made=decorate(base,d,UUID.randomUUID());
  for(int i=0;i<4;i++){
   var original=originals.get(i);var originalMeta=(LeatherArmorMeta)original.getItemMeta();var result=made.get(i);var resultMeta=(LeatherArmorMeta)result.getItemMeta();
   if(!Arrays.equals(before.get(i),original.serializeAsBytes())||!Objects.equals(originalMeta.getAttributeModifiers(),resultMeta.getAttributeModifiers())||!originalMeta.getEnchants().equals(resultMeta.getEnchants())||!Objects.equals(trim(originalMeta),trim(resultMeta)))throw new IllegalStateException("Kit decoration changed base combat properties");
   for(NamespacedKey tag:originalMeta.getPersistentDataContainer().getKeys()){if(tag.equals(new NamespacedKey("dunerealms","quality")))continue;String value=originalMeta.getPersistentDataContainer().get(tag,PersistentDataType.STRING);if(value!=null&&!value.equals(resultMeta.getPersistentDataContainer().get(tag,PersistentDataType.STRING)))throw new IllegalStateException("Faction authority changed");}
   if(!d.quality().equals(resultMeta.getPersistentDataContainer().get(new NamespacedKey("dunerealms","quality"),PersistentDataType.STRING)))throw new IllegalStateException("Quality label mismatch");
  }
 }
}
