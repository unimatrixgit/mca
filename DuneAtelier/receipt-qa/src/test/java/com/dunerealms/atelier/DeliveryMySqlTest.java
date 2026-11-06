package com.dunerealms.atelier;
import org.junit.jupiter.api.*;import java.nio.file.*;import java.sql.*;import java.util.*;import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
/** Explicit opt-in profile only. All DDL follows the dedicated-server identity guard. */
class DeliveryMySqlTest {
 final UUID owner=UUID.randomUUID(),order=UUID.randomUUID();
 final String snapshot="{\"blueprintId\":\""+UUID.randomUUID()+"\",\"name\":\"Original paid name\",\"color\":\"#123456\",\"quality\":\"rare\",\"lore\":[\"Original paid lore\"]}";
 @BeforeAll static void artifactIdentity()throws Exception{
  String artifact=System.getProperty("installedArtifact");Path actual=Path.of(DeliveryStore.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
  if(artifact!=null)assertEquals(Path.of(artifact).toRealPath(),actual,"Installed store must not be shadowed by compiled source");else assertTrue(Files.isDirectory(actual),"Source store classes required");
 }
 @BeforeEach void seed()throws Exception{
  try(Connection c=ReceiptCrashProcess.connect();Statement s=c.createStatement()){
   s.execute("DROP TABLE IF EXISTS dune_kit_sales");s.execute("DROP TABLE IF EXISTS inventory_checkpoint");
   s.execute("CREATE TABLE dune_kit_sales(id CHAR(36) PRIMARY KEY,buyer_uuid CHAR(36) NOT NULL,snapshot_json TEXT NOT NULL,completed_at BIGINT NOT NULL,redeem_requested_at BIGINT,delivered_at BIGINT) ENGINE=InnoDB");
   s.execute("CREATE TABLE inventory_checkpoint(player_uuid CHAR(36) PRIMARY KEY,pieces INT NOT NULL) ENGINE=InnoDB");
   try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_kit_sales VALUES(?,?,?,1,NULL,NULL)")){q.setString(1,order.toString());q.setString(2,owner.toString());q.setString(3,snapshot);q.executeUpdate();}
   try(PreparedStatement q=c.prepareStatement("INSERT INTO inventory_checkpoint VALUES(?,0)")){q.setString(1,owner.toString());q.executeUpdate();}
  }
 }
 Connection transaction()throws Exception{Connection c=ReceiptCrashProcess.connect();c.setAutoCommit(false);return c;}
 int pieces(Connection c)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT pieces FROM inventory_checkpoint")){assertTrue(r.next());return r.getInt(1);}}
 void checkpoint(Connection c)throws SQLException{try(PreparedStatement q=c.prepareStatement("UPDATE inventory_checkpoint SET pieces=pieces+4 WHERE player_uuid=?")){q.setString(1,owner.toString());assertEquals(1,q.executeUpdate());}}
 void unchangedSnapshot(Connection c)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT snapshot_json FROM dune_kit_sales")){assertTrue(r.next());assertEquals(snapshot,r.getString(1));}}
 void crash(String mode)throws Exception{
  List<String> command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-cp",System.getProperty("receiptTestClasspath"),ReceiptCrashProcess.class.getName(),mode,owner.toString(),order.toString()));
  Process process=new ProcessBuilder(command).redirectErrorStream(true).start();try{assertTrue(process.waitFor(15,TimeUnit.SECONDS),"Isolated crash child did not finish");String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);assertEquals(97,process.exitValue(),output);assertTrue(output.contains("isolated-receipt-crash:"+mode),output);}finally{if(process.isAlive()){process.destroyForcibly();assertTrue(process.waitFor(5,TimeUnit.SECONDS));}}
 }
 @Test void realInnoDbRowLockSerializesTwoReceiptClaimants()throws Exception{
  DeliveryStore store=new DeliveryStore();try(Connection first=transaction()){
   assertNotNull(store.lock(first,owner,order));CountDownLatch attempting=new CountDownLatch(1);ExecutorService worker=Executors.newSingleThreadExecutor();
   try{Future<Boolean> second=worker.submit(()->{try(Connection c=transaction()){attempting.countDown();try{store.lock(c,owner,order);return false;}catch(SQLException expected){return true;}finally{c.rollback();}}});
    assertTrue(attempting.await(3,TimeUnit.SECONDS));assertThrows(TimeoutException.class,()->second.get(150,TimeUnit.MILLISECONDS));checkpoint(first);store.delivered(first,owner,order,2);first.commit();assertTrue(second.get(5,TimeUnit.SECONDS));assertEquals(4,pieces(first));assertTrue(store.pending(first,owner,false).isEmpty());unchangedSnapshot(first);
   }finally{first.rollback();worker.shutdownNow();assertTrue(worker.awaitTermination(10,TimeUnit.SECONDS));}
  }
 }
 @Test void realInsertionFailureRollsBackReceiptAndCheckpointTogether()throws Exception{
  DeliveryStore store=new DeliveryStore();try(Connection c=transaction()){store.lock(c,owner,order);checkpoint(c);store.delivered(c,owner,order,2);assertThrows(SQLException.class,()->{try(PreparedStatement q=c.prepareStatement("INSERT INTO inventory_checkpoint VALUES(?,99)")){q.setString(1,owner.toString());q.executeUpdate();}});c.rollback();assertEquals(0,pieces(c));store.lock(c,owner,order);checkpoint(c);store.delivered(c,owner,order,3);c.commit();assertEquals(4,pieces(c));unchangedSnapshot(c);}
 }
 @Test void closingAnUncommittedConnectionReleasesReceiptForRetry()throws Exception{
  DeliveryStore store=new DeliveryStore();try(Connection c=transaction()){store.lock(c,owner,order);checkpoint(c);store.delivered(c,owner,order,2);}
  try(Connection fresh=transaction()){assertEquals(0,pieces(fresh));assertNotNull(store.lock(fresh,owner,order));assertEquals(1,store.pending(fresh,owner,false).size());fresh.rollback();}
 }
 @Test void abruptJvmExitBeforeCommitRollsBackAndRetryCommitsOnce()throws Exception{
  crash("before-commit");DeliveryStore store=new DeliveryStore();try(Connection c=transaction()){assertEquals(0,pieces(c));store.lock(c,owner,order);checkpoint(c);store.delivered(c,owner,order,3);c.commit();assertEquals(4,pieces(c));assertThrows(SQLException.class,()->store.lock(c,owner,order));unchangedSnapshot(c);}
 }
 @Test void abruptJvmExitAfterCommitCannotConsumeTheReceiptAgain()throws Exception{
  crash("after-commit");DeliveryStore store=new DeliveryStore();try(Connection c=transaction()){assertEquals(4,pieces(c));assertThrows(SQLException.class,()->store.lock(c,owner,order));assertThrows(SQLException.class,()->store.delivered(c,owner,order,3));assertTrue(store.pending(c,owner,false).isEmpty());unchangedSnapshot(c);c.rollback();}
 }
 @Test void ownershipQueueAndImmutablePaidValuesSurviveNativeSqlRoundTrip()throws Exception{
  DeliveryStore store=new DeliveryStore();try(Connection c=transaction()){UUID wrong=UUID.randomUUID();assertThrows(SQLException.class,()->store.lock(c,wrong,order));assertThrows(SQLException.class,()->store.delivered(c,wrong,order,2));assertEquals(0,pieces(c));assertEquals(1,store.pending(c,owner,false).size());assertTrue(store.pending(c,owner,true).isEmpty());try(Statement s=c.createStatement()){s.executeUpdate("UPDATE dune_kit_sales SET redeem_requested_at=2");}assertEquals(1,store.pending(c,owner,true).size());var design=store.lock(c,owner,order);assertEquals("Original paid name",design.name());assertEquals("Original paid lore",design.lore().getFirst());assertEquals("rare",design.quality());unchangedSnapshot(c);c.rollback();}
 }
}
