package com.dunerealms.clans;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.bukkit.entity.Player;
import com.example.dunegui.NativeMenus;
import java.lang.reflect.Method;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
class ClanMoneyStoreTest {
 String url;ClanStore store;ClanMoneyStore money;ClanStore.Context owner;
 @BeforeEach void setup()throws Exception{url="jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000";owner=new ClanStore.Context("00000000-0000-0000-0009-000000000001","clan-1","owner","Aerdrecht","AED",1,"blueprint-1",2,"Desert Kit","#c8ae62","[]",100);store=new ClanStore((c,u)->owner);money=new ClanMoneyStore();try(Connection c=open()){ClanStore.initialize(c);ClanMoneyStore.initialize(c);ClanMoneyStore.initialize(c);try(Statement s=c.createStatement()){s.execute("CREATE TABLE dune_economy_balances(uuid VARCHAR(36) PRIMARY KEY,balance DECIMAL(20,2) NOT NULL)");}}}
 Connection open()throws SQLException{return DriverManager.getConnection(url);}
 BigDecimal n(String v){return new BigDecimal(v);}
 ClanStore.Context role(String r){return new ClanStore.Context(owner.uuid(),owner.clan(),r,owner.name(),owner.tag(),1,owner.blueprint(),2,owner.kitName(),owner.color(),owner.lore(),100);}
 void seed(String wallet,String bank)throws Exception{try(Connection c=open()){try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_economy_balances VALUES(?,?)")){q.setString(1,owner.uuid());q.setBigDecimal(2,n(wallet));q.executeUpdate();}try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_clan_wallets VALUES(?,?,0)")){q.setString(1,owner.clan());q.setBigDecimal(2,n(bank));q.executeUpdate();}}}
 void assertMoney(Connection c,String wallet,String bank)throws Exception{assertEquals(0,n(wallet).compareTo(store.wallet(c,UUID.fromString(owner.uuid()))));assertEquals(0,n(bank).compareTo(money.balance(c,owner.clan(),false)));}
 long count(Connection c,String table)throws Exception{try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();return r.getLong(1);}}
 @Test void centsTransfersConserveTotalAndDoNotTouchLegacySpice()throws Exception{seed("250.25","0");try(Connection c=open()){c.setAutoCommit(false);store.change(c,owner,Map.of(ClanStore.Grade.rare,8L),"deposit","legacy");c.commit();money.transfer(c,role("member"),n("100.10"),false,UUID.randomUUID());c.commit();assertMoney(c,"150.15","100.10");money.transfer(c,role("officer"),n("20.05"),true,UUID.randomUUID());c.commit();assertMoney(c,"170.20","80.05");assertEquals(8L,store.balances(c,owner.clan(),false).get(ClanStore.Grade.rare));assertEquals(1,count(c,"dune_clan_bank_ledger"));assertEquals(2,money.ledger(c,owner.clan()).size());assertEquals(0,n("250.25").compareTo(store.wallet(c,UUID.fromString(owner.uuid())).add(money.balance(c,owner.clan(),false))));}}
 @Test void memberDepositAllowedButMemberAndUnknownAdminCannotWithdraw()throws Exception{seed("50","100");try(Connection c=open()){c.setAutoCommit(false);money.transfer(c,role("member"),n("10"),false,UUID.randomUUID());c.commit();for(String r:List.of("member","admin","administrator","op")){assertThrows(SQLException.class,()->money.transfer(c,role(r),n("10"),true,UUID.randomUUID()));c.rollback();}assertMoney(c,"40","110");assertEquals(1,count(c,"dune_clan_wallet_ledger"));}}
 @Test void invalidAmountsAndFenceRejectBeforeChanges()throws Exception{seed("100","100");for(String bad:List.of("0","-0.01","0.001","1.231","1000000.01"))assertThrows(SQLException.class,()->ClanMoneyStore.amount(n(bad)));assertEquals(n("0.01"),ClanMoneyStore.amount(n("0.01")));try(Connection c=open()){assertThrows(SQLException.class,()->money.transfer(c,owner,n("1"),false,UUID.randomUUID()));assertThrows(SQLException.class,()->money.purchase(c,owner,UUID.randomUUID()));assertMoney(c,"100","100");assertEquals(0,count(c,"dune_clan_wallet_ledger"));}}
 @Test void insufficientWalletOrBankCannotPartiallyMoveMoney()throws Exception{seed("10","5");try(Connection c=open()){c.setAutoCommit(false);assertThrows(SQLException.class,()->money.transfer(c,owner,n("11"),false,UUID.randomUUID()));c.rollback();assertThrows(SQLException.class,()->money.transfer(c,owner,n("6"),true,UUID.randomUUID()));c.rollback();assertMoney(c,"10","5");assertEquals(0,count(c,"dune_clan_wallet_ledger"));}}
 @Test void failureRollbackRestoresWalletBankLedgerAndOperation()throws Exception{seed("250.25","0");try(Connection c=open()){c.setAutoCommit(false);UUID id=UUID.randomUUID();store.operation(c,id,owner,"deposit");money.transfer(c,owner,n("100.10"),false,id);assertMoney(c,"150.15","100.10");c.rollback();assertMoney(c,"250.25","0");assertEquals(0,count(c,"dune_clan_operations"));assertEquals(0,count(c,"dune_clan_wallet_ledger"));assertEquals(0,count(c,"dune_clan_bank_ledger"));}}
 @Test void concurrentWithdrawalsAllowOnlyOneCommit()throws Exception{seed("0","100");ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);AtomicInteger success=new AtomicInteger();Callable<Void> action=()->{try(Connection c=open()){c.setAutoCommit(false);start.await();try{UUID id=UUID.randomUUID();store.operation(c,id,owner,"withdraw");money.transfer(c,owner,n("70"),true,id);c.commit();success.incrementAndGet();}catch(SQLException denied){c.rollback();}}return null;};try{var a=pool.submit(action);var b=pool.submit(action);start.countDown();a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);}finally{pool.shutdownNow();}assertEquals(1,success.get());try(Connection c=open()){assertMoney(c,"70","30");assertEquals(1,count(c,"dune_clan_wallet_ledger"));assertEquals(1,count(c,"dune_clan_operations"));}}
 @Test void bankKitPurchaseRequiresCurrentOfficerAndPriceComparison()throws Exception{seed("250","150");try(Connection c=open()){c.setAutoCommit(false);for(String r:List.of("member","admin","op")){assertThrows(SQLException.class,()->money.purchase(c,role(r),UUID.randomUUID()));c.rollback();}var stale=new ClanStore.Context(owner.uuid(),owner.clan(),owner.role(),owner.name(),owner.tag(),1,owner.blueprint(),2,owner.kitName(),owner.color(),owner.lore(),90);assertThrows(SQLException.class,()->owner.kitMatches(stale));UUID id=UUID.randomUUID();store.operation(c,id,owner,"kit-purchase");money.purchase(c,role("officer"),id);store.orderDunemarks(c,id,owner,"clan-bank");c.commit();assertMoney(c,"250","50");var entries=money.ledger(c,owner.clan());assertEquals(1,entries.size());assertEquals("kit-purchase",entries.getFirst().action());try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT funding,payment_currency,cost_dunemarks,cost_spice FROM dune_clan_kit_orders")){assertTrue(r.next());assertEquals("clan-bank",r.getString(1));assertEquals("dunemarks",r.getString(2));assertEquals(0,n("100").compareTo(r.getBigDecimal(3)));assertEquals(0,r.getLong(4));}assertEquals(0,n("-100").compareTo(entries.getFirst().delta()));assertEquals(0,count(c,"dune_clan_bank_balances"));assertEquals(0,count(c,"dune_clan_bank_ledger"));}}
 @Test void operationReplayIsRejectedBeforeTransferAgain()throws Exception{seed("250","0");UUID id=UUID.randomUUID();try(Connection c=open()){c.setAutoCommit(false);store.operation(c,id,owner,"deposit");money.transfer(c,owner,n("100"),false,id);c.commit();assertThrows(SQLException.class,()->{store.operation(c,id,owner,"deposit");money.transfer(c,owner,n("100"),false,id);});c.rollback();assertMoney(c,"150","100");assertEquals(1,count(c,"dune_clan_wallet_ledger"));}}

 @Test void nativeBankMenuUsesDunemarksAndRespectsOfficerVisibility()throws Exception{
  for(String role:List.of("owner","officer","member","op")){
   DuneClans plugin=mock(DuneClans.class,CALLS_REAL_METHODS);Player p=mock(Player.class);when(p.isOnline()).thenReturn(true);when(p.hasPermission("dune.clans.view")).thenReturn(true);
   var view=new DuneClans.View(role(role),n("100.10"),n("250.25"),List.of());
   Method show=DuneClans.class.getDeclaredMethod("show",Player.class,String.class,DuneClans.View.class);show.setAccessible(true);
   try(var menus=mockStatic(NativeMenus.class)){
    menus.when(()->NativeMenus.show(eq(plugin),eq(p),anyString(),anyString(),anyList())).thenAnswer(call->{
     String body=call.getArgument(3);List<NativeMenus.Choice> choices=call.getArgument(4);
     assertTrue(body.contains("Clan bank: 100.10 Dunemarks"));assertTrue(body.contains("Your wallet: 250.25 Dunemarks"));
     assertFalse(body.toLowerCase(Locale.ROOT).contains("spice"));
     assertTrue(choices.stream().anyMatch(x->x.label().startsWith("Deposit ")&&x.label().endsWith(" Dunemarks")));
     assertEquals(role.equals("owner")||role.equals("officer"),choices.stream().anyMatch(x->x.label().startsWith("Withdraw ")));
     assertFalse(choices.stream().anyMatch(x->(x.label()+x.description()).toLowerCase(Locale.ROOT).contains("spice")));return null;
    });show.invoke(plugin,p,"bank",view);menus.verify(()->NativeMenus.show(eq(plugin),eq(p),anyString(),anyString(),anyList()),times(1));
   }
  }
 }
 @Test void clanBankKitRollbackAlsoRestoresOrderReceipt()throws Exception{seed("250","150");try(Connection c=open()){c.setAutoCommit(false);UUID id=UUID.randomUUID();store.operation(c,id,owner,"kit-purchase");money.purchase(c,owner,id);store.orderDunemarks(c,id,owner,"clan-bank");assertMoney(c,"250","50");c.rollback();assertMoney(c,"250","150");assertEquals(0,count(c,"dune_clan_kit_orders"));assertEquals(0,count(c,"dune_clan_operations"));assertEquals(0,count(c,"dune_clan_wallet_ledger"));}}
}
