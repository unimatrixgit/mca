package com.example.dunerealms;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SharedDatabaseMysqlTest {
 @TempDir Path temp;
 private MockedStatic<DriverManager> drivers;
 private Connection schema,owner,operation;
 private Statement ddl;
 private DatabaseMetaData metadata;
 private PreparedStatement schemaLock,schemaRelease,lock,release,insert,read,write,query;
 private ResultSet profile,lockResult,queryResult;
 private SharedDatabase db;
 private Player player;
 private PlayerInventory inventory;
 private final UUID id=UUID.fromString("a1234567-1234-4234-8234-123456789abc");
 private final Map<UUID,String> factions=new HashMap<>();
 private final String url="jdbc:mysql://test.invalid:3306/dunegameplay";

 @BeforeEach void prepare() throws Exception {
  schema=mock(Connection.class);owner=mock(Connection.class);operation=mock(Connection.class);
  ddl=mock(Statement.class);metadata=mock(DatabaseMetaData.class);when(schema.createStatement()).thenReturn(ddl);when(schema.getMetaData()).thenReturn(metadata);when(schema.getCatalog()).thenReturn("dunegameplay");
  ResultSet exists=mock(ResultSet.class);when(exists.next()).thenReturn(true);when(metadata.getColumns(eq("dunegameplay"),isNull(),eq("dune_players"),anyString())).thenReturn(exists);
  schemaLock=mock(PreparedStatement.class);schemaRelease=mock(PreparedStatement.class);when(schema.prepareStatement("SELECT GET_LOCK(?,?)")).thenReturn(schemaLock);when(schema.prepareStatement("SELECT RELEASE_LOCK(?)")).thenReturn(schemaRelease);
  ResultSet schemaLocked=mock(ResultSet.class);when(schemaLock.executeQuery()).thenReturn(schemaLocked);when(schemaLocked.next()).thenReturn(true);when(schemaLocked.getInt(1)).thenReturn(1);
  PreparedStatement triggers=mock(PreparedStatement.class);ResultSet triggerFound=mock(ResultSet.class);when(schema.prepareStatement("SELECT 1 FROM INFORMATION_SCHEMA.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME=?")).thenReturn(triggers);when(triggers.executeQuery()).thenReturn(triggerFound);
  lock=mock(PreparedStatement.class);release=mock(PreparedStatement.class);insert=mock(PreparedStatement.class);read=mock(PreparedStatement.class);write=mock(PreparedStatement.class);query=mock(PreparedStatement.class);
  when(owner.prepareStatement(anyString())).thenAnswer(call->{String sql=call.getArgument(0);if(sql.startsWith("SELECT GET_LOCK"))return lock;if(sql.startsWith("SELECT RELEASE_LOCK"))return release;if(sql.startsWith("INSERT INTO dune_players"))return insert;if(sql.startsWith("SELECT faction"))return read;return write;});
  lockResult=mock(ResultSet.class);when(lock.executeQuery()).thenReturn(lockResult);when(lockResult.next()).thenReturn(true);when(lockResult.getInt(1)).thenReturn(1);
  profile=mock(ResultSet.class);when(read.executeQuery()).thenReturn(profile);when(profile.next()).thenReturn(true);when(profile.getString("faction")).thenReturn("fremen");when(write.executeUpdate()).thenReturn(1);
  queryResult=mock(ResultSet.class);when(operation.prepareStatement(anyString())).thenReturn(query);when(query.executeQuery()).thenReturn(queryResult);when(query.executeUpdate()).thenReturn(1);
  drivers=mockStatic(DriverManager.class);drivers.when(()->DriverManager.getConnection(eq(url),any(Properties.class))).thenReturn(schema,owner,operation);
  JavaPlugin plugin=mock(JavaPlugin.class);YamlConfiguration config=new YamlConfiguration();config.set("database.jdbc-url",url);config.set("database.username","dune");config.set("database.password","test-only");when(plugin.getConfig()).thenReturn(config);when(plugin.getDataFolder()).thenReturn(temp.toFile());when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
  db=new SharedDatabase(plugin);
  player=mock(Player.class);inventory=mock(PlayerInventory.class);when(player.getUniqueId()).thenReturn(id);when(player.getInventory()).thenReturn(inventory);when(inventory.getContents()).thenReturn(new ItemStack[41]);
 }
 @AfterEach void cleanup() throws Exception {if(db!=null)db.close();if(drivers!=null)drivers.close();}

 @Test void schemaUsesInnodbStringUuidsJsonAndDedicatedDdlLock() throws Exception {
  assertEquals(5,SharedDatabase.mysqlSchema().size());assertTrue(SharedDatabase.mysqlSchema().stream().allMatch(sql->sql.endsWith("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci")));
  assertTrue(SharedDatabase.mysqlSchema().getFirst().contains("inventory_view JSON"));assertTrue(SharedDatabase.mysqlSchema().getFirst().contains("VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin"));
  verify(schemaLock).setString(1,"dune:core:schema:v1");verify(schemaLock).setInt(2,10);verify(schemaRelease).setString(1,"dune:core:schema:v1");verify(schemaRelease).execute();verify(schema,never()).setAutoCommit(false);verify(ddl).executeUpdate("INSERT INTO dune_politics (id,yaml) VALUES (1,'') ON DUPLICATE KEY UPDATE id=id");
 }
 @Test void mysqlConnectionNeverReconnectsAndUsesMillisecondTimeoutsAndFoundRowCounts() throws Exception {
  drivers.verify(()->DriverManager.getConnection(eq(url),argThat(p->"false".equals(p.getProperty("autoReconnect"))&&"5000".equals(p.getProperty("connectTimeout"))&&"10000".equals(p.getProperty("socketTimeout"))&&"false".equals(p.getProperty("useAffectedRows"))&&"UTC".equals(p.getProperty("connectionTimeZone"))&&"false".equals(p.getProperty("allowMultiQueries")))));
 }
 @Test void namedProfileLockUsesFullUuidAndFailureNeverAppliesOrSavesItems() throws Exception {
  when(lockResult.getInt(1)).thenReturn(0);assertThrows(SQLException.class,()->db.loadPlayer(player,factions));assertFalse(db.isLoaded(id));verify(lock).setString(1,"dune:profile:"+id);verify(lock).setInt(2,0);verifyNoInteractions(read,write,inventory);verify(release).execute();verify(owner).close();
 }
 @Test void dirtyCrashMarkerFailsClosedAndReleasesOnlyTheOwnedConnection() throws Exception {
  when(profile.getBoolean("session_open")).thenReturn(true);assertThrows(SQLException.class,()->db.loadPlayer(player,factions));db.releasePlayer(player,factions);verifyNoInteractions(inventory);verify(write,never()).executeUpdate();verify(release).setString(1,"dune:profile:"+id);verify(owner).close();
 }
 @Test void savesJsonWithoutPostgresCastAndBindsUuidAsCanonicalString() throws Exception {
  db.loadPlayer(player,factions);db.savePlayer(player,factions);verify(owner).prepareStatement(contains("inventory_view=?,inventory_updated_at"));verify(insert).setString(1,id.toString());verify(read).setString(1,id.toString());verify(write).setString(9,id.toString());verify(write).setBoolean(7,true);verify(release,never()).execute();
  db.releasePlayer(player,factions);verify(write).setBoolean(7,false);verify(release).execute();verify(owner).close();
 }
 @Test void mysqlNumericConversionsAcceptDoubleProgressWithoutLossOfValidation() throws Exception {
  when(profile.getObject("experience_level")).thenReturn(12L);when(profile.getObject("experience_progress")).thenReturn(0.25d);when(profile.getObject("total_experience")).thenReturn(400L);when(profile.getObject("held_slot")).thenReturn((short)8);
  db.loadPlayer(player,factions);verify(player).setLevel(12);verify(player).setExp(.25f);verify(player).setTotalExperience(400);verify(inventory).setHeldItemSlot(8);
 }
 @Test void missingProfileAtSessionMarkerCannotApplyInventory() throws Exception {
  when(write.executeUpdate()).thenReturn(0);assertThrows(SQLException.class,()->db.loadPlayer(player,factions));verifyNoInteractions(inventory);assertFalse(db.isLoaded(id));verify(owner).close();
 }
 @Test void transactionCommitFailureQuarantinesProfileAndClosesNamedOwnership() throws Exception {
  db.loadPlayer(player,factions);doThrow(new SQLException("uncertain commit")).when(owner).commit();assertThrows(SQLException.class,()->db.transaction(player,factions,c->{}));assertFalse(db.isLoaded(id));verify(release).execute();verify(owner).close();verify(player).kickPlayer(anyString());clearInvocations(write);db.releasePlayer(player,factions);verifyNoInteractions(write);
 }
 @Test void transactionRollbackRestoresItemsAndKeepsTheSameNamedConnection() throws Exception {
  db.loadPlayer(player,factions);assertThrows(IllegalStateException.class,()->db.transaction(player,factions,c->{throw new IllegalStateException("no trade");}));verify(owner).rollback();verify(inventory).setContents(new ItemStack[41]);assertTrue(db.isLoaded(id));verify(release,never()).execute();
 }
 @Test void explicitReleaseFailureStillClosesConnectionToDropLock() throws Exception {
  db.loadPlayer(player,factions);SQLException failure=new SQLException("release failed");when(release.execute()).thenThrow(failure);assertThrows(SQLException.class,()->db.releasePlayer(player,factions));verify(owner).close();assertFalse(db.isLoaded(id));
 }
 @Test void permitIsOneAtomicConditionalDeleteAndDoesNotUseReturning() throws Exception {
  db.loadPlayer(player,factions);db.releasePlayer(player,factions);assertTrue(db.consumeArrakisPermit(id));verify(operation).prepareStatement("DELETE FROM dune_arrakis_permits WHERE player=? AND expires_at>?");verify(query).setString(1,id.toString());verify(query).executeUpdate();verify(query,never()).executeQuery();
 }
 @Test void bountyClaimLocksCurrentRowThenCommitsOneConditionalUpdate() throws Exception {
  db.loadPlayer(player,factions);db.releasePlayer(player,factions);when(queryResult.next()).thenReturn(true);when(queryResult.getInt(1)).thenReturn(500);
  assertEquals(500,db.claimBounty(id,UUID.fromString("b1234567-1234-4234-8234-123456789abc")));verify(operation).setAutoCommit(false);verify(operation).prepareStatement("SELECT reward FROM dune_bounties WHERE target=? AND claimed=FALSE FOR UPDATE");verify(operation).prepareStatement("UPDATE dune_bounties SET claimed=TRUE,hunter=? WHERE target=? AND claimed=FALSE");verify(operation).commit();
 }
 @Test void missingOrChangedBountyCannotReturnARewardOrCommit() throws Exception {
  db.loadPlayer(player,factions);db.releasePlayer(player,factions);when(queryResult.next()).thenReturn(false);assertEquals(0,db.claimBounty(id,UUID.randomUUID()));verify(operation).rollback();verify(operation,never()).commit();
  when(queryResult.next()).thenReturn(true);when(queryResult.getInt(1)).thenReturn(500);when(query.executeUpdate()).thenReturn(0);assertThrows(SQLException.class,()->db.claimBounty(id,UUID.randomUUID()));verify(operation,times(2)).rollback();verify(operation,never()).commit();
 }
 @Test void changedPoliticsAndItsEventCommitInTheSameConnectionTransaction() throws Exception {
  db.savePolitics("emperor: first\n");verify(owner).setAutoCommit(false);verify(owner).prepareStatement("UPDATE dune_politics SET yaml=? WHERE id=1 AND BINARY yaml<>BINARY ?");verify(write).setString(1,"emperor: first\n");verify(write).setString(2,"emperor: first\n");verify(owner).prepareStatement("INSERT INTO dune_game_events(topic) VALUES('politics')");verify(write,times(2)).executeUpdate();verify(owner).commit();verify(owner,never()).rollback();
 }
 @Test void unchangedPoliticsDoesNotEmitAnEvent() throws Exception {
  when(write.executeUpdate()).thenReturn(0);db.savePolitics("unchanged");verify(owner,never()).prepareStatement("INSERT INTO dune_game_events(topic) VALUES('politics')");verify(owner).commit();
 }
 @Test void failedPoliticsEventRollsBackTheYamlUpdateToo() throws Exception {
  SQLException failure=new SQLException("outbox unavailable");when(owner.prepareStatement("INSERT INTO dune_game_events(topic) VALUES('politics')")).thenThrow(failure);assertThrows(SQLException.class,()->db.savePolitics("changed"));verify(owner).rollback();verify(owner,never()).commit();
 }
 @Test void factionChangeTriggerIsInstalledAndOnlyEmitsWhenTheStoredFactionActuallyChanges() throws Exception {
  String trigger=SharedDatabase.mysqlFactionTrigger();assertTrue(trigger.contains("AFTER UPDATE ON dune_players FOR EACH ROW"));assertTrue(trigger.contains("IF NOT (OLD.faction <=> NEW.faction)"));assertTrue(trigger.contains("INSERT INTO dune_game_events(topic) VALUES('politics')"));assertFalse(trigger.contains("COMMIT"));verify(ddl).executeUpdate(trigger);
 }
}
