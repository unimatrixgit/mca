package com.example.duneess;

import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ExchangeStoreTest {
    private String url;
    private ExchangeStore store;
    private final UUID seller=UUID.randomUUID(),buyer=UUID.randomUUID();
    @BeforeEach void setup() throws Exception {
        url="jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        store=new ExchangeStore(()->DriverManager.getConnection(url));
        try(Connection c=DriverManager.getConnection(url);Statement s=c.createStatement()) {
            s.execute("CREATE TABLE dune_economy_balances(uuid UUID PRIMARY KEY,balance NUMERIC(20,2) NOT NULL)");
        }
        wallet(seller,"0"); wallet(buyer,"100"); store.initialize(); store.initialize();
    }
    private void wallet(UUID id,String amount) throws SQLException {
        try(Connection c=DriverManager.getConnection(url);PreparedStatement q=c.prepareStatement("INSERT INTO dune_economy_balances VALUES(?,?)")) {
            q.setObject(1,id);q.setBigDecimal(2,new BigDecimal(amount));q.executeUpdate();
        }
    }
    private BigDecimal balance(UUID id) throws SQLException {
        try(Connection c=DriverManager.getConnection(url);PreparedStatement q=c.prepareStatement("SELECT balance FROM dune_economy_balances WHERE uuid=?")) {
            q.setObject(1,id);try(ResultSet r=q.executeQuery()){assertTrue(r.next());return r.getBigDecimal(1);}
        }
    }
    private long listing(int amount,String price) throws SQLException {
        store.list(seller,"Seller","arrakis","spice","ORANGE_DYE",amount,new BigDecimal(price),"preserved-payload","Melange");
        return store.browse("all","all",null,0).getFirst().id();
    }
    @Test void finalItemIsDeletedAndBothWalletsChangeExactlyOnce() throws Exception {
        long id=listing(1,"12.50");
        assertEquals("preserved-payload",store.buy(buyer,id).payload());
        assertEquals(new BigDecimal("87.50"),balance(buyer));
        assertEquals(new BigDecimal("12.50"),balance(seller));
        assertTrue(store.browse("all","all",null,0).isEmpty());
        assertThrows(IllegalArgumentException.class,()->store.buy(buyer,id));
        assertEquals(new BigDecimal("87.50"),balance(buyer));
    }
    @Test void partiallyFilledStockRemainsAndCancellationReturnsOnlyUnsoldQuantity() throws Exception {
        long id=listing(3,"10");store.buy(buyer,id);
        assertEquals(2,store.find(id).quantity());
        assertEquals(2,store.cancel(seller,id).quantity());
        assertTrue(store.browse("all","all",null,0).isEmpty());
        assertEquals(new BigDecimal("10.00"),balance(seller));
    }
    @Test void insufficientFundsAndSelfPurchaseCannotChangeStock() throws Exception {
        long id=listing(2,"101");
        assertThrows(IllegalArgumentException.class,()->store.buy(buyer,id));
        assertThrows(IllegalArgumentException.class,()->store.buy(seller,id));
        assertEquals(2,store.find(id).quantity());assertEquals(new BigDecimal("100.00"),balance(buyer));
    }
    @Test void wrongOwnerCannotCancelOrReclaim() throws Exception {
        long id=listing(2,"1");
        assertThrows(IllegalArgumentException.class,()->store.cancel(buyer,id));
        assertEquals(2,store.find(id).quantity());
    }
    @Test void failedSellerCreditRollsBackBuyerDebit() throws Exception {
        long id=listing(1,"10");
        try(Connection c=DriverManager.getConnection(url);PreparedStatement q=c.prepareStatement("DELETE FROM dune_economy_balances WHERE uuid=?")) {
            q.setObject(1,seller);q.executeUpdate();
        }
        assertThrows(IllegalArgumentException.class,()->store.buy(buyer,id));
        assertEquals(new BigDecimal("100.00"),balance(buyer));assertEquals(1,store.find(id).quantity());
    }
    @Test void filtersAndPaginationIncludeAllCategoriesWithoutEmptyCategoryPredicate() throws Exception {
        listing(1,"1");
        store.list(seller,"Seller","caladan","building","OAK_LOG",64,new BigDecimal("2"),null,null);
        assertEquals(2,store.browse("all","all",null,0).size());
        assertEquals(1,store.browse("spice","arrakis",null,0).size());
        assertTrue(store.browse("spice","caladan",null,0).isEmpty());
        assertTrue(store.browse("all","all",buyer,0).isEmpty());
        for(int i=0;i<30;i++) listing(1,"1");
        assertEquals(29,store.browse("all","all",seller,0).size());
        assertEquals(4,store.browse("all","all",seller,1).size());
    }
    @Test void concurrentBuyersCannotBothBuyTheLastItem() throws Exception {
        UUID other=UUID.randomUUID(); wallet(other,"100");long id=listing(1,"25");
        try(ExecutorService pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch start=new CountDownLatch(1);
            List<Future<Boolean>> results=new ArrayList<>();
            for(UUID player:List.of(buyer,other)) results.add(pool.submit(()->{
                start.await();try{store.buy(player,id);return true;}catch(IllegalArgumentException ex){return false;}
            }));
            start.countDown();int successes=0;for(Future<Boolean> result:results)if(result.get(5,TimeUnit.SECONDS)) successes++;
            assertEquals(1,successes);assertEquals(new BigDecimal("25.00"),balance(seller));
            assertEquals(new BigDecimal("175.00"),balance(buyer).add(balance(other)));
        }
    }
    @Test void invalidAmountsAndPricesAreRejectedBeforeWriting() {
        for(String price:List.of("0","-1","1000001","0.001"))
            assertThrows(IllegalArgumentException.class,()->listing(1,price));
        assertThrows(IllegalArgumentException.class,()->listing(0,"1"));
        assertThrows(IllegalArgumentException.class,()->listing(65,"1"));
    }
    @Test void committedSaleRecordsBuyerItemAndExactPaymentButFailuresDoNot() throws Exception {
        long id=listing(2,"12.50");store.buy(buyer,"BuyerName",id);
        try(Connection c=DriverManager.getConnection(url);Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT * FROM dune_exchange_sales")) {
            assertTrue(r.next());assertEquals(seller,r.getObject("seller",UUID.class));
            assertEquals(buyer,r.getObject("buyer",UUID.class));assertEquals("BuyerName",r.getString("buyer_name"));
            assertEquals(1,r.getInt("quantity"));assertEquals(new BigDecimal("12.50"),r.getBigDecimal("total_price"));assertFalse(r.next());
        }
        assertThrows(IllegalArgumentException.class,()->store.buy(seller,id));store.cancel(seller,id);
        try(Connection c=DriverManager.getConnection(url);Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT COUNT(*) FROM dune_exchange_sales")) {
            assertTrue(r.next());assertEquals(1,r.getInt(1));
        }
    }
    @Test void externallyOwnedTransactionRollsBackStockWalletsAndHistoryTogether() throws Exception {
        long id=listing(1,"25");
        try(Connection c=DriverManager.getConnection(url)) {
            c.setAutoCommit(false);ExchangeStore.bound(c).buy(buyer,"Buyer",id);
            assertFalse(c.isClosed());c.rollback();
        }
        assertEquals(1,store.find(id).quantity());assertEquals(new BigDecimal("100.00"),balance(buyer));
        assertEquals(new BigDecimal("0.00"),balance(seller));
        try(Connection c=DriverManager.getConnection(url);Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT COUNT(*) FROM dune_exchange_sales")) {
            assertTrue(r.next());assertEquals(0,r.getInt(1));
        }
    }
}
