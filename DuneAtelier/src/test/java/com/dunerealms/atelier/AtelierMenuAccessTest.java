package com.dunerealms.atelier;
import com.example.dunegui.NativeMenus;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AtelierMenuAccessTest {
 @Test void aSavedDesignOrRewardButtonCannotRunAfterViewAccessRevocation(){
  Player p=mock(Player.class);when(p.isOnline()).thenReturn(true);when(p.hasPermission("dune.atelier.view")).thenReturn(true);
  AtomicInteger calls=new AtomicInteger();Runnable button=DuneAtelier.guarded(p,calls::incrementAndGet);
  when(p.hasPermission("dune.atelier.view")).thenReturn(false);button.run();assertEquals(0,calls.get());
 }
 @Test void disconnectedPlayersCannotRunStoredCallbacks(){
  Player p=mock(Player.class);when(p.hasPermission("dune.atelier.view")).thenReturn(true);
  AtomicInteger calls=new AtomicInteger();DuneAtelier.guarded(p,calls::incrementAndGet).run();assertEquals(0,calls.get());
 }
 @Test void anOldReceiptQueryCannotReopenAClosedOrReplacedScreen()throws Exception{
  DuneAtelier plugin=mock(DuneAtelier.class,CALLS_REAL_METHODS);Field ready=DuneAtelier.class.getDeclaredField("ready");ready.setAccessible(true);ready.set(plugin,true);
  Player p=mock(Player.class);Inventory old=mock(Inventory.class),fresh=mock(Inventory.class);
  when(p.isOnline()).thenReturn(true);when(p.hasPermission("dune.atelier.view")).thenReturn(true);
  try(var menus=mockStatic(NativeMenus.class)){
   menus.when(()->NativeMenus.isCurrent(p,fresh)).thenReturn(true);
   assertFalse(plugin.current(p,old));assertTrue(plugin.current(p,fresh));
   ready.set(plugin,false);assertFalse(plugin.current(p,fresh));
  }
 }
}
