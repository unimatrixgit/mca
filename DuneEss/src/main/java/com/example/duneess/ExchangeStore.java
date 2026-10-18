package com.example.duneess;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** SQL escrow and wallet changes share one transaction. Bukkit inventory work stays on its server thread. */
final class ExchangeStore {
    interface Connections { Connection open() throws SQLException; }
    record Listing(long id, UUID seller, String sellerName, String origin, String category,
                   String material, int quantity, BigDecimal price, String payload, String displayName) {}
    private final Connections connections;
    ExchangeStore(Connections connections) { this.connections = connections; }
    void initialize() throws SQLException {
        try (Connection c = connections.open(); Statement s = c.createStatement()) {
            s.setQueryTimeout(5);
            s.executeUpdate("CREATE TABLE IF NOT EXISTS dune_exchange_listings (id BIGSERIAL PRIMARY KEY, seller UUID NOT NULL, seller_name VARCHAR(32) NOT NULL, origin VARCHAR(48) NOT NULL, category VARCHAR(24) NOT NULL, material VARCHAR(80) NOT NULL, quantity INT NOT NULL CHECK(quantity>0), price NUMERIC(20,2) NOT NULL CHECK(price>0))");
            s.executeUpdate("ALTER TABLE dune_exchange_listings ADD COLUMN IF NOT EXISTS payload TEXT");
            s.executeUpdate("ALTER TABLE dune_exchange_listings ADD COLUMN IF NOT EXISTS display_name VARCHAR(120)");
        }
    }
    private PreparedStatement prepare(Connection c, String sql) throws SQLException {
        PreparedStatement q = c.prepareStatement(sql); q.setQueryTimeout(5); return q;
    }
    static void validate(int amount, BigDecimal price) {
        if (amount < 1 || amount > 64 || price == null || price.signum() <= 0 || price.scale() > 2
                || price.compareTo(new BigDecimal("1000000")) > 0)
            throw new IllegalArgumentException("Use 1–64 items and a price from 0.01 to 1,000,000 Solaris (two decimals maximum).");
    }
    void list(UUID seller, String name, String origin, String category, String material,
              int amount, BigDecimal price, String payload, String displayName) throws SQLException {
        validate(amount, price);
        try (Connection c = connections.open(); PreparedStatement q = prepare(c,
                "INSERT INTO dune_exchange_listings(seller,seller_name,origin,category,material,quantity,price,payload,display_name) VALUES(?,?,?,?,?,?,?,?,?)")) {
            q.setObject(1,seller); q.setString(2,name); q.setString(3,origin); q.setString(4,category);
            q.setString(5,material); q.setInt(6,amount); q.setBigDecimal(7,price); q.setString(8,payload); q.setString(9,displayName);
            q.executeUpdate();
        }
    }
    List<Listing> browse(String category, String planet, UUID owner, int page) throws SQLException {
        String sql = "SELECT * FROM dune_exchange_listings WHERE 1=1";
        if (!category.equals("all")) sql += " AND category=?";
        if (!planet.equals("all")) sql += " AND origin=?";
        if (owner != null) sql += " AND seller=?";
        sql += " ORDER BY id DESC LIMIT 29 OFFSET ?";
        try (Connection c = connections.open(); PreparedStatement q = prepare(c,sql)) {
            int k=1;
            if (!category.equals("all")) q.setString(k++,category);
            if (!planet.equals("all")) q.setString(k++,planet);
            if (owner!=null) q.setObject(k++,owner);
            q.setInt(k,Math.max(0,page)*28);
            try (ResultSet r=q.executeQuery()) {
                List<Listing> rows=new ArrayList<>(); while(r.next()) rows.add(read(r)); return rows;
            }
        }
    }
    Listing find(long id) throws SQLException {
        try(Connection c=connections.open(); PreparedStatement q=prepare(c,"SELECT * FROM dune_exchange_listings WHERE id=?")) {
            q.setLong(1,id); try(ResultSet r=q.executeQuery()) {
                if(!r.next()) throw new IllegalArgumentException("That listing has already been sold or cancelled.");
                return read(r);
            }
        }
    }
    private Listing read(ResultSet r) throws SQLException {
        return new Listing(r.getLong("id"),r.getObject("seller",UUID.class),r.getString("seller_name"),
            r.getString("origin"),r.getString("category"),r.getString("material"),r.getInt("quantity"),
            r.getBigDecimal("price"),r.getString("payload"),r.getString("display_name"));
    }
    /** On SQL failure, no wallet or listing changes survive. Never set quantity to zero under CHECK(quantity>0). */
    Listing buy(UUID buyer, long id) throws SQLException {
        try(Connection c=connections.open()) {
            c.setAutoCommit(false);
            try(PreparedStatement q=prepare(c,"SELECT * FROM dune_exchange_listings WHERE id=? FOR UPDATE")) {
                q.setLong(1,id);
                try(ResultSet r=q.executeQuery()) {
                    if(!r.next()) throw new IllegalArgumentException("Listing unavailable.");
                    Listing offer=read(r);
                    if(offer.seller().equals(buyer)) throw new IllegalArgumentException("Use My Listings to reclaim your own offer.");
                    try(PreparedStatement debit=prepare(c,"UPDATE dune_economy_balances SET balance=balance-? WHERE uuid=? AND balance>=?");
                        PreparedStatement credit=prepare(c,"UPDATE dune_economy_balances SET balance=balance+? WHERE uuid=?");
                        PreparedStatement stock=prepare(c,offer.quantity()==1?"DELETE FROM dune_exchange_listings WHERE id=?":"UPDATE dune_exchange_listings SET quantity=quantity-1 WHERE id=?")) {
                        debit.setBigDecimal(1,offer.price()); debit.setObject(2,buyer); debit.setBigDecimal(3,offer.price());
                        if(debit.executeUpdate()!=1) throw new IllegalArgumentException("Insufficient Solaris or shared wallet unavailable.");
                        credit.setBigDecimal(1,offer.price()); credit.setObject(2,offer.seller());
                        if(credit.executeUpdate()!=1) throw new IllegalArgumentException("Seller wallet unavailable; no payment taken.");
                        stock.setLong(1,id); if(stock.executeUpdate()!=1) throw new SQLException("Listing changed.");
                        c.commit(); return offer;
                    }
                }
            } catch(SQLException|RuntimeException ex) { c.rollback(); throw ex; }
        }
    }
    Listing cancel(UUID owner, long id) throws SQLException {
        try(Connection c=connections.open()) {
            c.setAutoCommit(false);
            try(PreparedStatement q=prepare(c,"SELECT * FROM dune_exchange_listings WHERE id=? AND seller=? FOR UPDATE")) {
                q.setLong(1,id); q.setObject(2,owner);
                try(ResultSet r=q.executeQuery()) {
                    if(!r.next()) throw new IllegalArgumentException("This listing is unavailable or belongs to another player.");
                    Listing offer=read(r);
                    try(PreparedStatement d=prepare(c,"DELETE FROM dune_exchange_listings WHERE id=?")) {
                        d.setLong(1,id); d.executeUpdate(); c.commit(); return offer;
                    }
                }
            } catch(SQLException|RuntimeException ex) { c.rollback(); throw ex; }
        }
    }
}
