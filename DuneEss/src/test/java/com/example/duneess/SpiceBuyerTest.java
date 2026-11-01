package com.example.duneess;
import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.bukkit.NamespacedKey;
import java.math.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SpiceBuyerTest {
 static Connection database()throws Exception{Connection c=DriverManager.getConnection("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL");try(Statement s=c.createStatement()){s.execute("CREATE TABLE dune_economy_balances(uuid VARCHAR(36) PRIMARY KEY,balance DECIMAL(20,2))");s.execute(SpiceBuyer.SCHEMA);}return c;}
 static BigDecimal balance(Connection c,UUID id)throws Exception{try(PreparedStatement q=c.prepareStatement("SELECT balance FROM dune_economy_balances WHERE uuid=?")){q.setString(1,id.toString());try(ResultSet r=q.executeQuery()){assertTrue(r.next());return r.getBigDecimal(1);}}}
 @Test void buyerCreditsExactPremiumInsteadOfWithdrawingMoney()throws Exception{try(Connection c=database()){UUID player=UUID.randomUUID();SpiceBuyer.credit(c,UUID.randomUUID(),player,UUID.randomUUID(),16,SpiceBuyer.unit(15));assertEquals(new BigDecimal("324.00"),balance(c,player));try(ResultSet r=c.createStatement().executeQuery("SELECT quantity,total_price FROM dune_npc_spice_sales")){assertTrue(r.next());assertEquals(16,r.getInt(1));assertEquals(new BigDecimal("324.00"),r.getBigDecimal(2));}}}
 @Test void duplicateReceiptRollsBackSecondCredit()throws Exception{try(Connection c=database()){UUID player=UUID.randomUUID(),trade=UUID.randomUUID(),npc=UUID.randomUUID();SpiceBuyer.credit(c,trade,player,npc,1,SpiceBuyer.unit(15));c.setAutoCommit(false);assertThrows(SQLException.class,()->SpiceBuyer.credit(c,trade,player,npc,1,SpiceBuyer.unit(15)));c.rollback();assertEquals(new BigDecimal("20.25"),balance(c,player));}}
 @Test void auditFailureRollsBackWalletCredit()throws Exception{try(Connection c=database()){UUID player=UUID.randomUUID();c.createStatement().execute("INSERT INTO dune_economy_balances VALUES('"+player+"',10)");c.createStatement().execute("ALTER TABLE dune_npc_spice_sales ADD CHECK(quantity<16)");c.setAutoCommit(false);assertThrows(SQLException.class,()->SpiceBuyer.credit(c,UUID.randomUUID(),player,UUID.randomUUID(),16,SpiceBuyer.unit(15)));c.rollback();assertEquals(new BigDecimal("10.00"),balance(c,player));}}
 @Test void overflowCannotCreditOrCreateASaleReceipt()throws Exception{try(Connection c=database()){UUID player=UUID.randomUUID();c.createStatement().execute("INSERT INTO dune_economy_balances VALUES('"+player+"',999999999999999999.99)");c.setAutoCommit(false);assertThrows(SQLException.class,()->SpiceBuyer.credit(c,UUID.randomUUID(),player,UUID.randomUUID(),16,SpiceBuyer.unit(15)));c.rollback();assertEquals(new BigDecimal("999999999999999999.99"),balance(c,player));}}
 static ItemStack item(int amount,boolean tagged){ItemStack item=mock(ItemStack.class);ItemMeta meta=mock(ItemMeta.class);PersistentDataContainer pdc=mock(PersistentDataContainer.class);AtomicInteger n=new AtomicInteger(amount);when(item.getAmount()).thenAnswer(x->n.get());doAnswer(x->{n.set(x.getArgument(0));return null;}).when(item).setAmount(anyInt());when(item.hasItemMeta()).thenReturn(true);when(item.getItemMeta()).thenReturn(meta);when(meta.getPersistentDataContainer()).thenReturn(pdc);when(pdc.get(any(NamespacedKey.class),eq(PersistentDataType.STRING))).thenReturn(tagged?"spice":null);when(item.clone()).thenAnswer(x->item(n.get(),tagged));return item;}
 static Player player(ItemStack[] storage){Player player=mock(Player.class);PlayerInventory inv=mock(PlayerInventory.class);when(player.getInventory()).thenReturn(inv);when(inv.getStorageContents()).thenReturn(storage);doAnswer(x->{storage[x.getArgument(0)]=x.getArgument(1);return null;}).when(inv).setItem(anyInt(),any());return player;}
 @Test void removesOnlyAuthenticatedSpiceAcrossStacks(){ItemStack plain=item(64,false);ItemStack[] stock={item(7,true),plain,item(8,true)};Player p=player(stock);assertEquals(15,SpiceBuyer.quantity(p));SpiceBuyer.remove(p,11);assertNull(stock[0]);assertSame(plain,stock[1]);assertEquals(4,stock[2].getAmount());assertEquals(64,plain.getAmount());}
 @Test void staleOrInvalidQuantityCannotTakeItems(){ItemStack original=item(7,true);Player p=player(new ItemStack[]{original});for(int amount:new int[]{0,8,17})assertThrows(IllegalArgumentException.class,()->SpiceBuyer.remove(p,amount));verify(p.getInventory(),never()).setItem(anyInt(),any());assertEquals(7,original.getAmount());}
 @Test void quantityIsCappedToTheReviewedBatch(){assertEquals(16,SpiceBuyer.quantity(player(new ItemStack[]{item(64,true),item(64,true)})));assertEquals(0,SpiceBuyer.quantity(player(new ItemStack[]{item(64,false)})));assertEquals(new BigDecimal("1.35"),SpiceBuyer.unit(-1));}
}
