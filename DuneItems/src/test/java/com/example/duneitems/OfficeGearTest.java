package com.example.duneitems;

import org.junit.jupiter.api.Test;

import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDispenseArmorEvent;

import org.bukkit.entity.Item;

import org.bukkit.event.player.PlayerDropItemEvent;

import org.bukkit.event.entity.EntityPickupItemEvent;

import org.bukkit.event.entity.ItemDespawnEvent;

import org.bukkit.event.inventory.InventoryPickupItemEvent;

import org.bukkit.inventory.ItemStack;

import org.bukkit.inventory.meta.ItemMeta;

import org.bukkit.persistence.*;

import java.sql.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.Mockito.*;

class OfficeGearTest {

 private static final UUID OWNER=UUID.fromString("00000000-0000-4000-8000-000000000001");

 private ItemStack gear(String owner,String office,String term){ItemStack item=mock(ItemStack.class);ItemMeta meta=mock(ItemMeta.class);PersistentDataContainer pdc=mock(PersistentDataContainer.class);when(item.hasItemMeta()).thenReturn(true);when(item.getItemMeta()).thenReturn(meta);when(meta.getPersistentDataContainer()).thenReturn(pdc);when(pdc.has(OfficeGear.OFFICE,PersistentDataType.STRING)).thenReturn(true);when(pdc.get(OfficeGear.OFFICE,PersistentDataType.STRING)).thenReturn(office);when(pdc.get(OfficeGear.TERM,PersistentDataType.STRING)).thenReturn(term);when(pdc.get(OfficeGear.OWNER,PersistentDataType.STRING)).thenReturn(owner);return item;}

 @Test void onlySameElectedOwnerExactCurrentTermMayUse(){Player p=mock(Player.class);when(p.getUniqueId()).thenReturn(OWNER);ItemStack gear=gear(OWNER.toString(),"emperor","term1");assertTrue(OfficeGear.eligible(p,gear,Map.of("emperor","term1")));assertFalse(OfficeGear.eligible(p,gear,Map.of("emperor","term2")));assertFalse(OfficeGear.eligible(p,gear,Map.of()));assertFalse(OfficeGear.eligible(p,gear(UUID.randomUUID().toString(),"emperor","term1"),Map.of("emperor","term1")));verify(p,never()).isOp();verify(p,never()).hasPermission(anyString());}

 @Test void malformedAndExpiredGearFailsClosed(){Player p=mock(Player.class);when(p.getUniqueId()).thenReturn(OWNER);assertFalse(OfficeGear.eligible(p,gear(OWNER.toString(),"emperor",null),Map.of("emperor","term1")));assertFalse(OfficeGear.eligible(p,gear(null,"emperor","term1"),Map.of("emperor","term1")));assertTrue(OfficeGear.eligible(p,null,Map.of()));}

 @Test void authorityKeysMustBeActualElectedOffices(){assertTrue(OfficeGear.validOffice("emperor"));for(String house:List.of("Fremen","Harkonnen","Corrino","Atreides","Ordos"))assertTrue(OfficeGear.validOffice("house-leader:"+house));for(String key:List.of("owner","admin","water-seller","house-leader","house-leader:Unknown","house-leader:atreides"))assertFalse(OfficeGear.validOffice(key));}

 @Test void officeDropsRemainOwnerBoundAndPersistent(){

  OfficeGear service=new OfficeGear(mock(DuneItems.class));Item entity=mock(Item.class);ItemStack bound=gear(OWNER.toString(),"emperor","term1");when(entity.getItemStack()).thenReturn(bound);PlayerDropItemEvent event=mock(PlayerDropItemEvent.class);when(event.getItemDrop()).thenReturn(entity);

  service.drop(event);verify(entity).setOwner(OWNER);verify(entity).setCanMobPickup(false);verify(entity).setUnlimitedLifetime(true);verify(entity).setPersistent(true);

 }

 @Test void foreignPickupAndHoppersCannotTakeOfficeGear(){

  OfficeGear service=new OfficeGear(mock(DuneItems.class));Item entity=mock(Item.class);ItemStack bound=gear(OWNER.toString(),"emperor","term1");when(entity.getItemStack()).thenReturn(bound);Player other=mock(Player.class);when(other.getUniqueId()).thenReturn(UUID.randomUUID());EntityPickupItemEvent pickup=mock(EntityPickupItemEvent.class);when(pickup.getItem()).thenReturn(entity);when(pickup.getEntity()).thenReturn(other);service.pickup(pickup);verify(pickup).setCancelled(true);

  InventoryPickupItemEvent hopper=mock(InventoryPickupItemEvent.class);when(hopper.getItem()).thenReturn(entity);service.hopper(hopper);verify(hopper).setCancelled(true);

 }

