package com.example.dunerealms;
import org.junit.jupiter.api.Test;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.*;
import java.lang.reflect.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class SunWearTest {
 @Test void rarityIntervalsIncreaseAndUnknownIsCommon(){int previous=0;for(String quality:new String[]{"common","uncommon","rare","epic","legendary"}){int interval=SunWearPolicy.interval(quality);assertTrue(interval>previous);previous=interval;}assertEquals(15,SunWearPolicy.interval(null));assertEquals(15,SunWearPolicy.interval("invalid"));assertEquals(90,SunWearPolicy.interval("LEGENDARY"));}
 @Test void actualWearUsesNormalSlotDamageAtBoundaryAndPreservesUnrelatedItems() throws Exception {
  for(int elapsed:new int[]{13,14}){
   DuneCore core=mock(DuneCore.class,CALLS_REAL_METHODS);Field key=DuneCore.class.getDeclaredField("itemKey");key.setAccessible(true);key.set(core,new NamespacedKey("test","item"));
   Player player=mock(Player.class);PlayerInventory inventory=mock(PlayerInventory.class);when(player.getInventory()).thenReturn(inventory);
   ItemStack helmet=mock(ItemStack.class);Damageable meta=mock(Damageable.class);PersistentDataContainer data=mock(PersistentDataContainer.class);when(inventory.getItem(EquipmentSlot.HEAD)).thenReturn(helmet);when(helmet.hasItemMeta()).thenReturn(true);when(helmet.getItemMeta()).thenReturn(meta);when(meta.getPersistentDataContainer()).thenReturn(data);when(data.get(new NamespacedKey("test","item"),PersistentDataType.STRING)).thenReturn("stillsuit");when(data.getOrDefault(new NamespacedKey("dunerealms","sun_wear_seconds"),PersistentDataType.INTEGER,0)).thenReturn(elapsed);
   Method wear=DuneCore.class.getDeclaredMethod("wearSunExposedStillsuit",Player.class);wear.setAccessible(true);wear.invoke(core,player);
   if(elapsed==14)verify(player).damageItemStack(EquipmentSlot.HEAD,1);else verify(player,never()).damageItemStack(any(EquipmentSlot.class),anyInt());
   verify(player,never()).setFireTicks(anyInt());verify(player,never()).damage(anyDouble());
  }
 }
}
