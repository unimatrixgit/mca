package com.dunerealms.clans;
import java.sql.*;
import java.nio.file.*;
import java.util.*;

/** Operator CLI: creates required empty schema, then tests only connection-local temporary copies. */
public final class ClanBankProbe {
 public static void main(String[] args)throws Exception{
  Properties privateConfig=new Properties();try(var in=Files.newInputStream(Path.of(args[0]))){privateConfig.load(in);}
  Properties auth=new Properties();auth.setProperty("user",privateConfig.getProperty("username"));auth.setProperty("password",privateConfig.getProperty("password"));
  auth.setProperty("connectTimeout","3000");auth.setProperty("socketTimeout","5000");
  try(Connection c=DriverManager.getConnection(privateConfig.getProperty("jdbc-url"),auth)){
   ClanStore.initialize(c);ClanMoneyStore.initialize(c);ClanMoneyStore.initializeTreasury(c);
   for(String name:List.of("dune_clan_bank_balances","dune_clan_bank_ledger","dune_clan_operations","dune_clan_kit_orders","dune_clan_wallets","dune_clan_wallet_ledger","dune_staff_bank_wallet","dune_staff_bank_ledger","dune_faction_bank_wallet","dune_faction_bank_ledger","dune_clan_kit_fee_allocations")){
    String ddl;try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SHOW CREATE TABLE "+name)){r.next();ddl=r.getString(2);}
    try(Statement q=c.createStatement()){q.execute(ddl.replaceFirst("CREATE TABLE ","CREATE TEMPORARY TABLE "));}
   }
   try(Statement q=c.createStatement()){q.execute("CREATE TEMPORARY TABLE dune_economy_balances(uuid VARCHAR(36) PRIMARY KEY,balance DECIMAL(20,2) NOT NULL)");}
   var ctx=new ClanStore.Context("00000000-0000-0000-0009-000000000001","00000000-0000-4000-8000-000000000002","owner","Synthetic Clan","QA",1,"00000000-0000-4000-8000-000000000003",1,"Test Kit","#112233","[]",20);
   try(Statement q=c.createStatement()){q.execute("CREATE TEMPORARY TABLE dune_players(uuid VARCHAR(36) PRIMARY KEY,faction VARCHAR(64))");}try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_players VALUES(?,'Atreides')")){q.setString(1,ctx.uuid());q.executeUpdate();}
   ClanStore s=new ClanStore((connection,id)->ctx);c.setAutoCommit(false);UUID operation=UUID.randomUUID();s.operation(c,operation,ctx,"qa");
   s.change(c,ctx,Map.of(ClanStore.Grade.common,50L,ClanStore.Grade.rare,10L),"qa-deposit",operation.toString());
   if(!s.balances(c,ctx.clan(),false).get(ClanStore.Grade.common).equals(50L))throw new AssertionError("Deposit mismatch");
   s.change(c,ctx,Map.of(ClanStore.Grade.common,-20L),"qa-withdraw",operation.toString());
   var b=s.balances(c,ctx.clan(),false);if(b.get(ClanStore.Grade.common)!=30||b.get(ClanStore.Grade.rare)!=10)throw new AssertionError("Grade conservation");
   try{s.operation(c,operation,ctx,"qa-replay");throw new AssertionError("Replay accepted");}catch(SQLException expected){if(!"23000".equals(expected.getSQLState()))throw expected;}
   try{s.change(c,ctx,Map.of(ClanStore.Grade.common,-31L),"qa-overdraft","qa");throw new AssertionError("Overdraft accepted");}catch(SQLException expected){if(!expected.getMessage().contains("insufficient"))throw expected;}
   try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_economy_balances(uuid,balance) VALUES(?,250.00)")){q.setString(1,ctx.uuid());q.executeUpdate();}
   s.payDunemarks(c,ctx);new ClanMoneyStore().treasuryFee(c,ctx,operation);s.orderDunemarks(c,operation,ctx);
   try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT faction_tax,server_tax,crafting_sink FROM dune_clan_kit_fee_allocations")){r.next();if(r.getBigDecimal(1).intValue()!=3||r.getBigDecimal(2).intValue()!=1||r.getBigDecimal(3).intValue()!=16)throw new AssertionError("15/5/80 allocation mismatch");}
   if(s.wallet(c,UUID.fromString(ctx.uuid())).compareTo(new java.math.BigDecimal("230.00"))!=0)throw new AssertionError("Wallet debit mismatch");
   ClanMoneyStore bank=new ClanMoneyStore();bank.transfer(c,ctx,new java.math.BigDecimal("100.10"),false,UUID.randomUUID());
   if(bank.balance(c,ctx.clan(),false).compareTo(new java.math.BigDecimal("100.10"))!=0||s.wallet(c,UUID.fromString(ctx.uuid())).compareTo(new java.math.BigDecimal("129.90"))!=0)throw new AssertionError("Dunemarks deposit conservation");
   bank.transfer(c,ctx,new java.math.BigDecimal("20.05"),true,UUID.randomUUID());
   if(bank.balance(c,ctx.clan(),false).compareTo(new java.math.BigDecimal("80.05"))!=0||s.wallet(c,UUID.fromString(ctx.uuid())).compareTo(new java.math.BigDecimal("149.95"))!=0)throw new AssertionError("Dunemarks withdrawal conservation");
   try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT cost_spice,cost_dunemarks,payment_currency FROM dune_clan_kit_orders")){r.next();if(r.getLong(1)!=0||r.getBigDecimal(2).intValue()!=20||!"dunemarks".equals(r.getString(3)))throw new AssertionError("Kit currency mismatch");}
   try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT SUM(delta) FROM dune_clan_bank_ledger WHERE quality='common'")){r.next();if(r.getLong(1)!=30)throw new AssertionError("Ledger conservation");}
   c.rollback();if(s.balances(c,ctx.clan(),false).values().stream().anyMatch(n->n!=0)||!s.ledger(c,ctx.clan()).isEmpty())throw new AssertionError("Rollback retained QA rows");
   if(s.wallet(c,UUID.fromString(ctx.uuid())).signum()!=0)throw new AssertionError("Wallet rollback failed");
   if(bank.balance(c,ctx.clan(),false).signum()!=0||!bank.ledger(c,ctx.clan()).isEmpty())throw new AssertionError("Dunemarks bank rollback failed");
   try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT COUNT(*) FROM dune_clan_kit_fee_allocations")){r.next();if(r.getLong(1)!=0)throw new AssertionError("Fee rollback retained receipt");}
   System.out.println("PASS actual MySQL: 15pct actual-faction tax / 5pct server tax / exact crafting sink rollback; Dunemarks wallet purchase and clan bank transfers preserve cents, receipts and conservation; overdraft/replay rejected; full rollback. TEMPORARY tables only. No real funds or items changed.");
  }
 }
}
