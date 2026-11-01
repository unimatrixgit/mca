package com.dunerealms.atelier;
import java.sql.*;import java.math.BigDecimal;import java.util.*;
/** Fixed sales chapters; rewards transfer existing treasury funds and never mint currency. */
public final class ArcStore {
 static final int[] TARGETS={3,5,10,15,25};
 static final long[] MINIMUM={10000,20000,40000,80000,160000},REWARDS={1000,2500,5000,10000,20000};
 static final String[] TITLES={"A Stitch in the Sand","Colours of the Caravan","The Caravan’s Trust","Patterns of Influence","A Legacy in Gold"};
 public record Chapter(int index,String title,String quality,int sales,int target,BigDecimal reward,boolean claimed,boolean unlocked){public boolean canClaim(){return !claimed&&unlocked&&sales>=target;}public String description(){return ArcLore.description(index);}public String templateName(){return ArcLore.templateName(index);}}
 public enum Claim {credited,already_claimed,pending_funding}
 static int claims(Connection c,UUID player)throws SQLException{int n=0;try(PreparedStatement q=c.prepareStatement("SELECT stage_index FROM dune_kit_arc_claims WHERE player_uuid=? ORDER BY stage_index")){q.setString(1,player.toString());try(ResultSet r=q.executeQuery()){while(r.next()){if(r.getInt(1)!=n||n>=5)throw new SQLException("Invalid claim sequence");n++;}}}return n;}
 static int sales(Connection c,UUID player,int index)throws SQLException{try(PreparedStatement q=c.prepareStatement("SELECT COUNT(*) FROM (SELECT buyer_uuid,FLOOR(delivered_at/86400000) AS utc_day FROM dune_kit_sales WHERE seller_uuid=? AND rarity=? AND buyer_uuid<>seller_uuid AND commerce_kind='personal_sale' AND delivered_at IS NOT NULL AND quantity=1 AND gross_cents>=? GROUP BY buyer_uuid,FLOOR(delivered_at/86400000)) AS qualifying")){q.setString(1,player.toString());q.setString(2,DeliveryStore.TIERS.get(index));q.setLong(3,MINIMUM[index]);try(ResultSet r=q.executeQuery()){r.next();return r.getInt(1);}}}
 public List<Chapter> read(Connection c,UUID player)throws SQLException{int done=claims(c,player);List<Chapter> result=new ArrayList<>();for(int i=0;i<5;i++)result.add(new Chapter(i,TITLES[i],DeliveryStore.TIERS.get(i),sales(c,player,i),TARGETS[i],BigDecimal.valueOf(REWARDS[i],2),i<done,i<=done));return result;}
 public Claim claim(Connection c,UUID player,int index)throws SQLException{
  if(index<0||index>=5)throw new SQLException("Invalid chapter");if(c.getAutoCommit())throw new SQLException("Explicit transaction required");
  ArcLore.description(index);
  BigDecimal treasury=null;try(PreparedStatement q=c.prepareStatement("SELECT balance FROM dune_staff_bank_wallet WHERE account_key='network' FOR UPDATE");ResultSet r=q.executeQuery()){if(r.next())treasury=r.getBigDecimal(1);}
  int done=claims(c,player);if(index<done)return Claim.already_claimed;if(index!=done||sales(c,player,index)<TARGETS[index])throw new SQLException("Complete the current sales chapter first");
  BigDecimal reward=BigDecimal.valueOf(REWARDS[index],2);if(treasury==null||treasury.compareTo(reward)<0)return Claim.pending_funding;
  BigDecimal wallet;try(PreparedStatement q=c.prepareStatement("SELECT balance FROM dune_economy_balances WHERE uuid=? FOR UPDATE")){q.setString(1,player.toString());try(ResultSet r=q.executeQuery()){if(!r.next())throw new SQLException("Existing Minecraft wallet required");wallet=r.getBigDecimal(1).add(reward);}}
  if(wallet.compareTo(new BigDecimal("1000000000000"))>0)throw new SQLException("Wallet limit reached");long now=System.currentTimeMillis();
  try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_kit_arc_claims(player_uuid,stage_index,reward_cents,claimed_at) VALUES(?,?,?,?)")){q.setString(1,player.toString());q.setInt(2,index);q.setLong(3,REWARDS[index]);q.setLong(4,now);q.executeUpdate();}
  try(PreparedStatement q=c.prepareStatement("UPDATE dune_staff_bank_wallet SET balance=?,updated_at=? WHERE account_key='network'")){q.setBigDecimal(1,treasury.subtract(reward));q.setLong(2,now);if(q.executeUpdate()!=1)throw new SQLException("Treasury changed");}
  try(PreparedStatement q=c.prepareStatement("UPDATE dune_economy_balances SET balance=? WHERE uuid=?")){q.setBigDecimal(1,wallet);q.setString(2,player.toString());if(q.executeUpdate()!=1)throw new SQLException("Wallet changed");}
  try(PreparedStatement q=c.prepareStatement("INSERT INTO dune_staff_bank_ledger(id,player_uuid,action,delta,balance_after,created_at,reference) VALUES(?,?,?,?,?,?,?)")){q.setString(1,UUID.randomUUID().toString());q.setString(2,player.toString());q.setString(3,"kit-arc-reward");q.setBigDecimal(4,reward.negate());q.setBigDecimal(5,treasury.subtract(reward));q.setLong(6,now);q.setString(7,"kit-arc:"+player+":"+index);q.executeUpdate();}
  return Claim.credited;
 }
}
