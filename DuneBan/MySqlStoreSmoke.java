package com.dunerealms.ban;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
/** Uses private connection-local TEMPORARY tables only; global guard cannot see test records. */
public final class MySqlStoreSmoke {
 static int assertions;
 static void check(boolean value,String text){assertions++;if(!value)throw new AssertionError(text);}
 public static void main(String[] args)throws Exception {
  Properties p=new Properties();try(InputStream in=Files.newInputStream(Path.of(args[0]))){p.load(in);}Class.forName("com.mysql.cj.jdbc.Driver");
  Properties auth=new Properties();auth.setProperty("user",p.getProperty("username"));auth.setProperty("password",p.getProperty("password"));auth.setProperty("connectTimeout","3000");auth.setProperty("socketTimeout","5000");auth.setProperty("connectionTimeZone","UTC");
  try(Connection raw=DriverManager.getConnection(p.getProperty("jdbc-url"),auth)){
   String schema=Files.readString(Path.of(args[1])).replace("CREATE TABLE IF NOT EXISTS","CREATE TEMPORARY TABLE");
   try(Statement s=raw.createStatement()){for(String sql:schema.split(";"))if(!sql.isBlank())s.execute(sql);}
   Connection borrowed=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(proxy,method,values)->{if(method.getName().equals("close"))return null;try{return method.invoke(raw,values);}catch(InvocationTargetException e){throw e.getCause();}});
   BanStore store=new BanStore(()->borrowed);UUID actor=UUID.fromString("00000000-0000-4000-8000-000000000001"),target=UUID.fromString("00000000-0000-4000-8000-000000000002");
   BanRecord initial=store.add(target,"Temporary Fixture",actor,"FixtureStaff","Private temporary SQL fixture",null,"arrakis");
   check(store.find(target).id().equals(initial.id()),"Inserted exact record must read back");
   check(store.online(List.of(target)).size()==1,"Active online lookup must enforce record");
   try{store.add(target,"Temporary Fixture",actor,"FixtureStaff","Duplicate temporary fixture",null,"kaitain");throw new AssertionError("Duplicate active ban allowed");}catch(SQLException expected){assertions++;}
   check(store.find(target).id().equals(initial.id()),"Duplicate must preserve original record");
   try{store.remove(target,initial.created()+1,"FixtureStaff","Stale snapshot fixture");throw new AssertionError("Stale removal allowed");}catch(SQLException expected){assertions++;}
   check(store.find(target)!=null,"Stale snapshot must keep current ban");
   check(store.remove(target,initial.created(),"FixtureStaff","Exact snapshot fixture"),"Exact removal must commit");check(store.find(target)==null,"Removed record must no longer enforce");
   check(!store.remove(target,initial.created(),"FixtureStaff","Duplicate removal fixture"),"Absent removal must report no match");
   BanRecord timed=store.add(target,"Temporary Fixture",actor,"FixtureStaff","Expiring temporary SQL fixture",1000L,"arrakis");
   try(PreparedStatement q=raw.prepareStatement("UPDATE dune_network_bans SET expires_at=1 WHERE id=?")){q.setString(1,timed.id().toString());q.executeUpdate();raw.commit();}
   check(store.find(target)==null,"Expired record must not enforce");
   BanRecord replacement=store.add(target,"Temporary Fixture",actor,"FixtureStaff","Replacement after expiry fixture",null,"giedi_prime");check(!replacement.id().equals(timed.id()),"Replacement needs distinct history record");
   check(store.active().size()==1,"Only current record enforces");
  }
  System.out.println("DuneBan MySQL store smoke PASS: "+assertions+" assertions; connection-local temporary tables only, no live player bans changed.");
 }
}
