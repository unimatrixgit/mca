package com.dunerealms.staff;
import java.sql.*;
import java.util.*;
import java.math.*;

/** SQL authority is locked inside the caller's target inventory checkpoint. Never commits itself. */
public final class CaseStore {
 public static final List<String> FACTIONS=List.of("Fremen","Atreides","Harkonnen","Corrino","Ordos");
 public record Grant(long ticket,long version,UUID assigned,UUID creator,UUID suspect,UUID target,boolean teleport,boolean inspect,boolean edit,boolean economy,boolean faction){public Grant(long ticket,long version,UUID assigned,UUID creator,UUID suspect,UUID target,boolean teleport,boolean inspect,boolean edit,boolean economy){this(ticket,version,assigned,creator,suspect,target,teleport,inspect,edit,economy,false);}}
 public enum Action {read,teleport_creator,teleport_suspect,inspect_inventory,edit_inventory,adjust_economy,edit_faction}
 @FunctionalInterface interface Authority {Grant lock(Connection c,UUID actor,long ticket,long version,Action action)throws SQLException;}
 @FunctionalInterface interface BankAuthority {boolean allowed(Connection c,UUID actor,boolean credit)throws SQLException;}
 private final Authority authority;private final BankAuthority bankAuthority;
 public CaseStore(){this(CaseStore::lockedGrant,CaseStore::lockedBank);}CaseStore(Authority authority,BankAuthority bank){this.authority=authority;this.bankAuthority=bank;}
 public static void initialize(Connection c)throws SQLException{
  try(Statement q=c.createStatement()){q.execute("CREATE TABLE IF NOT EXISTS dune_staff_case_audit(id VARCHAR(36) PRIMARY KEY,ticket_id BIGINT NOT NULL,context_version BIGINT NOT NULL,actor_uuid VARCHAR(36) NOT NULL,target_uuid VARCHAR(36) NOT NULL,action VARCHAR(32) NOT NULL,slot_index INTEGER,item_bytes LONGBLOB,wallet_delta DECIMAL(20,2),wallet_after DECIMAL(20,2),treasury_after DECIMAL(20,2),details VARCHAR(100),created_at BIGINT NOT NULL) ENGINE=InnoDB");}
  ensureDetails(c);
 }
 private static void ensureDetails(Connection c)throws SQLException{
  try(ResultSet r=c.getMetaData().getColumns(c.getCatalog(),null,"dune_staff_case_audit","details")){if(r.next())return;}
  try(Statement q=c.createStatement()){q.execute("ALTER TABLE dune_staff_case_audit ADD COLUMN details VARCHAR(100)");}catch(SQLException e){if(e.getErrorCode()!=1060&&!"42S21".equals(e.getSQLState()))throw e;}
 }
 static void transaction(Connection c)throws SQLException{if(c.getAutoCommit())throw new SQLException("Fenced target inventory transaction required");}
 static UUID identity(String raw)throws SQLException{try{UUID id=UUID.fromString(raw);if(!id.toString().equals(raw)||id.getMostSignificantBits()==0&&id.getLeastSignificantBits()==0)throw new IllegalArgumentException();return id;}catch(RuntimeException e){throw new SQLException("Verified canonical identity required");}}
 static UUID nullable(String raw)throws SQLException{return raw==null?null:identity(raw);}
 static boolean flag(ResultSet r,String column)throws SQLException{int value=r.getInt(column);if(r.wasNull()||(value!=0&&value!=1))throw new SQLException("Invalid case authority flags");return value==1;}
 static Grant lockedGrant(Connection c,UUID actor,long ticket,long version,Action action)throws SQLException{
  transaction(c);if(ticket<=0||version<0)throw new SQLException("Invalid case reference");
  try(CallableStatement q=c.prepareCall("CALL dune_case_action_lock(?,?,?,?)")){
   q.setString(1,actor.toString());q.setLong(2,ticket);q.setLong(3,version);q.setString(4,action.name());boolean has=q.execute();while(!has&&q.getUpdateCount()!=-1)has=q.getMoreResults();if(!has)throw new SQLException("Case authority unavailable");
   try(ResultSet r=q.getResultSet()){
    if(!r.next()||!actor.toString().equals(r.getString("actor_uuid")))throw new SQLException("Case is no longer authorised");
    Grant g=new Grant(r.getLong("ticket_id"),r.getLong("context_version"),nullable(r.getString("assigned_agent_uuid")),nullable(r.getString("creator_uuid")),nullable(r.getString("suspect_uuid")),nullable(r.getString("target_uuid")),flag(r,"can_teleport"),flag(r,"can_inspect_inventory"),flag(r,"can_edit_inventory"),flag(r,"can_adjust_economy"),flag(r,"can_edit_faction"));
    if(r.next()||g.ticket!=ticket||g.version!=version)throw new SQLException("Stale or ambiguous case context");return g;
   }
  }
 }
 public Grant authorise(Connection c,UUID actor,long ticket,long version,Action action,UUID target)throws SQLException{
  transaction(c);Grant grant=authority.lock(c,actor,ticket,version,action);
  if(grant.ticket!=ticket||grant.version!=version||!actor.equals(grant.assigned)||target==null||!target.equals(grant.target))throw new SQLException("Case assignment or target changed");
  boolean allowed=switch(action){case teleport_creator,teleport_suspect->grant.teleport;case inspect_inventory->grant.inspect;case edit_inventory->grant.edit;case adjust_economy->grant.economy;case edit_faction->grant.faction;default->false;};
  if(!allowed)throw new SQLException("Case action denied");
  UUID expected=action==Action.teleport_creator?grant.creator:grant.suspect;
  if(!target.equals(expected))throw new SQLException("Case target does not match the chosen role");return grant;
 }
 static boolean lockedBank(Connection c,UUID actor,boolean credit)throws SQLException{
  try(CallableStatement q=c.prepareCall("CALL dune_staff_bank_access_lock(?)")){q.setString(1,actor.toString());boolean has=q.execute();while(!has&&q.getUpdateCount()!=-1)has=q.getMoreResults();if(!has)return false;try(ResultSet r=q.getResultSet()){if(!r.next()||!actor.toString().equals(r.getString("player_uuid")))return false;boolean allowed=flag(r,"can_view")&&flag(r,credit?"can_withdraw":"can_deposit");return !r.next()&&allowed;}}
 }
 public void audit(Connection c,UUID request,UUID actor,long ticket,long version,UUID target,Action action,Integer slot,byte[] items,BigDecimal delta,BigDecimal wallet,BigDecimal bank)throws SQLException{
  transaction(c);if(items!=null&&(items.length==0||items.length>1024*1024))throw new SQLException("Item evidence outside safe size bounds");
  try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_staff_case_audit(id,ticket_id,context_version,actor_uuid,target_uuid,action,slot_index,item_bytes,wallet_delta,wallet_after,treasury_after,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")){
   q.setString(1,request.toString());q.setLong(2,ticket);q.setLong(3,version);q.setString(4,actor.toString());q.setString(5,target.toString());q.setString(6,action.name());if(slot==null)q.setNull(7,Types.INTEGER);else q.setInt(7,slot);q.setBytes(8,items);q.setBigDecimal(9,delta);q.setBigDecimal(10,wallet);q.setBigDecimal(11,bank);q.setLong(12,System.currentTimeMillis());q.executeUpdate();
  }
 }
 public void confiscation(Connection c,UUID request,UUID actor,long ticket,long version,UUID target,int slot,byte[] bytes)throws SQLException{
  if(bytes==null||slot<0||slot>40||actor.equals(target))throw new SQLException("Invalid confiscation target");
  authorise(c,actor,ticket,version,Action.edit_inventory,target);audit(c,request,actor,ticket,version,target,Action.edit_inventory,slot,bytes,null,null,null);
 }
 static BigDecimal money(BigDecimal value)throws SQLException{try{if(value==null||value.signum()<=0||value.compareTo(new BigDecimal("1000000.00"))>0)throw new ArithmeticException();return value.setScale(2,RoundingMode.UNNECESSARY);}catch(ArithmeticException e){throw new SQLException("Positive amount up to1000000 with at most2 decimals required");}}
 public void factionChange(Connection c,UUID request,UUID actor,long ticket,long version,UUID target,String faction)throws SQLException{
  if(actor.equals(target)||!FACTIONS.contains(faction))throw new SQLException("Canonical non-self faction target required");
  authorise(c,actor,ticket,version,Action.edit_faction,target);audit(c,request,actor,ticket,version,target,Action.edit_faction,null,null,null,null,null);
  try(PreparedStatement q=c.prepareStatement("UPDATE dune_staff_case_audit SET details=? WHERE id=? AND action='edit_faction'")){q.setString(1,"Faction assignment: "+faction);q.setString(2,request.toString());if(q.executeUpdate()!=1)throw new SQLException("Faction audit unavailable");}
 }
 public record Balances(BigDecimal wallet,BigDecimal treasury){}
 public Balances balances(Connection c,UUID actor,long ticket,long version,UUID target,boolean credit)throws SQLException{
  authorise(c,actor,ticket,version,Action.adjust_economy,target);if(!bankAuthority.allowed(c,actor,credit))throw new SQLException("Current bank authority required");
  BigDecimal bank,wallet;
  try(PreparedStatement q=c.prepareStatement("SELECT balance FROM dune_staff_bank_wallet WHERE account_key='network' FOR UPDATE");ResultSet r=q.executeQuery()){if(!r.next())throw new SQLException("Treasury unavailable");bank=r.getBigDecimal(1);}
  try(PreparedStatement q=c.prepareStatement("SELECT balance FROM dune_economy_balances WHERE uuid=? FOR UPDATE")){q.setString(1,target.toString());try(ResultSet r=q.executeQuery()){if(!r.next())throw new SQLException("Wallet unavailable");wallet=r.getBigDecimal(1);}}
  return new Balances(wallet,bank);
 }
 public void correction(Connection c,UUID request,UUID actor,long ticket,long version,UUID target,BigDecimal value,boolean credit)throws SQLException{
  transaction(c);if(actor.equals(target))throw new SQLException("Self-correction forbidden");value=money(value);authorise(c,actor,ticket,version,Action.adjust_economy,target);
  if(!bankAuthority.allowed(c,actor,credit))throw new SQLException("Current bank authority required");
  BigDecimal bank,wallet;
  try(PreparedStatement q=c.prepareStatement("SELECT balance FROM dune_staff_bank_wallet WHERE account_key='network' FOR UPDATE");ResultSet r=q.executeQuery()){if(!r.next())throw new SQLException("Staff treasury unavailable");bank=r.getBigDecimal(1);if(r.next())throw new SQLException("Ambiguous treasury");}
  try(PreparedStatement q=c.prepareStatement("SELECT balance FROM dune_economy_balances WHERE uuid=? FOR UPDATE")){q.setString(1,target.toString());try(ResultSet r=q.executeQuery()){if(!r.next())throw new SQLException("Target wallet unavailable");wallet=r.getBigDecimal(1);}}
  BigDecimal delta=credit?value:value.negate(),afterWallet=wallet.add(delta),afterBank=bank.subtract(delta);
  if(bank.signum()<0||afterBank.signum()<0||!credit&&afterWallet.signum()<0||afterBank.compareTo(new BigDecimal("1000000000000.00"))>0||afterWallet.abs().compareTo(new BigDecimal("1000000000000.00"))>0)throw new SQLException("Insufficient funds or correction bound exceeded");
  // Receipt insert precedes both balance writes. Duplicate confirmation fails before any second debit.
  audit(c,request,actor,ticket,version,target,Action.adjust_economy,null,null,delta,afterWallet,afterBank);
  try(PreparedStatement q=c.prepareStatement("UPDATE dune_staff_bank_wallet SET balance=?,updated_at=? WHERE account_key='network' AND balance=?")){q.setBigDecimal(1,afterBank);q.setLong(2,System.currentTimeMillis());q.setBigDecimal(3,bank);if(q.executeUpdate()!=1)throw new SQLException("Treasury changed");}
  try(PreparedStatement q=c.prepareStatement("UPDATE dune_economy_balances SET balance=? WHERE uuid=? AND balance=?")){q.setBigDecimal(1,afterWallet);q.setString(2,target.toString());q.setBigDecimal(3,wallet);if(q.executeUpdate()!=1)throw new SQLException("Wallet changed");}
  try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_staff_bank_ledger(id,player_uuid,action,delta,balance_after,created_at,reference) VALUES(?,?,?,?,?,?,?)")){q.setString(1,UUID.randomUUID().toString());q.setString(2,actor.toString());q.setString(3,credit?"case_credit":"case_debit");q.setBigDecimal(4,delta.negate());q.setBigDecimal(5,afterBank);q.setLong(6,System.currentTimeMillis());q.setString(7,"case:"+request);q.executeUpdate();}
 }
}



