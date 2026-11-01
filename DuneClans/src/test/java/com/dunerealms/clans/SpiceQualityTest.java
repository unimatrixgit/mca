package com.dunerealms.clans;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.*;
import org.junit.jupiter.api.Test;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.*;
class SpiceQualityTest {
 ClanStore.Grade read(String value)throws Exception{
  DuneClans plugin=mock(DuneClans.class,CALLS_REAL_METHODS);
  NamespacedKey itemKey=new NamespacedKey("dunerealms","item"),qualityKey=new NamespacedKey("dunerealms","quality");
  for(var entry:java.util.Map.of("itemKey",itemKey,"qualityKey",qualityKey).entrySet()){Field f=DuneClans.class.getDeclaredField(entry.getKey());f.setAccessible(true);f.set(plugin,entry.getValue());}
  ItemStack item=mock(ItemStack.class);ItemMeta meta=mock(ItemMeta.class);PersistentDataContainer pdc=mock(PersistentDataContainer.class);
  when(item.getType()).thenReturn(Material.ORANGE_DYE);when(item.hasItemMeta()).thenReturn(true);when(item.getItemMeta()).thenReturn(meta);when(meta.getPersistentDataContainer()).thenReturn(pdc);
  when(pdc.get(itemKey,PersistentDataType.STRING)).thenReturn("spice");when(pdc.get(qualityKey,PersistentDataType.STRING)).thenReturn(value);
  Method m=DuneClans.class.getDeclaredMethod("spiceGrade",ItemStack.class);m.setAccessible(true);return (ClanStore.Grade)m.invoke(plugin,item);
 }
 @Test void recognizesExistingCoreAndItemsQualityLabels()throws Exception{
  for(var grade:ClanStore.Grade.values()){assertEquals(grade,read(grade.label()),"Core title-case "+grade);assertEquals(grade,read(grade.name()),"legacy enum "+grade);}
 }
 @Test void missingQualityRemainsLegacyCommonButUnknownCannotBecomeCommon()throws Exception{assertEquals(ClanStore.Grade.common,read(null));assertNull(read("Mythic"));assertNull(read(""));}
 @Test void loreRejectsInjectedFormattingAndTooManyLines(){assertThrows(IllegalArgumentException.class,()->DuneClans.lore("[\"bad\\u00a7color\"]"));assertThrows(IllegalArgumentException.class,()->DuneClans.lore("[\"a\",\"b\",\"c\",\"d\",\"e\"]"));assertEquals(java.util.List.of("Desert woven"),DuneClans.lore("[\"Desert woven\"]"));}
}
