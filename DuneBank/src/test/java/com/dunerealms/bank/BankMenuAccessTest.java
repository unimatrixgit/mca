package com.dunerealms.bank;
import com.example.dunegui.NativeMenus;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BankMenuAccessTest {
 @Test void previouslyRenderedStaffLedgerCannotOpenAfterAccessRevocation(){
  Player p=mock(Player.class);when(p.isOnline()).thenReturn(true);when(p.hasPermission("dunebank.use")).thenReturn(true);when(p.hasPermission("dunebank.staff.view")).thenReturn(true);
  AtomicInteger opened=new AtomicInteger();Runnable ledger=DuneBank.guarded(p,true,opened::incrementAndGet);
  when(p.hasPermission("dunebank.staff.view")).thenReturn(false);ledger.run();assertEquals(0,opened.get());
 }
 @Test void personalNavigationRequiresLivePlayerAndUseNode(){
  Player p=mock(Player.class);AtomicInteger opened=new AtomicInteger();Runnable button=DuneBank.guarded(p,false,opened::incrementAndGet);
  when(p.hasPermission("dunebank.use")).thenReturn(true);button.run();assertEquals(0,opened.get());
  when(p.isOnline()).thenReturn(true);button.run();assertEquals(1,opened.get());
  when(p.hasPermission("dunebank.use")).thenReturn(false);button.run();assertEquals(1,opened.get());
 }
 @Test void oldBalanceQueryCannotReplaceAClosedOrDifferentView(){
  Player p=mock(Player.class);Inventory old=mock(Inventory.class),fresh=mock(Inventory.class);
  when(p.isOnline()).thenReturn(true);when(p.hasPermission("dunebank.use")).thenReturn(true);
  try(var menus=mockStatic(NativeMenus.class)){
   menus.when(()->NativeMenus.isCurrent(p,fresh)).thenReturn(true);
   assertFalse(DuneBank.current(p,false,old));assertTrue(DuneBank.current(p,false,fresh));
   when(p.hasPermission("dunebank.use")).thenReturn(false);assertFalse(DuneBank.current(p,false,fresh));
  }
 }
}
