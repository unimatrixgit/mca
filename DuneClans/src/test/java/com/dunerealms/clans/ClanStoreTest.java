package com.dunerealms.clans;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.dunerealms.clans.ClanStore.Grade.*;

class ClanStoreTest {
 String url;ClanStore store;ClanStore.Context ctx;
 @BeforeEach void setup()throws Exception{
  url="jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000";
  ctx=context("owner",200);store=new ClanStore((c,p)->ctx);
  try(Connection c=open()){ClanStore.initialize(c);ClanStore.initialize(c);}
 }
 Connection open()throws SQLException{return DriverManager.getConnection(url);}
 ClanStore.Context context(String role,long price){return new ClanStore.Context("00000000-0000-0000-0009-000000000001","clan-1",role,"Aerdrecht","AED",1,"blueprint-1",3,"Desert Kit","#c8ae62","[]",price);}
 long count(Connection c,String table)throws Exception{try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();return r.getLong(1);}}
 void seed(Map<ClanStore.Grade,Long> values)throws Exception{try(Connection c=open()){c.setAutoCommit(false);store.change(c,ctx,values,"deposit","seed");c.commit();}}
 @Test void ddlAndInjectedActualBedrockUuidContext()throws Exception{try(Connection c=open()){assertSame(ctx,store.context(c,UUID.fromString(ctx.uuid())));assertEquals(5,store.balances(c,ctx.clan(),false).size());assertEquals(0,count(c,"dune_clan_kit_orders"));}}
 @Test void allMutationsRequireCallerTransaction()throws Exception{try(Connection c=open()){
  assertThrows(SQLException.class,()->store.balances(c,ctx.clan(),true));assertThrows(SQLException.class,()->store.operation(c,UUID.randomUUID(),ctx,"deposit"));
  assertThrows(SQLException.class,()->store.change(c,ctx,Map.of(common,1L),"deposit","x"));assertThrows(SQLException.class,()->store.order(c,UUID.randomUUID(),ctx,"bank",Map.of(common,200L)));
  assertEquals(0,count(c,"dune_clan_bank_balances"));
 }}
 @Test void gradeConservationAndLedger()throws Exception{seed(Map.of(common,10L,uncommon,20L,rare,30L,very_rare,40L,legendary,50L));try(Connection c=open()){
  c.setAutoCommit(false);store.change(c,ctx,Map.of(common,-3L,rare,-7L),"withdraw","withdraw-test");c.commit();
  var b=store.balances(c,ctx.clan(),false);assertEquals(7L,b.get(common));assertEquals(20L,b.get(uncommon));assertEquals(23L,b.get(rare));assertEquals(40L,b.get(very_rare));assertEquals(50L,b.get(legendary));
  try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT quality,SUM(delta),MAX(balance_after) FROM dune_clan_bank_ledger GROUP BY quality")){while(r.next())assertEquals(b.get(ClanStore.Grade.valueOf(r.getString(1))).longValue(),r.getLong(2));}
  assertEquals(7,store.ledger(c,ctx.clan()).size());
 }}
 @Test void insufficiencyCannotWashAnotherGrade()throws Exception{seed(Map.of(common,10L,rare,100L));try(Connection c=open()){
  c.setAutoCommit(false);assertThrows(SQLException.class,()->store.change(c,ctx,Map.of(common,-11L),"withdraw","x"));c.rollback();
  assertEquals(10L,store.balances(c,ctx.clan(),false).get(common));assertEquals(100L,store.balances(c,ctx.clan(),false).get(rare));assertEquals(2,count(c,"dune_clan_bank_ledger"));
 }}
 @Test void overflowAndLimitFailClosed()throws Exception{seed(Map.of(common,10L));try(Connection c=open()){
  c.setAutoCommit(false);assertThrows(SQLException.class,()->store.change(c,ctx,Map.of(common,Long.MAX_VALUE),"deposit","x"));c.rollback();
  assertThrows(SQLException.class,()->store.change(c,ctx,Map.of(common,1000000000001L),"deposit","x"));c.rollback();assertEquals(10L,store.balances(c,ctx.clan(),false).get(common));
 }}
 @Test void operationUuidCannotReplay()throws Exception{UUID id=UUID.randomUUID();try(Connection c=open()){
  c.setAutoCommit(false);store.operation(c,id,ctx,"withdraw");c.commit();assertThrows(SQLException.class,()->store.operation(c,id,ctx,"withdraw"));c.rollback();assertEquals(1,count(c,"dune_clan_operations"));
 }}
 @Test void callerRollbackUndoesOperationLedgerBalanceAndReceipt()throws Exception{seed(Map.of(common,250L));try(Connection c=open()){
  c.setAutoCommit(false);UUID id=UUID.randomUUID();store.operation(c,id,ctx,"kit");store.change(c,ctx,Map.of(common,-200L),"kit","order");store.order(c,id,ctx,"bank",Map.of(common,200L));
  assertEquals(1,count(c,"dune_clan_operations"));assertEquals(1,count(c,"dune_clan_kit_orders"));c.rollback();assertEquals(0,count(c,"dune_clan_operations"));assertEquals(0,count(c,"dune_clan_kit_orders"));assertEquals(1,count(c,"dune_clan_bank_ledger"));assertEquals(250L,store.balances(c,ctx.clan(),false).get(common));
 }}
 @Test void staleAllocationCannotSpendNowInsufficientGrades()throws Exception{seed(Map.of(common,100L,rare,100L));EnumMap<ClanStore.Grade,Long> preview;
  try(Connection c=open()){preview=ClanStore.allocate(store.balances(c,ctx.clan(),false),80);}
  try(Connection c=open()){c.setAutoCommit(false);store.change(c,ctx,Map.of(common,-50L),"withdraw","race");c.commit();}
  try(Connection c=open()){c.setAutoCommit(false);assertThrows(SQLException.class,()->store.change(c,ctx,ClanStore.negative(preview),"kit","stale"));c.rollback();assertEquals(50L,store.balances(c,ctx.clan(),false).get(common));assertEquals(100L,store.balances(c,ctx.clan(),false).get(rare));}
 }
 @Test void allocationUsesLowGradesWithoutChangingQuality(){assertThrows(SQLException.class,()->ClanStore.allocate(Map.of(common,2L),3));assertThrows(SQLException.class,()->ClanStore.allocate(Map.of(),0));assertThrows(SQLException.class,()->ClanStore.allocate(Map.of(legendary,2000000L),1000001));assertDoesNotThrow(()->assertEquals(Map.of(common,2L,uncommon,3L,rare,2L),ClanStore.allocate(Map.of(common,2L,uncommon,3L,rare,10L),7)));}
 @Test void kitPreviewRejectsChangedPriceBlueprintAndMembership(){var expected=context("owner",200);assertDoesNotThrow(()->expected.kitMatches(expected));assertThrows(SQLException.class,()->context("owner",201).kitMatches(expected));var changed=new ClanStore.Context(ctx.uuid(),"other","owner",ctx.name(),ctx.tag(),ctx.clanVersion(),ctx.blueprint(),ctx.blueprintVersion(),ctx.kitName(),ctx.color(),ctx.lore(),ctx.price());assertThrows(SQLException.class,()->changed.kitMatches(expected));var revision=new ClanStore.Context(ctx.uuid(),ctx.clan(),ctx.role(),ctx.name(),ctx.tag(),1,ctx.blueprint(),4,ctx.kitName(),ctx.color(),ctx.lore(),ctx.price());assertThrows(SQLException.class,()->revision.kitMatches(expected));assertThrows(SQLException.class,()->context("owner",0).kitMatches(context("owner",0)));assertThrows(SQLException.class,()->context("owner",1000001).kitMatches(context("owner",1000001)));assertTrue(context("officer",200).officer());assertFalse(context("member",200).officer());assertFalse(context("admin",200).officer());}
 @Test void concurrentWithdrawalsSerializeAndOnlyOneCanSpend()throws Exception{seed(Map.of(common,100L));ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);AtomicInteger success=new AtomicInteger();
  Callable<Void> action=()->{try(Connection c=open()){c.setAutoCommit(false);start.await();try{store.change(c,ctx,Map.of(common,-70L),"withdraw","race");c.commit();success.incrementAndGet();}catch(SQLException denied){c.rollback();}}return null;};
  try{var a=pool.submit(action);var b=pool.submit(action);start.countDown();a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
  assertEquals(1,success.get());try(Connection c=open()){assertEquals(30L,store.balances(c,ctx.clan(),false).get(common));assertEquals(2,count(c,"dune_clan_bank_ledger"));}
 }
 @Test void malformedDatabaseRoleCannotAuthorize()throws Exception{Connection c=mock(Connection.class);CallableStatement q=mock(CallableStatement.class);ResultSet r=mock(ResultSet.class);UUID player=UUID.fromString(ctx.uuid());when(c.prepareCall(anyString())).thenReturn(q);when(q.execute()).thenReturn(true);when(q.getResultSet()).thenReturn(r);when(r.next()).thenReturn(true,false);when(r.getString("minecraft_uuid")).thenReturn(player.toString());when(r.getString("clan_role")).thenReturn("administrator");assertThrows(SQLException.class,()->ClanStore.lockedContext(c,player));}
 @Test void mismatchedDatabaseUuidCannotAuthorize()throws Exception{Connection c=mock(Connection.class);CallableStatement q=mock(CallableStatement.class);ResultSet r=mock(ResultSet.class);when(c.prepareCall(anyString())).thenReturn(q);when(q.execute()).thenReturn(true);when(q.getResultSet()).thenReturn(r);when(r.next()).thenReturn(true);when(r.getString("minecraft_uuid")).thenReturn(UUID.randomUUID().toString());assertThrows(SQLException.class,()->ClanStore.lockedContext(c,UUID.fromString(ctx.uuid())));}
}
