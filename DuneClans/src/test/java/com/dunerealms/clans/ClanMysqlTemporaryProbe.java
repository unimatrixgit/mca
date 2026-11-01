package com.dunerealms.clans;
import java.sql.*;
import java.lang.reflect.*;
import java.util.*;
/** Uses connection-local TEMPORARY tables only; never writes persistent clan/player rows. */
public final class ClanMysqlTemporaryProbe {
 static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
 static long count(Connection c,String table)throws Exception{try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();return r.getLong(1);}}
 static Object unwrap(InvocationTargetException e)throws Throwable{throw e.getCause();}
 public static void main(String[] args)throws Exception{
  String url=System.getenv("MYSQL_TEST_URL"),user=System.getenv("MYSQL_TEST_USER"),pw=System.getenv("MYSQL_TEST_PASSWORD");
  if(url==null||!url.startsWith("jdbc:mysql:")||user==null||pw==null)throw new IllegalArgumentException("Private MYSQL_TEST environment required");
  Properties props=new Properties();props.setProperty("user",user);props.setProperty("password",pw);props.setProperty("connectTimeout","3000");props.setProperty("socketTimeout","5000");
  Class.forName("com.mysql.cj.jdbc.Driver");
  try(Connection c=DriverManager.getConnection(url,props)){
   Connection ddl=(Connection)Proxy.newProxyInstance(ClanMysqlTemporaryProbe.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,values)->{
    try{
     Object v=method.invoke(c,values);
     if(method.getName().equals("createStatement")){Statement s=(Statement)v;return Proxy.newProxyInstance(ClanMysqlTemporaryProbe.class.getClassLoader(),new Class<?>[]{Statement.class},(p,m,a)->{
      try{if(m.getName().equals("execute")&&a!=null&&a.length>0&&a[0] instanceof String sql){check(sql.startsWith("CREATE TABLE IF NOT EXISTS dune_clan_"),"Only known clan DDL allowed");a[0]=sql.replaceFirst("CREATE TABLE","CREATE TEMPORARY TABLE");}return m.invoke(s,a);}catch(InvocationTargetException e){return unwrap(e);}
     });}return v;
    }catch(InvocationTargetException e){return unwrap(e);}
   });
   ClanStore.initialize(ddl);ClanStore.initialize(ddl);
   var ctx=new ClanStore.Context("00000000-0000-0000-0009-000000000001","temporary-probe-clan","owner","Temporary QA","QA",1,"temporary-blueprint",2,"QA Kit","#123456","[]",25);
   var store=new ClanStore((db,uuid)->ctx);c.setAutoCommit(false);
   store.change(c,ctx,Map.of(ClanStore.Grade.common,100L,ClanStore.Grade.rare,8L),"deposit","temporary-seed");c.commit();
   UUID id=UUID.randomUUID();store.operation(c,id,ctx,"kit");store.change(c,ctx,Map.of(ClanStore.Grade.common,-25L),"kit","temporary-order");store.order(c,id,ctx,"clan-bank",Map.of(ClanStore.Grade.common,25L));
   check(store.balances(c,ctx.clan(),false).get(ClanStore.Grade.common)==75,"debit");check(count(c,"dune_clan_kit_orders")==1,"receipt staged");c.rollback();
   check(store.balances(c,ctx.clan(),false).get(ClanStore.Grade.common)==100,"balance rollback");check(count(c,"dune_clan_kit_orders")==0,"receipt rollback");check(count(c,"dune_clan_operations")==0,"operation rollback");check(count(c,"dune_clan_bank_ledger")==2,"ledger rollback");
   store.operation(c,id,ctx,"withdraw");store.change(c,ctx,Map.of(ClanStore.Grade.common,-40L),"withdraw","temporary-final");c.commit();
   try{store.operation(c,id,ctx,"withdraw");throw new AssertionError("replay accepted");}catch(SQLException expected){c.rollback();}
   try{store.change(c,ctx,Map.of(ClanStore.Grade.common,-61L),"withdraw","temporary-overdraft");throw new AssertionError("overdraft accepted");}catch(SQLException expected){c.rollback();}
   check(store.balances(c,ctx.clan(),false).get(ClanStore.Grade.common)==60,"final conserved balance");check(store.balances(c,ctx.clan(),false).get(ClanStore.Grade.rare)==8,"grade preserved");
   System.out.println("PASS actual MySQL: TEMPORARY-only DDL, grade conservation, atomic receipt/operation/ledger/balance rollback, replay and overdraft rejection. No persistent rows changed.");
  }
 }
}
