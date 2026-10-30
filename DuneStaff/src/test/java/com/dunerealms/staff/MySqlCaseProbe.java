package com.dunerealms.staff;
import com.google.gson.*;
import java.nio.file.*;import java.sql.*;import java.util.*;import java.math.BigDecimal;
/** TEMP-only storage probe. Never calls production authority or moves a real player's item/funds. */
public final class MySqlCaseProbe {
 public static void main(String[] args)throws Exception{
  if(args.length!=1)throw new IllegalArgumentException("Private root configuration path required");
  JsonObject config=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
  Properties auth=new Properties();auth.setProperty("user",config.get(config.has("username")?"username":"user").getAsString());auth.setProperty("password",config.get("password").getAsString());auth.setProperty("allowPublicKeyRetrieval","true");auth.setProperty("connectTimeout","3000");auth.setProperty("socketTimeout","5000");Class.forName("com.mysql.cj.jdbc.Driver");
  UUID actor=UUID.fromString("10000000-0000-0000-0000-000000000001"),target=UUID.fromString("00000000-0000-0000-0009-000000000002");
  try(Connection c=DriverManager.getConnection("jdbc:mysql://127.0.0.1:13307/dunegameplay",auth)){
   try(Statement q=c.createStatement()){
    q.execute("CREATE TEMPORARY TABLE dune_staff_case_audit(id VARCHAR(36) PRIMARY KEY,ticket_id BIGINT NOT NULL,context_version BIGINT NOT NULL,actor_uuid VARCHAR(36) NOT NULL,target_uuid VARCHAR(36) NOT NULL,action VARCHAR(32) NOT NULL,slot_index INTEGER,item_bytes LONGBLOB,wallet_delta DECIMAL(20,2),wallet_after DECIMAL(20,2),treasury_after DECIMAL(20,2),created_at BIGINT NOT NULL) ENGINE=InnoDB");
    q.execute("CREATE TEMPORARY TABLE dune_economy_balances(uuid VARCHAR(36) PRIMARY KEY,balance DECIMAL(20,2) NOT NULL) ENGINE=InnoDB");q.execute("CREATE TEMPORARY TABLE dune_staff_bank_wallet(account_key VARCHAR(16) PRIMARY KEY,balance DECIMAL(20,2),updated_at BIGINT) ENGINE=InnoDB");q.execute("CREATE TEMPORARY TABLE dune_staff_bank_ledger(id VARCHAR(36) PRIMARY KEY,player_uuid VARCHAR(36),action VARCHAR(32),delta DECIMAL(20,2),balance_after DECIMAL(20,2),created_at BIGINT,reference VARCHAR(100) UNIQUE) ENGINE=InnoDB");q.execute("INSERT INTO dune_staff_bank_wallet VALUES('network',500,0)");
   }
   try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_economy_balances VALUES(?,100)")){q.setString(1,target.toString());q.executeUpdate();}
   CaseStore store=new CaseStore((con,a,t,v,x)->new CaseStore.Grant(t,v,actor,null,target,target,true,true,true,true),(con,a,credit)->true);c.setAutoCommit(false);
   UUID receipt=UUID.randomUUID();store.correction(c,receipt,actor,229211,3,target,new BigDecimal("10.25"),false);c.commit();amounts(c,"89.75","510.25");
   try{store.correction(c,receipt,actor,229211,3,target,new BigDecimal("10.25"),false);throw new AssertionError("Duplicate accepted");}catch(SQLException expected){c.rollback();}amounts(c,"89.75","510.25");
   store.correction(c,UUID.randomUUID(),actor,229211,3,target,new BigDecimal("20.05"),true);c.rollback();amounts(c,"89.75","510.25");
   try{store.correction(c,UUID.randomUUID(),actor,229211,3,target,new BigDecimal("900"),true);throw new AssertionError("Unfunded credit accepted");}catch(SQLException expected){c.rollback();}amounts(c,"89.75","510.25");
   byte[] evidence={0,1,2,(byte)255};UUID item=UUID.randomUUID();store.confiscation(c,item,actor,229211,3,target,40,evidence);try(PreparedStatement q=c.prepareStatement("SELECT item_bytes FROM dune_staff_case_audit WHERE id=?")){q.setString(1,item.toString());try(ResultSet r=q.executeQuery()){if(!r.next()||!Arrays.equals(evidence,r.getBytes(1)))throw new AssertionError("Item evidence changed");}}c.rollback();
   try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT COUNT(*) FROM dune_staff_case_audit")){r.next();if(r.getInt(1)!=1)throw new AssertionError("Rollback evidence leaked");}
   System.out.println("PASS: actual MySQL TEMP-only correction conservation, duplicate receipt, rollback, insufficient treasury, exact item BLOB and rollback. No real authority calls, inventory actions or balances modified.");
  }
 }
 static void amounts(Connection c,String wallet,String treasury)throws Exception{
  try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT balance FROM dune_economy_balances")){r.next();if(r.getBigDecimal(1).compareTo(new BigDecimal(wallet))!=0)throw new AssertionError("Wallet conservation");}
  try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT balance FROM dune_staff_bank_wallet")){r.next();if(r.getBigDecimal(1).compareTo(new BigDecimal(treasury))!=0)throw new AssertionError("Treasury conservation");}
 }
}
