package com.dunerealms.clans;
import com.example.dunegui.NativeMenus;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClanMenuAccessTest {
 @Test void revokingMenuPermissionStopsAlreadyRenderedBankAndKitCallbacks(){
  Player p=mock(Player.class);when(p.isOnline()).thenReturn(true);when(p.hasPermission("dune.clans.view")).thenReturn(true);
  AtomicInteger calls=new AtomicInteger();Runnable button=DuneClans.guarded(p,calls::incrementAndGet);
  when(p.hasPermission("dune.clans.view")).thenReturn(false);button.run();assertEquals(0,calls.get());
 }
 @Test void disconnectedPlayersCannotRunStoredMenuCallbacks(){
  Player p=mock(Player.class);when(p.hasPermission("dune.clans.view")).thenReturn(true);
  AtomicInteger calls=new AtomicInteger();DuneClans.guarded(p,calls::incrementAndGet).run();assertEquals(0,calls.get());
 }
 @Test void currentPermissionAllowsNavigationButOpFlagCannotReplaceNode(){
  Player p=mock(Player.class);when(p.isOnline()).thenReturn(true);when(p.isOp()).thenReturn(true);
  AtomicInteger calls=new AtomicInteger();Runnable button=DuneClans.guarded(p,calls::incrementAndGet);
  button.run();assertEquals(0,calls.get());when(p.hasPermission("dune.clans.view")).thenReturn(true);button.run();assertEquals(1,calls.get());
 }
 @Test void lateDatabaseReplyCannotReplaceAClosedOrDifferentMenu(){
  Player p=mock(Player.class);Inventory original=mock(Inventory.class),replacement=mock(Inventory.class);
  when(p.isOnline()).thenReturn(true);when(p.hasPermission("dune.clans.view")).thenReturn(true);
  try(var menus=mockStatic(NativeMenus.class)){
   menus.when(()->NativeMenus.isCurrent(p,original)).thenReturn(false);menus.when(()->NativeMenus.isCurrent(p,replacement)).thenReturn(true);
   assertFalse(DuneClans.current(p,original));assertTrue(DuneClans.current(p,replacement));
   when(p.hasPermission("dune.clans.view")).thenReturn(false);assertFalse(DuneClans.current(p,replacement));
  }
 }
}
