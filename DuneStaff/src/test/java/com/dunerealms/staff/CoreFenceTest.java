package com.dunerealms.staff;
import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;import org.bukkit.plugin.java.JavaPlugin;
import java.sql.*;import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class CoreFenceTest {
 public static class CoreApi extends JavaPlugin{public void transactNetworkInventory(Player p,Consumer<Connection> action)throws Exception{}public void adminAssignFaction(Player actor,Player target,String faction,Consumer<Connection> action)throws Exception{}}
 @Test void factionAssignmentUsesOnlyExactCoreFacadeAndSameAuthorityConnection()throws Exception{
  CoreApi core=mock(CoreApi.class);Player actor=mock(Player.class),target=mock(Player.class);Connection c=mock(Connection.class);when(core.isEnabled()).thenReturn(true);when(actor.isOnline()).thenReturn(true);when(target.isOnline()).thenReturn(true);when(actor.getUniqueId()).thenReturn(java.util.UUID.randomUUID());when(target.getUniqueId()).thenReturn(java.util.UUID.randomUUID());when(actor.hasPermission("dune.tickets.staff")).thenReturn(true);when(actor.hasPermission("dune.admin")).thenReturn(true);
  doAnswer(call->{Consumer<Connection> action=call.getArgument(3);action.accept(c);return null;}).when(core).adminAssignFaction(eq(actor),eq(target),eq("Atreides"),any());
  new CoreFence(core).assignFaction(actor,target,"Atreides",actual->assertSame(c,actual));verify(core).adminAssignFaction(eq(actor),eq(target),eq("Atreides"),any());verify(core,never()).transactNetworkInventory(any(),any());assertThrows(SQLException.class,()->new CoreFence(core).assignFaction(actor,target,"atreides",actual->fail("Unknown faction cannot reach authority")));
 }
 @Test void exactLoadedTargetAndSameCoreConnectionAreUsed()throws Exception{CoreApi core=mock(CoreApi.class);Player target=mock(Player.class);Connection c=mock(Connection.class);when(core.isEnabled()).thenReturn(true);when(target.isOnline()).thenReturn(true);doAnswer(call->{Consumer<Connection> action=call.getArgument(1);action.accept(c);return null;}).when(core).transactNetworkInventory(eq(target),any());new CoreFence(core).run(target,actual->assertSame(c,actual));verify(core).transactNetworkInventory(eq(target),any());}
 @Test void coreFailureIsPropagatedWithoutIndependentCommitOrFallback()throws Exception{CoreApi core=mock(CoreApi.class);Player target=mock(Player.class);when(core.isEnabled()).thenReturn(true);when(target.isOnline()).thenReturn(true);SQLException failure=new SQLException("Unknown checkpoint result");doThrow(failure).when(core).transactNetworkInventory(eq(target),any());assertSame(failure,assertThrows(SQLException.class,()->new CoreFence(core).run(target,c->fail("Must not fall back"))));}
 @Test void disconnectedTargetOrDisabledCoreCannotRun()throws Exception{CoreApi core=mock(CoreApi.class);Player target=mock(Player.class);assertThrows(SQLException.class,()->new CoreFence(core).run(target,c->{}));when(target.isOnline()).thenReturn(true);assertThrows(SQLException.class,()->new CoreFence(core).run(target,c->{}));verify(core,never()).transactNetworkInventory(any(),any());}
}

