package com.dunerealms.atelier;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Receipt/checkpoint contract, also runnable against the installed plugin JAR. */
class DeliveryCheckpointTest {
 private final UUID owner=UUID.randomUUID(), order=UUID.randomUUID();
 private final String url="jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000";
 private final String snapshot="{\"blueprintId\":\""+UUID.randomUUID()+"\",\"name\":\"Original paid name\",\"color\":\"#123456\",\"quality\":\"rare\",\"lore\":[\"Original paid lore\"]}";
 private Connection database() throws Exception {
  Connection c=DriverManager.getConnection(url);
  try(Statement s=c.createStatement()) {
   s.execute("CREATE TABLE dune_kit_sales(id VARCHAR(36) PRIMARY KEY,buyer_uuid VARCHAR(36),snapshot_json TEXT,completed_at BIGINT,redeem_requested_at BIGINT,delivered_at BIGINT)");
   s.execute("CREATE TABLE inventory_checkpoint(player_uuid VARCHAR(36) PRIMARY KEY,pieces INT)");
  }
  try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_kit_sales VALUES(?,?,?,1,NULL,NULL)")) {
   q.setString(1,order.toString());q.setString(2,owner.toString());q.setString(3,snapshot);q.executeUpdate();
  }
  try(PreparedStatement q=c.prepareStatement("INSERT INTO inventory_checkpoint VALUES(?,0)")) {q.setString(1,owner.toString());q.executeUpdate();}
  c.setAutoCommit(false);return c;
 }
 private int pieces(Connection c) throws Exception {try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT pieces FROM inventory_checkpoint")){assertTrue(r.next());return r.getInt(1);}}
 private void addCheckpoint(Connection c) throws Exception {try(Statement q=c.createStatement()){assertEquals(1,q.executeUpdate("UPDATE inventory_checkpoint SET pieces=pieces+4"));}}
 @Test void twoConnectionsCannotConsumeTheSameReceipt() throws Exception {
  try(Connection first=database()) {
   first.commit();DeliveryStore store=new DeliveryStore();assertNotNull(store.lock(first,owner,order));
   CountDownLatch attempting=new CountDownLatch(1);ExecutorService worker=Executors.newSingleThreadExecutor();
   try {
    Future<Boolean> second=worker.submit(()->{try(Connection c=DriverManager.getConnection(url)){c.setAutoCommit(false);attempting.countDown();try{store.lock(c,owner,order);return false;}catch(SQLException expected){return true;}finally{c.rollback();}}});
    assertTrue(attempting.await(2,TimeUnit.SECONDS));assertThrows(TimeoutException.class,()->second.get(150,TimeUnit.MILLISECONDS));
    addCheckpoint(first);store.delivered(first,owner,order,2);first.commit();
    assertTrue(second.get(3,TimeUnit.SECONDS));assertEquals(4,pieces(first));assertTrue(store.pending(first,owner,false).isEmpty());
   } finally {first.rollback();worker.shutdownNow();assertTrue(worker.awaitTermination(6,TimeUnit.SECONDS));}
  }
 }
 @Test void checkpointAndReceiptRollbackTogetherAfterInsertionFailure() throws Exception {
  try(Connection c=database()) {
   c.commit();DeliveryStore store=new DeliveryStore();store.lock(c,owner,order);addCheckpoint(c);store.delivered(c,owner,order,2);
   c.rollback();assertEquals(0,pieces(c));assertNotNull(store.lock(c,owner,order));
   addCheckpoint(c);store.delivered(c,owner,order,3);c.commit();assertEquals(4,pieces(c));assertThrows(SQLException.class,()->store.lock(c,owner,order));
  }
 }
 @Test void snapshotNameLoreAndQualityRemainThePaidValuesAfterConsumption() throws Exception {
  try(Connection c=database()) {
   DeliveryStore store=new DeliveryStore();var design=store.lock(c,owner,order);
   assertEquals("Original paid name",design.name());assertEquals("Original paid lore",design.lore().getFirst());assertEquals("rare",design.quality());
   addCheckpoint(c);store.delivered(c,owner,order,2);c.commit();
   try(PreparedStatement q=c.prepareStatement("SELECT snapshot_json FROM dune_kit_sales WHERE id=?")){q.setString(1,order.toString());try(ResultSet r=q.executeQuery()){assertTrue(r.next());assertEquals(snapshot,r.getString(1));}}
  }
 }
 @Test void anotherOwnerCannotConsumeOrChangeTheCheckpoint() throws Exception {
  try(Connection c=database()) {
   DeliveryStore store=new DeliveryStore();UUID other=UUID.randomUUID();assertThrows(SQLException.class,()->store.lock(c,other,order));assertThrows(SQLException.class,()->store.delivered(c,other,order,2));
   assertEquals(0,pieces(c));assertNotNull(store.lock(c,owner,order));assertEquals(1,store.pending(c,owner,false).size());
  }
 }
}
