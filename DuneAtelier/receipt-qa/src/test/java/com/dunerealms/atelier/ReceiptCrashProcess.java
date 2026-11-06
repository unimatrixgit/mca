package com.dunerealms.atelier;
import java.nio.file.*;import java.sql.*;import java.util.*;import java.util.regex.*;
/** Test-only process. No production plugin startup or game configuration is loaded. */
public final class ReceiptCrashProcess {
 static Connection connect()throws Exception{
  String url=System.getenv("DUNE_RECEIPT_TEST_URL"),directory=System.getenv("DUNE_RECEIPT_TEST_DIR");
  if(url==null||directory==null)throw new IllegalStateException("Explicit isolated receipt database required");
  Matcher match=Pattern.compile("jdbc:mysql://127\\.0\\.0\\.1:(\\d{5})/receipt318qa").matcher(url);
  if(!match.matches()||Integer.parseInt(match.group(1))<49152)throw new IllegalStateException("Isolated high loopback port required");
  Path qa=Path.of(directory).toRealPath();if(!qa.toString().matches("/tmp/dune-receipt318-qa-[0-9]{8}T[0-9]{6}Z")||!Files.readString(qa.resolve(".codex-qa318")).strip().equals("isolated-receipt318"))throw new IllegalStateException("Private QA marker required");
  Properties properties=new Properties();properties.setProperty("user","root");properties.setProperty("password","");properties.setProperty("useSSL","false");properties.setProperty("allowPublicKeyRetrieval","true");properties.setProperty("connectTimeout","5000");properties.setProperty("socketTimeout","12000");
  Connection c=DriverManager.getConnection(url,properties);
  try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT @@datadir,@@port,@@bind_address,DATABASE()")){
   if(!r.next()||!Path.of(r.getString(1)).toRealPath().equals(qa.resolve("data").toRealPath())||r.getInt(2)!=Integer.parseInt(match.group(1))||!r.getString(3).equals("127.0.0.1")||!r.getString(4).equals("receipt318qa"))throw new IllegalStateException("Connection is not the dedicated isolated server");
  }catch(Exception e){c.close();throw e;}
  return c;
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=3||!List.of("before-commit","after-commit").contains(args[0]))throw new IllegalArgumentException("Crash fixture arguments required");
  Connection c=connect();c.setAutoCommit(false);UUID owner=UUID.fromString(args[1]),order=UUID.fromString(args[2]);DeliveryStore store=new DeliveryStore();store.lock(c,owner,order);
  try(Statement s=c.createStatement()){if(s.executeUpdate("UPDATE inventory_checkpoint SET pieces=pieces+4")!=1)throw new IllegalStateException("Fixture checkpoint missing");}
  store.delivered(c,owner,order,2);if(args[0].equals("after-commit"))c.commit();System.out.println("isolated-receipt-crash:"+args[0]);System.out.flush();Runtime.getRuntime().halt(97);
 }
}
