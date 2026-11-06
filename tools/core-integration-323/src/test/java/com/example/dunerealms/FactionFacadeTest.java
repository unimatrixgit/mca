package com.example.dunerealms;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FactionFacadeTest {
 DuneCore core;Player actor,target;Connection connection;Map<UUID,String> selected;MockedStatic<Bukkit> bukkit;
 final UUID targetId=UUID.randomUUID();
 @BeforeEach void setup()throws Exception{
  core=mock(DuneCore.class,CALLS_REAL_METHODS);actor=mock(Player.class);target=mock(Player.class);connection=mock(Connection.class);
  when(actor.isOnline()).thenReturn(true);when(actor.hasPermission("dune.admin")).thenReturn(true);when(target.isOnline()).thenReturn(true);when(target.getUniqueId()).thenReturn(targetId);
  selected=new HashMap<>();Field field=DuneCore.class.getDeclaredField("selected");field.setAccessible(true);field.set(core,selected);
  bukkit=mockStatic(Bukkit.class);bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
 }
 @AfterEach void close(){bukkit.close();}
 @Test void actualArtifactCannotBeShadowedByCompilationDonor()throws Exception{
  String candidate=System.getProperty("qaCoreJar");if(candidate!=null)assertEquals(java.nio.file.Path.of(candidate).toRealPath(),java.nio.file.Path.of(DuneCore.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath());
 }
 @Test void nullArgumentsAndMissingTargetIdentityFailBeforeCheckpoint()throws Exception{
  assertThrows(NullPointerException.class,()->core.adminAssignFaction(null,target,"Atreides",c->{}));
  assertThrows(NullPointerException.class,()->core.adminAssignFaction(actor,null,"Atreides",c->{}));
  assertThrows(NullPointerException.class,()->core.adminAssignFaction(actor,target,"Atreides",null));
  assertThrows(IllegalArgumentException.class,()->core.adminAssignFaction(actor,target,null,c->{}));
  when(target.getUniqueId()).thenReturn(null);assertThrows(NullPointerException.class,()->core.adminAssignFaction(actor,target,"Atreides",c->{}));
  verify(core,never()).transactNetworkInventory(any(),any());assertTrue(selected.isEmpty());
 }
 @SuppressWarnings("unchecked") void checkpoint(boolean fail)throws Exception{
  doAnswer(call->{Consumer<Connection> operation=call.getArgument(1);operation.accept(connection);if(fail)throw new SQLException("Checkpoint failure");return null;}).when(core).transactNetworkInventory(eq(target),any());
 }
 @Test void authorizationRunsBeforeMutationAndSameCheckpointPersistsMetadataOnly()throws Exception{
  selected.put(targetId,"Fremen");checkpoint(false);AtomicInteger locked=new AtomicInteger();
  core.adminAssignFaction(actor,target,"Atreides",c->{assertSame(connection,c);assertEquals("Fremen",selected.get(targetId));locked.incrementAndGet();});
  assertEquals("Atreides",selected.get(targetId));assertEquals(1,locked.get());verify(core).transactNetworkInventory(eq(target),any());
  verify(target,never()).getInventory();verifyNoInteractions(connection);verify(target,never()).teleport(any(org.bukkit.Location.class));
 }
 @Test void lockedCaseRevocationCannotChangeFaction()throws Exception{
  selected.put(targetId,"Fremen");checkpoint(false);
  assertThrows(SecurityException.class,()->core.adminAssignFaction(actor,target,"Atreides",c->{throw new SecurityException("Case reassigned");}));
  assertEquals("Fremen",selected.get(targetId));
 }
 @Test void nativePermissionRevocationDuringLockedCallbackIsRechecked()throws Exception{
  selected.put(targetId,"Fremen");checkpoint(false);
  assertThrows(SecurityException.class,()->core.adminAssignFaction(actor,target,"Atreides",c->when(actor.hasPermission("dune.admin")).thenReturn(false)));
  assertEquals("Fremen",selected.get(targetId));
 }
 @Test void disconnectedTargetDuringLockedCallbackCannotBeEdited()throws Exception{
  selected.put(targetId,"Fremen");checkpoint(false);
  assertThrows(SecurityException.class,()->core.adminAssignFaction(actor,target,"Atreides",c->when(target.isOnline()).thenReturn(false)));
  assertEquals("Fremen",selected.get(targetId));
 }
 @Test void checkpointSqlFailureRestoresPriorMapAndRethrows()throws Exception{
  selected.put(targetId,"Corrino");checkpoint(true);
  assertThrows(SQLException.class,()->core.adminAssignFaction(actor,target,"Atreides",c->{}));
  assertEquals("Corrino",selected.get(targetId));verifyNoInteractions(connection);
 }
 @Test void rollbackPreservesAbsentVersusEmptyFactionEntry()throws Exception{
  checkpoint(true);
  assertThrows(SQLException.class,()->core.adminAssignFaction(actor,target,"Ordos",c->{}));assertFalse(selected.containsKey(targetId));
  selected.put(targetId,"");assertThrows(SQLException.class,()->core.adminAssignFaction(actor,target,"Ordos",c->{}));assertTrue(selected.containsKey(targetId));assertEquals("",selected.get(targetId));
 }
 @Test void unknownCommitFailureDoesNotTryAnotherCheckpointOrSqlWrite()throws Exception{
  selected.put(targetId,"Fremen");
  doAnswer(call->{Consumer<Connection> operation=call.getArgument(1);operation.accept(connection);when(target.isOnline()).thenReturn(false);throw new SQLException("Unknown commit; existing Core already fenced the session");}).when(core).transactNetworkInventory(eq(target),any());
  assertThrows(SQLException.class,()->core.adminAssignFaction(actor,target,"Atreides",c->{}));assertEquals("Fremen",selected.get(targetId));
  verify(core,times(1)).transactNetworkInventory(eq(target),any());verifyNoInteractions(connection);
 }
 @Test void nonCanonicalFactionsFailBeforeAnyTransaction()throws Exception{
  for(String faction:List.of("atreides","Aedrecht","Vaerkring","","Atreides;op"))assertThrows(IllegalArgumentException.class,()->core.adminAssignFaction(actor,target,faction,c->{}));
  verify(core,never()).transactNetworkInventory(any(),any());
 }
 @Test void allFiveCanonicalHousesUseSameAuthorizedFacade()throws Exception{
  checkpoint(false);for(String faction:List.of("Fremen","Atreides","Harkonnen","Corrino","Ordos")){core.adminAssignFaction(actor,target,faction,c->{});assertEquals(faction,selected.get(targetId));}
 }
 @Test void offThreadOrUnprivilegedOrOfflineCallsNeverBeginCheckpoint()throws Exception{
  bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);assertThrows(IllegalStateException.class,()->core.adminAssignFaction(actor,target,"Atreides",c->{}));
  bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);when(actor.hasPermission("dune.admin")).thenReturn(false);assertThrows(SecurityException.class,()->core.adminAssignFaction(actor,target,"Atreides",c->{}));
  when(actor.hasPermission("dune.admin")).thenReturn(true);when(actor.isOnline()).thenReturn(false);assertThrows(SecurityException.class,()->core.adminAssignFaction(actor,target,"Atreides",c->{}));
  verify(core,never()).transactNetworkInventory(any(),any());
 }
}
