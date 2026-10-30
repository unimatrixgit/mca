package com.dunerealms.ban;
import java.sql.*;
import java.util.*;
import java.util.function.Supplier;
public final class BanStore {
 @FunctionalInterface public interface Connections {Connection open()throws SQLException;}
 private final Connections connections;
 public BanStore(String url,String username,String password){if(url==null||!url.startsWith("jdbc:mysql://"))throw new IllegalArgumentException("Private MySQL configuration is required.");connections=()->{try{Class.forName("com.mysql.cj.jdbc.Driver");}catch(ClassNotFoundException e){throw new SQLException("Driver unavailable");}Properties p=new Properties();p.setProperty("user",username);p.setProperty("password",password);p.setProperty("connectTimeout","3000");p.setProperty("socketTimeout","5000");p.setProperty("connectionTimeZone","UTC");return DriverManager.getConnection(url,p);};}
 public BanStore(Connections connections){this.connections=connections;}
 public void initialize()throws SQLException {try(Connection c=connections.open();Statement s=c.createStatement()){
  s.execute("CREATE TABLE IF NOT EXISTS dune_network_bans(id VARCHAR(36) PRIMARY KEY,player_uuid VARCHAR(36) NOT NULL,player_name VARCHAR(40) NOT NULL,actor_uuid VARCHAR(36) NOT NULL,actor_name VARCHAR(40) NOT NULL,reason VARCHAR(500) NOT NULL,created_at BIGINT NOT NULL,expires_at BIGINT,source VARCHAR(32) NOT NULL,active BOOLEAN NOT NULL DEFAULT TRUE,revoked_at BIGINT,revoked_by TEXT,revoked_reason TEXT,active_player_uuid VARCHAR(36) GENERATED ALWAYS AS (IF(active,player_uuid,NULL)) STORED,UNIQUE KEY dune_network_bans_one_active(active_player_uuid)) ENGINE=InnoDB");
  s.execute("CREATE TABLE IF NOT EXISTS dune_network_ban_locks(player_uuid VARCHAR(36) PRIMARY KEY) ENGINE=InnoDB");
 }}
 private static long now(Connection c)throws SQLException {try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS SIGNED)")){if(!r.next())throw new SQLException("Clock unavailable");return r.getLong(1);}}
 private static void lock(Connection c,UUID id)throws SQLException {try(PreparedStatement s=c.prepareStatement("INSERT INTO dune_network_ban_locks(player_uuid) VALUES(?) ON DUPLICATE KEY UPDATE player_uuid=VALUES(player_uuid)")){s.setString(1,id.toString());s.executeUpdate();}try(PreparedStatement s=c.prepareStatement("SELECT player_uuid FROM dune_network_ban_locks WHERE player_uuid=? FOR UPDATE")){s.setString(1,id.toString());try(ResultSet r=s.executeQuery()){if(!r.next())throw new SQLException("Player lock unavailable");}}}
 public BanRecord add(UUID player,String name,UUID actor,String actorName,String reason,Long duration,String source)throws SQLException {
  reason=BanRules.reason(reason);if(player.equals(actor)||name==null||name.length()>40||actorName==null||actorName.length()>40||source==null||!source.matches("[a-z0-9_-]{1,32}"))throw new IllegalArgumentException("Invalid ban identity.");
  try(Connection c=connections.open()){c.setAutoCommit(false);try{lock(c,player);long now=now(c);
   try(PreparedStatement q=c.prepareStatement("UPDATE dune_network_bans SET active=FALSE WHERE player_uuid=? AND active AND expires_at IS NOT NULL AND expires_at<=?")){q.setString(1,player.toString());q.setLong(2,now);q.executeUpdate();}
   BanRecord record=new BanRecord(UUID.randomUUID(),player,name,actor,actorName,reason,now,duration==null?null:Math.addExact(now,duration),source);
   try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_network_bans(id,player_uuid,player_name,actor_uuid,actor_name,reason,created_at,expires_at,source) VALUES(?,?,?,?,?,?,?,?,?)")){
    q.setString(1,record.id().toString());q.setString(2,player.toString());q.setString(3,name);q.setString(4,actor.toString());q.setString(5,actorName);q.setString(6,reason);q.setLong(7,now);if(record.expires()==null)q.setNull(8,Types.BIGINT);else q.setLong(8,record.expires());q.setString(9,source);if(q.executeUpdate()!=1)throw new SQLException("An active network ban already exists.");}
   c.commit();return record;
  }catch(Exception e){c.rollback();if(e instanceof SQLException sql)throw sql;throw e;}}
 }
 public boolean remove(UUID player,long expectedCreated,String actor,String reason)throws SQLException {
  BanRules.reason(reason);try(Connection c=connections.open()){c.setAutoCommit(false);try{lock(c,player);
   try(PreparedStatement q=c.prepareStatement("SELECT created_at FROM dune_network_bans WHERE player_uuid=? AND active FOR UPDATE")){q.setString(1,player.toString());try(ResultSet r=q.executeQuery()){if(!r.next()){c.rollback();return false;}if(r.getLong(1)!=expectedCreated)throw new SQLException("The network ban changed; refresh first.");}}
   try(PreparedStatement q=c.prepareStatement("UPDATE dune_network_bans SET active=FALSE,revoked_at=?,revoked_by=?,revoked_reason=? WHERE player_uuid=? AND active AND created_at=?")){q.setLong(1,now(c));q.setString(2,actor);q.setString(3,reason);q.setString(4,player.toString());q.setLong(5,expectedCreated);if(q.executeUpdate()!=1)throw new SQLException("The network ban changed.");}
   c.commit();return true;
  }catch(Exception e){c.rollback();if(e instanceof SQLException sql)throw sql;throw e;}}
 }
 private static BanRecord record(ResultSet r)throws SQLException {long expires=r.getLong("expires_at");Long expiry=r.wasNull()?null:expires;return new BanRecord(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("player_uuid")),r.getString("player_name"),UUID.fromString(r.getString("actor_uuid")),r.getString("actor_name"),r.getString("reason"),r.getLong("created_at"),expiry,r.getString("source"));}
 public List<BanRecord> active()throws SQLException {try(Connection c=connections.open();PreparedStatement q=c.prepareStatement("SELECT * FROM dune_network_bans WHERE active AND (expires_at IS NULL OR expires_at>CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS SIGNED)) ORDER BY created_at DESC LIMIT 5000");ResultSet r=q.executeQuery()){List<BanRecord> records=new ArrayList<>();while(r.next())records.add(record(r));return records;}}
 public BanRecord find(UUID player)throws SQLException {try(Connection c=connections.open();PreparedStatement q=c.prepareStatement("SELECT * FROM dune_network_bans WHERE player_uuid=? AND active AND (expires_at IS NULL OR expires_at>CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS SIGNED))")){q.setString(1,player.toString());try(ResultSet r=q.executeQuery()){return r.next()?record(r):null;}}}
 public List<BanRecord> online(Collection<UUID> players)throws SQLException {if(players.size()>5000)throw new IllegalArgumentException("Too many players");if(players.isEmpty())return List.of();String placeholders=String.join(",",Collections.nCopies(players.size(),"?"));try(Connection c=connections.open();PreparedStatement q=c.prepareStatement("SELECT * FROM dune_network_bans WHERE player_uuid IN ("+placeholders+") AND active AND (expires_at IS NULL OR expires_at>CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS SIGNED))")){int index=1;for(UUID id:players)q.setString(index++,id.toString());try(ResultSet r=q.executeQuery()){List<BanRecord> records=new ArrayList<>();while(r.next())records.add(record(r));return records;}}}
}