 @Test void officeGearDoesNotDespawn(){OfficeGear service=new OfficeGear(mock(DuneItems.class));Item entity=mock(Item.class);ItemStack bound=gear(OWNER.toString(),"emperor","term1");when(entity.getItemStack()).thenReturn(bound);ItemDespawnEvent event=mock(ItemDespawnEvent.class);when(event.getEntity()).thenReturn(entity);service.despawn(event);verify(event).setCancelled(true);}

 @Test void factoryAlwaysUsesActualCanonicalPlayerFaction(){for(String faction:List.of("Fremen","Harkonnen","Corrino","Atreides","Ordos"))assertEquals(faction,OfficeGear.canonicalFaction(faction.toLowerCase(Locale.ROOT)));assertThrows(IllegalStateException.class,()->OfficeGear.canonicalFaction(null));assertThrows(IllegalStateException.class,()->OfficeGear.canonicalFaction("Unknown"));}

 @Test void allAppointedStewardLeadersHaveDistinctMetadataAndColors(){

  for(String type:List.of("water-steward","spice-steward"))for(String house:List.of("Fremen","Harkonnen","Corrino","Atreides","Ordos"))assertTrue(OfficeGear.validOffice(type+":"+house));

  assertEquals("Water Steward - Aerdrecht",OfficeGear.officeTitle("water-steward:Atreides"));assertEquals("Spice Steward - Korenholt",OfficeGear.officeTitle("spice-steward:Corrino"));assertNotEquals(OfficeGear.officeColor("water-steward:Fremen"),OfficeGear.officeColor("spice-steward:Fremen"));assertNull(OfficeGear.officeColor("house-leader:Fremen"));

  assertFalse(OfficeGear.validOffice("water-steward:Unknown"));assertFalse(OfficeGear.validOffice("water-steward"));

 }

 @Test void revokedAppointmentTermCannotUseStewardSuit(){Player p=mock(Player.class);when(p.getUniqueId()).thenReturn(OWNER);ItemStack suit=gear(OWNER.toString(),"water-steward:Atreides","old-emperor-appointment");assertTrue(OfficeGear.eligible(p,suit,Map.of("water-steward:Atreides","old-emperor-appointment")));assertFalse(OfficeGear.eligible(p,suit,Map.of("water-steward:Atreides","new-emperor-appointment")));assertFalse(OfficeGear.eligible(p,suit,Map.of("house-leader:Atreides","leader-term")));}

 @Test void dispenserCannotEquipForeignOrRevokedOfficeArmor(){
  OfficeGear service=spy(new OfficeGear(mock(DuneItems.class)));Player player=mock(Player.class);when(player.getUniqueId()).thenReturn(OWNER);doReturn(Map.of("emperor","term1")).when(service).offices(player,true);doReturn(Map.of("emperor","term1")).when(service).offices(player,false);
  for(ItemStack invalid:List.of(gear(UUID.randomUUID().toString(),"emperor","term1"),gear(OWNER.toString(),"emperor","expired-term"))){BlockDispenseArmorEvent event=mock(BlockDispenseArmorEvent.class);when(event.getTargetEntity()).thenReturn(player);when(event.getItem()).thenReturn(invalid);service.dispense(event);verify(event).setCancelled(true);}
  BlockDispenseArmorEvent valid=mock(BlockDispenseArmorEvent.class);when(valid.getTargetEntity()).thenReturn(player);ItemStack current=gear(OWNER.toString(),"emperor","term1");when(valid.getItem()).thenReturn(current);service.dispense(valid);verify(valid,never()).setCancelled(anyBoolean());
 }
 @Test void sharedUniqueReceiptPreventsSecondServerClaimAndRollbackPreservesRetry()throws Exception{

  String url="jdbc:h2:mem:office_receipts;MODE=MySQL;DB_CLOSE_DELAY=-1";

  try(Connection first=DriverManager.getConnection(url);Connection second=DriverManager.getConnection(url)){

   first.createStatement().execute("CREATE TABLE dune_office_kit_claims(office_key VARCHAR(96),term_id VARCHAR(190),owner_uuid VARCHAR(36),PRIMARY KEY(office_key,term_id))");

   first.setAutoCommit(false);OfficeGear.insertReceipt(first,"emperor","rollback-term",OWNER);first.rollback();OfficeGear.insertReceipt(first,"emperor","rollback-term",OWNER);first.commit();

   assertThrows(SQLException.class,()->OfficeGear.insertReceipt(second,"emperor","rollback-term",OWNER));OfficeGear.insertReceipt(second,"emperor","new-term",OWNER);

   try(ResultSet r=second.createStatement().executeQuery("SELECT COUNT(*) FROM dune_office_kit_claims")){r.next();assertEquals(2,r.getInt(1));}

  }

 }

}

