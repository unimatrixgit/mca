package com.dunerealms.clans;

import java.sql.*;
import java.math.BigDecimal;
import java.util.*;

/** Same-connection bank ledger; caller owns the fenced inventory transaction. */
public final class ClanStore {
 public enum Grade {common,uncommon,rare,very_rare,legendary;
  public String label(){return switch(this){case common->"Common";case uncommon->"Uncommon";case rare->"Rare";case very_rare->"Very Rare";case legendary->"Legendary";};}
 }
 public record Context(String uuid,String clan,String role,String name,String tag,long clanVersion,
                       String blueprint,long blueprintVersion,String kitName,String color,String lore,long price){
  public boolean officer(){return role.equals("owner")||role.equals("officer");}
  public void sameClan(String expected)throws SQLException{if(!clan.equals(expected))throw new SQLException("Your clan membership changed. Reopen the Clan Shop.");}
  public void kitMatches(Context expected)throws SQLException{
   sameClan(expected.clan);if(blueprint==null||!blueprint.equals(expected.blueprint)||blueprintVersion!=expected.blueprintVersion||price!=expected.price)
    throw new SQLException("The kit changed. Review its current design and price.");
   if(price<1||price>1000000)throw new SQLException("Kit price is outside the allowed bounds.");
  }
 }
 public record Entry(String action,String quality,long delta,long balance,long time){}
 @FunctionalInterface public interface ContextReader {Context read(Connection c,UUID player)throws SQLException;}
 private final ContextReader reader;
 public ClanStore(){this(ClanStore::lockedContext);}
 ClanStore(ContextReader reader){this.reader=reader;}
 public static void initialize(Connection c)throws SQLException{
  for(String sql:List.of(
   "CREATE TABLE IF NOT EXISTS dune_clan_bank_balances(clan_id VARCHAR(36) NOT NULL,quality VARCHAR(16) NOT NULL,balance BIGINT NOT NULL DEFAULT 0,PRIMARY KEY(clan_id,quality),CHECK(balance>=0)) ENGINE=InnoDB",
   "CREATE TABLE IF NOT EXISTS dune_clan_bank_ledger(id VARCHAR(36) PRIMARY KEY,clan_id VARCHAR(36) NOT NULL,player_uuid VARCHAR(36) NOT NULL,action VARCHAR(32) NOT NULL,quality VARCHAR(16) NOT NULL,delta BIGINT NOT NULL,balance_after BIGINT NOT NULL,created_at BIGINT NOT NULL,reference VARCHAR(100) NOT NULL,INDEX dune_clan_ledger_recent(clan_id,created_at)) ENGINE=InnoDB",
   "CREATE TABLE IF NOT EXISTS dune_clan_operations(id VARCHAR(36) PRIMARY KEY,clan_id VARCHAR(36) NOT NULL,player_uuid VARCHAR(36) NOT NULL,action VARCHAR(32) NOT NULL,created_at BIGINT NOT NULL) ENGINE=InnoDB",
   "CREATE TABLE IF NOT EXISTS dune_clan_kit_orders(id VARCHAR(36) PRIMARY KEY,clan_id VARCHAR(36) NOT NULL,player_uuid VARCHAR(36) NOT NULL,blueprint_id VARCHAR(36) NOT NULL,blueprint_version BIGINT NOT NULL,cost_spice BIGINT NOT NULL,funding VARCHAR(16) NOT NULL,grades_json TEXT NOT NULL,created_at BIGINT NOT NULL) ENGINE=InnoDB"
  ))try(Statement q=c.createStatement()){q.execute(sql);}
  for(var column:Map.of("cost_dunemarks","DECIMAL(20,2) NOT NULL DEFAULT 0","payment_currency","VARCHAR(16) NOT NULL DEFAULT 'spice'").entrySet()){
   try(ResultSet found=c.getMetaData().getColumns(c.getCatalog(),null,"dune_clan_kit_orders",column.getKey())){
    if(!found.next())try(Statement q=c.createStatement()){q.execute("ALTER TABLE dune_clan_kit_orders ADD COLUMN "+column.getKey()+" "+column.getValue());}
    catch(SQLException race){
     if(race.getErrorCode()!=1060&&!"42S21".equals(race.getSQLState()))throw race;
     try(ResultSet after=c.getMetaData().getColumns(c.getCatalog(),null,"dune_clan_kit_orders",column.getKey())){if(!after.next())throw race;}
    }
   }
  }
 }
 public Context context(Connection c,UUID player)throws SQLException{return reader.read(c,player);}
 static Context lockedContext(Connection c,UUID player)throws SQLException{
  try(CallableStatement q=c.prepareCall("CALL dune_clan_context_lock(?)")){
   q.setString(1,player.toString());boolean result=q.execute();
   while(!result&&q.getUpdateCount()!=-1)result=q.getMoreResults();
   if(!result)throw new SQLException("Link your Minecraft account and join a clan on the Clan Center first.");
   try(ResultSet r=q.getResultSet()){
    if(!r.next())throw new SQLException("Link your Minecraft account and join a clan on the Clan Center first.");
    String id=r.getString("minecraft_uuid");if(!player.toString().equals(id))throw new SQLException("Clan identity verification failed.");
    Context ctx=new Context(id,r.getString("clan_id"),r.getString("clan_role"),r.getString("clan_name"),r.getString("clan_tag"),r.getLong("clan_version"),r.getString("blueprint_id"),r.getLong("blueprint_version"),r.getString("kit_name"),r.getString("kit_color"),r.getString("kit_lore_json"),r.getLong("price_dunemarks"));
    if(r.next()||!Set.of("owner","officer","member").contains(ctx.role))throw new SQLException("Invalid clan membership response.");
    return ctx;
   }
  }
 }
 public EnumMap<Grade,Long> balances(Connection c,String clan,boolean lock)throws SQLException{
  if(lock){transaction(c);for(Grade g:Grade.values())try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_clan_bank_balances(clan_id,quality,balance) VALUES(?,?,0) ON DUPLICATE KEY UPDATE balance=balance")){q.setString(1,clan);q.setString(2,g.name());q.executeUpdate();}}
  EnumMap<Grade,Long> result=new EnumMap<>(Grade.class);for(Grade g:Grade.values())result.put(g,0L);
  try(PreparedStatement q=c.prepareStatement("SELECT quality,balance FROM dune_clan_bank_balances WHERE clan_id=? ORDER BY quality"+(lock?" FOR UPDATE":""))){q.setString(1,clan);try(ResultSet r=q.executeQuery()){while(r.next()){long n=r.getLong(2);if(n<0)throw new SQLException("Invalid bank balance.");result.put(Grade.valueOf(r.getString(1)),n);}}}
  return result;
 }
 public List<Entry> ledger(Connection c,String clan)throws SQLException{
  List<Entry> result=new ArrayList<>();try(PreparedStatement q=c.prepareStatement("SELECT action,quality,delta,balance_after,created_at FROM dune_clan_bank_ledger WHERE clan_id=? ORDER BY created_at DESC,id DESC LIMIT 12")){q.setString(1,clan);try(ResultSet r=q.executeQuery()){while(r.next())result.add(new Entry(r.getString(1),r.getString(2),r.getLong(3),r.getLong(4),r.getLong(5)));}}return result;
 }
 static void transaction(Connection c)throws SQLException{if(c.getAutoCommit())throw new SQLException("A fenced inventory transaction is required.");}
 public void operation(Connection c,UUID id,Context ctx,String action)throws SQLException{
  transaction(c);try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_clan_operations(id,clan_id,player_uuid,action,created_at) VALUES(?,?,?,?,?)")){q.setString(1,id.toString());q.setString(2,ctx.clan);q.setString(3,ctx.uuid);q.setString(4,action);q.setLong(5,System.currentTimeMillis());q.executeUpdate();}
 }
 public void change(Connection c,Context ctx,Map<Grade,Long> amounts,String action,String reference)throws SQLException{
  transaction(c);EnumMap<Grade,Long> before=balances(c,ctx.clan,true);
  for(Grade g:Grade.values()){
   long delta=amounts.getOrDefault(g,0L);if(delta==0)continue;long after;
   try{after=Math.addExact(before.get(g),delta);}catch(ArithmeticException overflow){throw new SQLException("Bank balance limit exceeded.");}
   if(after<0||after>1000000000000L)throw new SQLException("The bank balance changed or is insufficient. Review it again.");
   try(PreparedStatement q=c.prepareStatement("UPDATE dune_clan_bank_balances SET balance=? WHERE clan_id=? AND quality=? AND balance=?")){q.setLong(1,after);q.setString(2,ctx.clan);q.setString(3,g.name());q.setLong(4,before.get(g));if(q.executeUpdate()!=1)throw new SQLException("Bank balance changed.");}
   try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_clan_bank_ledger(id,clan_id,player_uuid,action,quality,delta,balance_after,created_at,reference) VALUES(?,?,?,?,?,?,?,?,?)")){q.setString(1,UUID.randomUUID().toString());q.setString(2,ctx.clan);q.setString(3,ctx.uuid);q.setString(4,action);q.setString(5,g.name());q.setLong(6,delta);q.setLong(7,after);q.setLong(8,System.currentTimeMillis());q.setString(9,reference);q.executeUpdate();}
  }
 }
 public static EnumMap<Grade,Long> allocate(Map<Grade,Long> balances,long amount)throws SQLException{
  if(amount<1||amount>1000000)throw new SQLException("Invalid spice quantity.");
  EnumMap<Grade,Long> result=new EnumMap<>(Grade.class);long left=amount;
  for(Grade g:Grade.values()){long n=Math.min(left,balances.getOrDefault(g,0L));if(n>0){result.put(g,n);left-=n;}}
  if(left!=0)throw new SQLException("Not enough spice.");return result;
 }
 public static EnumMap<Grade,Long> negative(Map<Grade,Long> amounts){EnumMap<Grade,Long> r=new EnumMap<>(Grade.class);amounts.forEach((g,n)->r.put(g,-n));return r;}
 public void order(Connection c,UUID id,Context ctx,String funding,Map<Grade,Long> grades)throws SQLException{
  transaction(c);String json="{"+String.join(",",grades.entrySet().stream().map(e->"\""+e.getKey().name()+"\":"+e.getValue()).toList())+"}";
  try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_clan_kit_orders(id,clan_id,player_uuid,blueprint_id,blueprint_version,cost_spice,funding,grades_json,created_at) VALUES(?,?,?,?,?,?,?,?,?)")){q.setString(1,id.toString());q.setString(2,ctx.clan);q.setString(3,ctx.uuid);q.setString(4,ctx.blueprint);q.setLong(5,ctx.blueprintVersion);q.setLong(6,ctx.price);q.setString(7,funding);q.setString(8,json);q.setLong(9,System.currentTimeMillis());q.executeUpdate();}
 }
 public BigDecimal wallet(Connection c,UUID player)throws SQLException{
  try(PreparedStatement q=c.prepareStatement("SELECT balance FROM dune_economy_balances WHERE uuid=?")){q.setString(1,player.toString());try(ResultSet r=q.executeQuery()){return r.next()?r.getBigDecimal(1):BigDecimal.ZERO;}}
 }
 public void payDunemarks(Connection c,Context ctx)throws SQLException{
  transaction(c);if(ctx.price<1||ctx.price>1000000)throw new SQLException("The kit price is invalid.");
  BigDecimal price=BigDecimal.valueOf(ctx.price);
  try(PreparedStatement q=c.prepareStatement("UPDATE dune_economy_balances SET balance=balance-? WHERE uuid=? AND balance>=?")){
   q.setBigDecimal(1,price);q.setString(2,ctx.uuid);q.setBigDecimal(3,price);
   if(q.executeUpdate()!=1)throw new SQLException("Not enough Dunemarks in your wallet.");
  }
 }
 public void orderDunemarks(Connection c,UUID id,Context ctx)throws SQLException{
  orderDunemarks(c,id,ctx,"wallet");
 }
 public void orderDunemarks(Connection c,UUID id,Context ctx,String funding)throws SQLException{
  transaction(c);
  if(!Set.of("wallet","clan-bank").contains(funding))throw new SQLException("Invalid payment source.");
  try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_clan_kit_orders(id,clan_id,player_uuid,blueprint_id,blueprint_version,cost_spice,cost_dunemarks,payment_currency,funding,grades_json,created_at) VALUES(?,?,?,?,?,0,?,'dunemarks',?,'{}',?)")){
   q.setString(1,id.toString());q.setString(2,ctx.clan);q.setString(3,ctx.uuid);q.setString(4,ctx.blueprint);q.setLong(5,ctx.blueprintVersion);q.setBigDecimal(6,BigDecimal.valueOf(ctx.price));q.setString(7,funding);q.setLong(8,System.currentTimeMillis());q.executeUpdate();
  }
 }
}
