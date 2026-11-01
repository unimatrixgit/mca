package com.dunerealms.clans;
import java.sql.*;import java.math.*;import java.util.*;
/** Allocates an already-debited kit price on the SAME fenced connection. */
public final class KitFeeStore{
 public record Split(BigDecimal total,BigDecimal faction,BigDecimal server,BigDecimal sink){}
 public static Split split(BigDecimal total)throws SQLException{total=ClanMoneyStore.amount(total);BigDecimal faction=total.multiply(new BigDecimal("0.15")).setScale(2,RoundingMode.DOWN),server=total.multiply(new BigDecimal("0.05")).setScale(2,RoundingMode.DOWN);return new Split(total,faction,server,total.subtract(faction).subtract(server));}
 public static void initialize(Connection c)throws SQLException{for(String sql:List.of(
  "CREATE TABLE IF NOT EXISTS dune_faction_bank_wallet(faction_id VARCHAR(32) PRIMARY KEY,balance DECIMAL(20,2) NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL,CHECK(balance>=0)) ENGINE=InnoDB",
  "CREATE TABLE IF NOT EXISTS dune_faction_bank_ledger(id VARCHAR(36) PRIMARY KEY,faction_id VARCHAR(32) NOT NULL,player_uuid VARCHAR(36) NOT NULL,action VARCHAR(32) NOT NULL,delta DECIMAL(20,2) NOT NULL,balance_after DECIMAL(20,2) NOT NULL,created_at BIGINT NOT NULL,reference VARCHAR(100) NOT NULL UNIQUE) ENGINE=InnoDB",
  "CREATE TABLE IF NOT EXISTS dune_clan_kit_fee_allocations(receipt_id VARCHAR(36) PRIMARY KEY,player_uuid VARCHAR(36) NOT NULL,faction_id VARCHAR(32) NOT NULL,cost_dunemarks DECIMAL(20,2) NOT NULL,faction_tax DECIMAL(20,2) NOT NULL,server_tax DECIMAL(20,2) NOT NULL,crafting_sink DECIMAL(20,2) NOT NULL,created_at BIGINT NOT NULL) ENGINE=InnoDB"
 ))try(Statement q=c.createStatement()){q.execute(sql);}}
 static String faction(Connection c,String player)throws SQLException{try(PreparedStatement q=c.prepareStatement("SELECT faction FROM dune_players WHERE uuid=? FOR UPDATE")){q.setString(1,player);try(ResultSet r=q.executeQuery()){if(!r.next())throw new SQLException("Choose your faction before buying a clan kit.");String value=r.getString(1);if(value==null)throw new SQLException("Choose your faction before buying a clan kit.");value=value.toLowerCase(Locale.ROOT);if(!Set.of("fremen","atreides","harkonnen","corrino","ordos").contains(value)||r.next())throw new SQLException("Your faction could not be verified. Reopen the menu.");return value;}}}
 public static void apply(Connection c,ClanStore.Context ctx,UUID receipt)throws SQLException{
  ClanStore.transaction(c);Split s=split(BigDecimal.valueOf(ctx.price()));String faction=faction(c,ctx.uuid());long now=System.currentTimeMillis();
  try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_clan_kit_fee_allocations(receipt_id,player_uuid,faction_id,cost_dunemarks,faction_tax,server_tax,crafting_sink,created_at) VALUES(?,?,?,?,?,?,?,?)")){q.setString(1,receipt.toString());q.setString(2,ctx.uuid());q.setString(3,faction);q.setBigDecimal(4,s.total);q.setBigDecimal(5,s.faction);q.setBigDecimal(6,s.server);q.setBigDecimal(7,s.sink);q.setLong(8,now);q.executeUpdate();}
  if(s.faction.signum()>0)credit(c,ctx,receipt,false,faction,s.faction,now);if(s.server.signum()>0)credit(c,ctx,receipt,true,"network",s.server,now);
 }
 private static void credit(Connection c,ClanStore.Context ctx,UUID receipt,boolean server,String keyValue,BigDecimal amount,long now)throws SQLException{
  String table=server?"dune_staff_bank_wallet":"dune_faction_bank_wallet",key=server?"account_key":"faction_id";
  try(PreparedStatement q=c.prepareStatement("INSERT INTO "+table+"("+key+",balance,updated_at) VALUES(?,0,?) ON DUPLICATE KEY UPDATE "+key+"="+key)){q.setString(1,keyValue);q.setLong(2,now);q.executeUpdate();}
  BigDecimal before;try(PreparedStatement q=c.prepareStatement("SELECT balance FROM "+table+" WHERE "+key+"=? FOR UPDATE")){q.setString(1,keyValue);try(ResultSet r=q.executeQuery()){if(!r.next())throw new SQLException("Fee treasury is unavailable.");before=r.getBigDecimal(1);}}
  BigDecimal after=before.add(amount);if(before.signum()<0||after.compareTo(new BigDecimal("1000000000000.00"))>0)throw new SQLException("Fee treasury limit reached.");
  try(PreparedStatement q=c.prepareStatement("UPDATE "+table+" SET balance=?,updated_at=? WHERE "+key+"=? AND balance=?")){q.setBigDecimal(1,after);q.setLong(2,now);q.setString(3,keyValue);q.setBigDecimal(4,before);if(q.executeUpdate()!=1)throw new SQLException("Fee treasury changed.");}
  String sql=server?"INSERT INTO dune_staff_bank_ledger(id,player_uuid,action,delta,balance_after,created_at,reference) VALUES(?,?,'clan-kit-fee',?,?,?,?)":"INSERT INTO dune_faction_bank_ledger(id,player_uuid,action,delta,balance_after,created_at,reference,faction_id) VALUES(?,?,'clan-kit-tax',?,?,?,?,?)";
  try(PreparedStatement q=c.prepareStatement(sql)){q.setString(1,UUID.randomUUID().toString());q.setString(2,ctx.uuid());q.setBigDecimal(3,amount);q.setBigDecimal(4,after);q.setLong(5,now);q.setString(6,"clan-kit:"+receipt);if(!server)q.setString(7,keyValue);q.executeUpdate();}
 }
}
