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
 @Test void actualExposureLoopResetsInWaterShadeNightAndOtherWorld() throws Exception {
  for(int scenario=0;scenario<5;scenario++){
   DuneCore core=mock(DuneCore.class,CALLS_REAL_METHODS);Player player=mock(Player.class);org.bukkit.World world=mock(org.bukkit.World.class);org.bukkit.Location location=mock(org.bukkit.Location.class);PlayerInventory inventory=mock(PlayerInventory.class);org.bukkit.configuration.file.FileConfiguration config=mock(org.bukkit.configuration.file.FileConfiguration.class);
   doReturn(config).when(core).getConfig();when(config.getBoolean("arrakis-sun.enabled",true)).thenReturn(true);when(config.getInt("arrakis-sun.lethal-seconds",300)).thenReturn(300);when(config.getInt("arrakis-sun.warning-interval-seconds",30)).thenReturn(30);
   java.util.UUID id=java.util.UUID.randomUUID();when(player.getUniqueId()).thenReturn(id);when(player.getWorld()).thenReturn(world);when(player.getInventory()).thenReturn(inventory);when(inventory.getArmorContents()).thenReturn(new ItemStack[]{null,null,null,null});when(player.getLocation()).thenReturn(location);when(location.getBlockY()).thenReturn(64);when(world.getName()).thenReturn(scenario==4?"caladan":"arrakis");when(world.isClearWeather()).thenReturn(true);when(world.getTime()).thenReturn(scenario==3?13000L:1000L);when(world.getHighestBlockYAt(location)).thenReturn(scenario==2?70:64);when(player.isInWater()).thenReturn(scenario==1);when(player.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
   java.util.Map<java.util.UUID,Integer> exposure=new java.util.HashMap<>();exposure.put(id,10);Field f=DuneCore.class.getDeclaredField("sunExposureSeconds");f.setAccessible(true);f.set(core,exposure);
   try(var bukkit=mockStatic(org.bukkit.Bukkit.class)){bukkit.when(org.bukkit.Bukkit::getOnlinePlayers).thenReturn(java.util.List.of(player));Method check=DuneCore.class.getDeclaredMethod("checkArrakisSun");check.setAccessible(true);check.invoke(core);}
   if(scenario==0)assertEquals(11,exposure.get(id));else assertFalse(exposure.containsKey(id));verify(player,never()).setFireTicks(anyInt());
  }
 }
}
