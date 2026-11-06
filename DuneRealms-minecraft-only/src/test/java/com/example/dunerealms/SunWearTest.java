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
 @Test void configuredIntervalsStayBoundedAndOrdered() {
  assertEquals(120, SunWearPolicy.interval("rare", q -> switch(q) {case "common" -> 40;case "uncommon" -> 80;default -> 120;}));
  int previous=0;
  for(String q:new String[]{"common","uncommon","rare","epic","legendary"}) {
   int value=SunWearPolicy.interval(q, ignored -> Integer.MAX_VALUE);
   assertTrue(value>previous && value<=86400);previous=value;
  }
  assertEquals(1,SunWearPolicy.interval("unknown", ignored -> -99));
  assertEquals(5,SunWearPolicy.interval("legendary", ignored -> -99));
 }
 @Test void rarityIntervalsIncreaseAndUnknownIsCommon(){int previous=0;for(String quality:new String[]{"common","uncommon","rare","epic","legendary"}){int interval=SunWearPolicy.interval(quality);assertTrue(interval>previous);previous=interval;}assertEquals(15,SunWearPolicy.interval(null));assertEquals(15,SunWearPolicy.interval("invalid"));assertEquals(90,SunWearPolicy.interval("LEGENDARY"));}
 @Test void actualWearUsesNormalSlotDamageAtBoundaryAndPreservesUnrelatedItems() throws Exception {
  for(int elapsed:new int[]{13,14}){
   DuneCore core=mock(DuneCore.class,CALLS_REAL_METHODS);Field key=DuneCore.class.getDeclaredField("itemKey");key.setAccessible(true);key.set(core,new NamespacedKey("test","item"));
   var config=mock(org.bukkit.configuration.file.FileConfiguration.class);doReturn(config).when(core).getConfig();when(config.getInt(anyString(),anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
   Player player=mock(Player.class);PlayerInventory inventory=mock(PlayerInventory.class);when(player.getInventory()).thenReturn(inventory);
   ItemStack helmet=mock(ItemStack.class);Damageable meta=mock(Damageable.class);PersistentDataContainer data=mock(PersistentDataContainer.class);when(inventory.getItem(EquipmentSlot.HEAD)).thenReturn(helmet);when(helmet.hasItemMeta()).thenReturn(true);when(helmet.getItemMeta()).thenReturn(meta);when(meta.getPersistentDataContainer()).thenReturn(data);when(data.get(new NamespacedKey("test","item"),PersistentDataType.STRING)).thenReturn("stillsuit");when(data.getOrDefault(new NamespacedKey("dunerealms","sun_wear_seconds"),PersistentDataType.INTEGER,0)).thenReturn(elapsed);
   Method wear=DuneCore.class.getDeclaredMethod("wearSunExposedStillsuit",Player.class);wear.setAccessible(true);wear.invoke(core,player);
   if(elapsed==14)verify(player).damageItemStack(EquipmentSlot.HEAD,1);else verify(player,never()).damageItemStack(any(EquipmentSlot.class),anyInt());
   verify(player,never()).setFireTicks(anyInt());verify(player,never()).damage(anyDouble());
  }
 }
 @Test void actualExposureLoopResetsInWaterShadeNightAndOtherWorld() throws Exception {
  for(int scenario=0;scenario<9;scenario++){
   DuneCore core=mock(DuneCore.class,CALLS_REAL_METHODS);Player player=mock(Player.class);org.bukkit.World world=mock(org.bukkit.World.class);org.bukkit.Location location=mock(org.bukkit.Location.class);PlayerInventory inventory=mock(PlayerInventory.class);org.bukkit.configuration.file.FileConfiguration config=mock(org.bukkit.configuration.file.FileConfiguration.class);
   doReturn(config).when(core).getConfig();when(config.getBoolean("arrakis-sun.enabled",true)).thenReturn(true);when(config.getInt("arrakis-sun.lethal-seconds",300)).thenReturn(300);when(config.getInt("arrakis-sun.warning-interval-seconds",30)).thenReturn(30);
   java.util.UUID id=java.util.UUID.randomUUID();when(player.getUniqueId()).thenReturn(id);when(player.getWorld()).thenReturn(world);when(player.getInventory()).thenReturn(inventory);when(inventory.getArmorContents()).thenReturn(new ItemStack[]{null,null,null,null});when(player.getLocation()).thenReturn(location);when(location.getBlockY()).thenReturn(64);when(world.getName()).thenReturn(scenario==4?"caladan":"arrakis");when(world.isClearWeather()).thenReturn(scenario!=5);when(player.isDead()).thenReturn(scenario==6);when(world.getTime()).thenReturn(scenario==3?13000L:1000L);when(world.getHighestBlockYAt(location)).thenReturn(scenario==2?70:64);when(player.isInWater()).thenReturn(scenario==1);when(player.getGameMode()).thenReturn(scenario==7?org.bukkit.GameMode.CREATIVE:scenario==8?org.bukkit.GameMode.SPECTATOR:org.bukkit.GameMode.SURVIVAL);
   java.util.Map<java.util.UUID,Integer> exposure=new java.util.HashMap<>();exposure.put(id,10);Field f=DuneCore.class.getDeclaredField("sunExposureSeconds");f.setAccessible(true);f.set(core,exposure);
   try(var bukkit=mockStatic(org.bukkit.Bukkit.class)){bukkit.when(org.bukkit.Bukkit::getOnlinePlayers).thenReturn(java.util.List.of(player));Method check=DuneCore.class.getDeclaredMethod("checkArrakisSun");check.setAccessible(true);check.invoke(core);}
   if(scenario==0)assertEquals(11,exposure.get(id));else assertFalse(exposure.containsKey(id));verify(player,never()).setFireTicks(anyInt());
  }
 }
 @Test void unbreakableAndUnrelatedEquipmentAreNeverDamaged() throws Exception {
  DuneCore core=mock(DuneCore.class,CALLS_REAL_METHODS);Field key=DuneCore.class.getDeclaredField("itemKey");key.setAccessible(true);key.set(core,new NamespacedKey("test","item"));
  Player player=mock(Player.class);PlayerInventory inventory=mock(PlayerInventory.class);when(player.getInventory()).thenReturn(inventory);
  for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST}) {
   ItemStack item=mock(ItemStack.class);Damageable meta=mock(Damageable.class);PersistentDataContainer data=mock(PersistentDataContainer.class);
   when(inventory.getItem(slot)).thenReturn(item);when(item.hasItemMeta()).thenReturn(true);when(item.getItemMeta()).thenReturn(meta);when(meta.getPersistentDataContainer()).thenReturn(data);
   when(data.get(new NamespacedKey("test","item"),PersistentDataType.STRING)).thenReturn(slot==EquipmentSlot.HEAD?"stillsuit":"ordinary_helmet");when(meta.isUnbreakable()).thenReturn(true);
   verify(item,never()).setItemMeta(any());
  }
  Method wear=DuneCore.class.getDeclaredMethod("wearSunExposedStillsuit",Player.class);wear.setAccessible(true);wear.invoke(core,player);
  verify(player,never()).damageItemStack(any(EquipmentSlot.class),anyInt());verify(core,never()).getConfig();
 }
 @Test void brokenPieceLosesFullSuitProtectionInSameExposureTick() throws Exception {
  DuneCore core=mock(DuneCore.class,CALLS_REAL_METHODS);Field key=DuneCore.class.getDeclaredField("itemKey");key.setAccessible(true);NamespacedKey tag=new NamespacedKey("test","item");key.set(core,tag);
  var config=mock(org.bukkit.configuration.file.FileConfiguration.class);doReturn(config).when(core).getConfig();when(config.getBoolean("arrakis-sun.enabled",true)).thenReturn(true);when(config.getInt(anyString(),anyInt())).thenAnswer(i->i.getArgument(1));
  Player player=mock(Player.class);PlayerInventory inventory=mock(PlayerInventory.class);when(player.getInventory()).thenReturn(inventory);var world=mock(org.bukkit.World.class);var location=mock(org.bukkit.Location.class);when(player.getWorld()).thenReturn(world);when(player.getLocation()).thenReturn(location);when(world.getName()).thenReturn("arrakis");when(world.isClearWeather()).thenReturn(true);when(world.getTime()).thenReturn(1000L);when(player.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
  java.util.UUID id=java.util.UUID.randomUUID();when(player.getUniqueId()).thenReturn(id);java.util.Map<java.util.UUID,Integer> exposure=new java.util.HashMap<>();Field field=DuneCore.class.getDeclaredField("sunExposureSeconds");field.setAccessible(true);field.set(core,exposure);
  ItemStack[] equipped=new ItemStack[4];var slots=new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
  for(int n=0;n<4;n++) {
   ItemStack item=mock(ItemStack.class);Damageable meta=mock(Damageable.class);PersistentDataContainer data=mock(PersistentDataContainer.class);equipped[n]=item;
   when(inventory.getItem(slots[n])).thenReturn(item);when(item.hasItemMeta()).thenReturn(true);when(item.getItemMeta()).thenReturn(meta);when(meta.getPersistentDataContainer()).thenReturn(data);when(data.get(tag,PersistentDataType.STRING)).thenReturn("stillsuit");when(data.getOrDefault(new NamespacedKey("dunerealms","sun_wear_seconds"),PersistentDataType.INTEGER,0)).thenReturn(14);
  }
  when(inventory.getArmorContents()).thenAnswer(i->equipped.clone());
  doAnswer(i->{equipped[0]=null;return null;}).when(player).damageItemStack(EquipmentSlot.HEAD,1);
  try(var bukkit=mockStatic(org.bukkit.Bukkit.class)) {bukkit.when(org.bukkit.Bukkit::getOnlinePlayers).thenReturn(java.util.List.of(player));Method check=DuneCore.class.getDeclaredMethod("checkArrakisSun");check.setAccessible(true);check.invoke(core);}
  assertEquals(1,exposure.get(id));for(var slot:slots)verify(player).damageItemStack(slot,1);verify(player,never()).setHealth(anyDouble());
 }
}
