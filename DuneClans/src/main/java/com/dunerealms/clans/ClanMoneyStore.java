package com.dunerealms.clans;
import java.sql.*;
import java.math.*;
import java.util.*;

/** Dunemarks transfers use the same connection as the authoritative player checkpoint. */
public final class ClanMoneyStore {
 public record Entry(String id,String action,BigDecimal delta,BigDecimal balance,long time){}
 public static void initialize(Connection c)throws SQLException{
  for(String sql:List.of(
   "CREATE TABLE IF NOT EXISTS dune_clan_wallets(clan_id VARCHAR(36) PRIMARY KEY,balance DECIMAL(20,2) NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL,CHECK(balance>=0)) ENGINE=InnoDB",
   "CREATE TABLE IF NOT EXISTS dune_clan_wallet_ledger(id VARCHAR(36) PRIMARY KEY,clan_id VARCHAR(36) NOT NULL,player_uuid VARCHAR(36) NOT NULL,action VARCHAR(32) NOT NULL,delta DECIMAL(20,2) NOT NULL,balance_after DECIMAL(20,2) NOT NULL,created_at BIGINT NOT NULL,reference VARCHAR(100) NOT NULL,INDEX dune_clan_wallet_recent(clan_id,created_at)) ENGINE=InnoDB"
  ))try(Statement q=c.createStatement()){q.execute(sql);}
 }
 public static void initializeTreasury(Connection c)throws SQLException{
  for(String sql:List.of(
   "CREATE TABLE IF NOT EXISTS dune_staff_bank_wallet(account_key VARCHAR(16) PRIMARY KEY,balance DECIMAL(20,2) NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL,CHECK(balance>=0),CHECK(account_key='network')) ENGINE=InnoDB",
   "CREATE TABLE IF NOT EXISTS dune_staff_bank_ledger(id VARCHAR(36) PRIMARY KEY,player_uuid VARCHAR(36) NOT NULL,action VARCHAR(32) NOT NULL,delta DECIMAL(20,2) NOT NULL,balance_after DECIMAL(20,2) NOT NULL,created_at BIGINT NOT NULL,reference VARCHAR(100) NOT NULL UNIQUE) ENGINE=InnoDB"
  ))try(Statement q=c.createStatement()){q.execute(sql);}
  KitFeeStore.initialize(c);
 }
 public void treasuryFee(Connection c,ClanStore.Context ctx,UUID receipt)throws SQLException{KitFeeStore.apply(c,ctx,receipt);}
 public static BigDecimal amount(BigDecimal value)throws SQLException{
  try{value=value.setScale(2,RoundingMode.UNNECESSARY);}catch(ArithmeticException e){throw new SQLException("Use at most two Dunemarks decimal places.");}
  if(value.signum()<=0||value.compareTo(new BigDecimal("1000000.00"))>0)throw new SQLException("Use a Dunemarks amount between 0.01 and 1000000.");return value;
 }
 public BigDecimal balance(Connection c,String clan,boolean lock)throws SQLException{
  if(lock){ClanStore.transaction(c);try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_clan_wallets(clan_id,balance,updated_at) VALUES(?,0,?) ON DUPLICATE KEY UPDATE clan_id=clan_id")){q.setString(1,clan);q.setLong(2,System.currentTimeMillis());q.executeUpdate();}}
  try(PreparedStatement q=c.prepareStatement("SELECT balance FROM dune_clan_wallets WHERE clan_id=?"+(lock?" FOR UPDATE":""))){q.setString(1,clan);try(ResultSet r=q.executeQuery()){BigDecimal n=r.next()?r.getBigDecimal(1):new BigDecimal("0.00");if(n.signum()<0)throw new SQLException("Invalid clan balance.");return n;}}
 }
 public List<Entry> ledger(Connection c,String clan)throws SQLException{
  List<Entry> entries=new ArrayList<>();try(PreparedStatement q=c.prepareStatement("SELECT id,action,delta,balance_after,created_at FROM dune_clan_wallet_ledger WHERE clan_id=? ORDER BY created_at DESC,id DESC LIMIT 12")){q.setString(1,clan);try(ResultSet r=q.executeQuery()){while(r.next())entries.add(new Entry(r.getString(1),r.getString(2),r.getBigDecimal(3),r.getBigDecimal(4),r.getLong(5)));}}return entries;
 }
 private void change(Connection c,ClanStore.Context ctx,BigDecimal before,BigDecimal delta,String action,UUID ref)throws SQLException{
  BigDecimal after=before.add(delta);if(after.signum()<0||after.compareTo(new BigDecimal("1000000000000.00"))>0)throw new SQLException("The clan bank has insufficient Dunemarks or exceeds its limit.");
  try(PreparedStatement q=c.prepareStatement("UPDATE dune_clan_wallets SET balance=?,updated_at=? WHERE clan_id=? AND balance=?")){q.setBigDecimal(1,after);q.setLong(2,System.currentTimeMillis());q.setString(3,ctx.clan());q.setBigDecimal(4,before);if(q.executeUpdate()!=1)throw new SQLException("The bank changed. Refresh before retrying.");}
  try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_clan_wallet_ledger(id,clan_id,player_uuid,action,delta,balance_after,created_at,reference) VALUES(?,?,?,?,?,?,?,?)")){q.setString(1,UUID.randomUUID().toString());q.setString(2,ctx.clan());q.setString(3,ctx.uuid());q.setString(4,action);q.setBigDecimal(5,delta);q.setBigDecimal(6,after);q.setLong(7,System.currentTimeMillis());q.setString(8,ref.toString());q.executeUpdate();}
 }
 public void transfer(Connection c,ClanStore.Context ctx,BigDecimal requested,boolean withdraw,UUID ref)throws SQLException{
  ClanStore.transaction(c);BigDecimal value=amount(requested);if(withdraw&&!ctx.officer())throw new SQLException("Only clan owners and officers may withdraw bank Dunemarks.");
  BigDecimal before=balance(c,ctx.clan(),true);
  if(withdraw){
   if(before.compareTo(value)<0)throw new SQLException("The clan bank has insufficient Dunemarks.");
   try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_economy_balances(uuid,balance) VALUES(?,0) ON DUPLICATE KEY UPDATE uuid=uuid")){q.setString(1,ctx.uuid());q.executeUpdate();}
   try(PreparedStatement q=c.prepareStatement("UPDATE dune_economy_balances SET balance=balance+? WHERE uuid=?")){q.setBigDecimal(1,value);q.setString(2,ctx.uuid());if(q.executeUpdate()!=1)throw new SQLException("Wallet credit failed.");}
   change(c,ctx,before,value.negate(),"withdraw",ref);
  }else{
   try(PreparedStatement q=c.prepareStatement("UPDATE dune_economy_balances SET balance=balance-? WHERE uuid=? AND balance>=?")){q.setBigDecimal(1,value);q.setString(2,ctx.uuid());q.setBigDecimal(3,value);if(q.executeUpdate()!=1)throw new SQLException("Not enough Dunemarks in your wallet.");}
   change(c,ctx,before,value,"deposit",ref);
  }
 }
 public void purchase(Connection c,ClanStore.Context ctx,UUID ref)throws SQLException{
  ClanStore.transaction(c);if(!ctx.officer())throw new SQLException("Only clan owners and officers may spend bank Dunemarks.");
  BigDecimal value=amount(BigDecimal.valueOf(ctx.price()));BigDecimal before=balance(c,ctx.clan(),true);change(c,ctx,before,value.negate(),"kit-purchase",ref);
 }
}
