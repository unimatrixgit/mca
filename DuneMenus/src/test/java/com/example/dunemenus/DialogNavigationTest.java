package com.example.dunemenus;
import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;

class DialogNavigationTest {
 @Test void staleDialogCannotCloseNewerView()throws Exception{
  var h=new VirtualDispatchTest.Harness();
  h.plugin.navigate(h.player,h.view,"close",0,0);
  assertSame(h.view,h.views.get(h.id));verify(h.player,never()).closeInventory();
 }
 @Test void otherPlayerCannotUseDialogOwnersCallback()throws Exception{
  var h=new VirtualDispatchTest.Harness();Player other=mock(Player.class);
  when(other.getUniqueId()).thenReturn(UUID.randomUUID());when(other.isOnline()).thenReturn(true);
  h.plugin.navigate(other,h.view,"close",0,1);
  assertSame(h.view,h.views.get(h.id));verify(other,never()).closeInventory();
 }
 @Test void revokedAuthorityInvalidatesNavigationImmediately()throws Exception{
  var h=new VirtualDispatchTest.Harness();h.allowed.set(false);
  h.plugin.navigate(h.player,h.view,"close",0,1);
  verify(h.player,never()).closeInventory();assertFalse(h.plugin.current(h.player,h.view));
 }
 @Test void currentOwnerCanCloseWithoutRunningGameplayAction()throws Exception{
  var h=new VirtualDispatchTest.Harness();h.plugin.navigate(h.player,h.view,"close",0,1);
  assertTrue(h.views.isEmpty());assertEquals(0,h.calls.get());verify(h.player).closeInventory();
 }
}
