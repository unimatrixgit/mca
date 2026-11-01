package com.dunerealms.bank;
import java.sql.*;
import java.math.*;
import java.util.*;
public final class BankStore {
 public record Access(boolean view,boolean deposit,boolean withdraw){}
 public record Entry(String id,String action,BigDecimal delta,BigDecimal balance,long time){}
 @FunctionalInterface interface AccessReader{Access read(Connection c,UUID player)throws SQLException;}
 private final AccessReader access;
 public BankStore(){this(BankStore::lockedAccess);} BankStore(AccessReader reader){access=reader;}
 public static void initialize(Connection c)throws SQLException{
  for(String sql:List.of(
   "CREATE TABLE IF NOT EXISTS dune_player_banks(player_uuid VARCHAR(36) PRIMARY KEY,balance DECIMAL(20,2) NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL,CHECK(balance>=0)) ENGINE=InnoDB",
   "CREATE TABLE IF NOT EXISTS dune_player_bank_ledger(id VARCHAR(36) PRIMARY KEY,player_uuid VARCHAR(36) NOT NULL,action VARCHAR(32) NOT NULL,delta DECIMAL(20,2) NOT NULL,balance_after DECIMAL(20,2) NOT NULL,created_at BIGINT NOT NULL,reference VARCHAR(100) NOT NULL UNIQUE) ENGINE=InnoDB",
   "CREATE TABLE IF NOT EXISTS dune_staff_bank_wallet(account_key VARCHAR(16) PRIMARY KEY,balance DECIMAL(20,2) NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL,CHECK(balance>=0),CHECK(account_key='network')) ENGINE=InnoDB",
   "CREATE TABLE IF NOT EXISTS dune_staff_bank_ledger(id VARCHAR(36) PRIMARY KEY,player_uuid VARCHAR(36) NOT NULL,action VARCHAR(32) NOT NULL,delta DECIMAL(20,2) NOT NULL,balance_after DECIMAL(20,2) NOT NULL,created_at BIGINT NOT NULL,reference VARCHAR(100) NOT NULL UNIQUE) ENGINE=InnoDB"
  ))try(Statement q=c.createStatement()){q.execute(sql);}
 }
 static void transaction(Connection c)throws SQLException{if(c.getAutoCommit())throw new SQLException("A fenced player transaction is required.");}
 public static BigDecimal amount(BigDecimal value)throws SQLException{if(value==null)throw new SQLException("Invalid Dunemarks amount.");try{value=value.setScale(2,RoundingMode.UNNECESSARY);}catch(ArithmeticException e){throw new SQLException("Use at most two decimal places.");}if(value.signum()<=0||value.compareTo(new BigDecimal("1000000.00"))>0)throw new SQLException("Use an amount between 0.01 and 1000000 Dunemarks.");return value;}
 static Access lockedAccess(Connection c,UUID player)throws SQLException{
  try(CallableStatement q=c.prepareCall("CALL dune_staff_bank_access_lock(?)")){q.setString(1,player.toString());boolean has=q.execute();while(!has&&q.getUpdateCount()!=-1)has=q.getMoreResults();if(!has)throw new SQLException("Staff bank access denied.");try(ResultSet r=q.getResultSet()){if(!r.next()||!player.toString().equals(r.getString("player_uuid")))throw new SQLException("Staff identity verification failed.");boolean view=flag(r,"can_view"),deposit=flag(r,"can_deposit"),withdraw=flag(r,"can_withdraw");if(r.next())throw new SQLException("Ambiguous staff identity.");return new Access(view,deposit,withdraw);}}
 }
 private static boolean flag(ResultSet r,String key)throws SQLException{int value=r.getInt(key);if(r.wasNull()||(value!=0&&value!=1))throw new SQLException("Invalid staff permissions.");return value==1;}
 public Access permissions(Connection c,UUID player)throws SQLException{return access.read(c,player);}
 public BigDecimal wallet(Connection c,UUID player)throws SQLException{try(PreparedStatement q=c.prepareStatement("SELECT balance FROM dune_economy_balances WHERE uuid=?")){q.setString(1,player.toString());try(ResultSet r=q.executeQuery()){return r.next()?r.getBigDecimal(1):new BigDecimal("0.00");}}}
 public BigDecimal balance(Connection c,UUID player,boolean staff,boolean lock)throws SQLException{
  String table=staff?"dune_staff_bank_wallet":"dune_player_banks",key=staff?"account_key":"player_uuid",id=staff?"network":player.toString();
  if(lock){transaction(c);try(PreparedStatement q=c.prepareStatement("INSERT INTO "+table+"("+key+",balance,updated_at) VALUES(?,0,?) ON DUPLICATE KEY UPDATE "+key+"="+key)){q.setString(1,id);q.setLong(2,System.currentTimeMillis());q.executeUpdate();}}
  try(PreparedStatement q=c.prepareStatement("SELECT balance FROM "+table+" WHERE "+key+"=?"+(lock?" FOR UPDATE":""))){q.setString(1,id);try(ResultSet r=q.executeQuery()){BigDecimal value=r.next()?r.getBigDecimal(1):new BigDecimal("0.00");if(value.signum()<0)throw new SQLException("Invalid bank balance.");return value;}}
 }
 public List<Entry> ledger(Connection c,UUID player,boolean staff)throws SQLException{
  String sql="SELECT id,action,delta,balance_after,created_at FROM "+(staff?"dune_staff_bank_ledger":"dune_player_bank_ledger WHERE player_uuid=?")+" ORDER BY created_at DESC,id DESC LIMIT 12";List<Entry> entries=new ArrayList<>();try(PreparedStatement q=c.prepareStatement(sql)){if(!staff)q.setString(1,player.toString());try(ResultSet r=q.executeQuery()){while(r.next())entries.add(new Entry(r.getString(1),r.getString(2),r.getBigDecimal(3),r.getBigDecimal(4),r.getLong(5)));}}return entries;
 }
 public record FactionView(String faction,BigDecimal balance,List<Entry> ledger){}
 public FactionView faction(Connection c,UUID player)throws SQLException{
  String faction;try(PreparedStatement q=c.prepareStatement("SELECT faction FROM dune_players WHERE uuid=?")){q.setString(1,player.toString());try(ResultSet r=q.executeQuery()){if(!r.next()||r.getString(1)==null)throw new SQLException("Choose your faction before viewing its treasury.");faction=r.getString(1).toLowerCase(Locale.ROOT);if(!Set.of("fremen","atreides","harkonnen","corrino","ordos").contains(faction)||r.next())throw new SQLException("Your faction could not be verified.");}}
  BigDecimal balance;try(PreparedStatement q=c.prepareStatement("SELECT balance FROM dune_faction_bank_wallet WHERE faction_id=?")){q.setString(1,faction);try(ResultSet r=q.executeQuery()){balance=r.next()?r.getBigDecimal(1):new BigDecimal("0.00");if(balance.signum()<0)throw new SQLException("Invalid faction bank balance.");}}
  List<Entry> entries=new ArrayList<>();try(PreparedStatement q=c.prepareStatement("SELECT id,action,delta,balance_after,created_at FROM dune_faction_bank_ledger WHERE faction_id=? ORDER BY created_at DESC,id DESC LIMIT 12")){q.setString(1,faction);try(ResultSet r=q.executeQuery()){while(r.next())entries.add(new Entry(r.getString(1),r.getString(2),r.getBigDecimal(3),r.getBigDecimal(4),r.getLong(5)));}}
  return new FactionView(faction,balance,entries);
 }
 public void transfer(Connection c,UUID player,boolean staff,boolean withdraw,BigDecimal requested,UUID reference)throws SQLException{
  transaction(c);BigDecimal value=amount(requested);if(staff){Access a=permissions(c,player);if(!a.view||!(withdraw?a.withdraw:a.deposit))throw new SQLException("Staff bank access denied.");}
  BigDecimal before=balance(c,player,staff,true),after=before.add(withdraw?value.negate():value);if(after.signum()<0||after.compareTo(new BigDecimal("1000000000000.00"))>0)throw new SQLException("Bank funds are insufficient or exceed the limit.");
  String ledger=staff?"dune_staff_bank_ledger":"dune_player_bank_ledger";
  try(PreparedStatement q=c.prepareStatement("SELECT id FROM "+ledger+" WHERE reference=?")){q.setString(1,reference.toString());try(ResultSet r=q.executeQuery()){if(r.next())throw new SQLException("This transfer was already recorded.");}}
  if(withdraw)try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_economy_balances(uuid,balance) VALUES(?,0) ON DUPLICATE KEY UPDATE uuid=uuid")){q.setString(1,player.toString());q.executeUpdate();}
  try(PreparedStatement q=c.prepareStatement("UPDATE dune_economy_balances SET balance=balance"+(withdraw?"+":"-")+"? WHERE uuid=?"+(withdraw?"":" AND balance>=?"))){q.setBigDecimal(1,value);q.setString(2,player.toString());if(!withdraw)q.setBigDecimal(3,value);if(q.executeUpdate()!=1)throw new SQLException("Not enough wallet Dunemarks or wallet unavailable.");}
  String table=staff?"dune_staff_bank_wallet":"dune_player_banks",key=staff?"account_key":"player_uuid";
  try(PreparedStatement q=c.prepareStatement("UPDATE "+table+" SET balance=?,updated_at=? WHERE "+key+"=? AND balance=?")){q.setBigDecimal(1,after);q.setLong(2,System.currentTimeMillis());q.setString(3,staff?"network":player.toString());q.setBigDecimal(4,before);if(q.executeUpdate()!=1)throw new SQLException("Bank changed. Refresh before retrying.");}
  try(PreparedStatement q=c.prepareStatement("INSERT INTO "+ledger+"(id,player_uuid,action,delta,balance_after,created_at,reference) VALUES(?,?,?,?,?,?,?)")){q.setString(1,UUID.randomUUID().toString());q.setString(2,player.toString());q.setString(3,withdraw?"withdraw":"deposit");q.setBigDecimal(4,withdraw?value.negate():value);q.setBigDecimal(5,after);q.setLong(6,System.currentTimeMillis());q.setString(7,reference.toString());q.executeUpdate();}
 }
}
