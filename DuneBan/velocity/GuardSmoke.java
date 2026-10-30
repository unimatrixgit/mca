package com.dunerealms.ban;
import com.velocitypowered.api.event.*;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.server.*;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
/** Isolated real Velocity event API smoke tests. Does not connect to a database or player. */
public class GuardSmoke {
 static final UUID ID=UUID.fromString("00000000-0000-4000-8000-000000000002");
 static int assertions;
 static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
 @SuppressWarnings("unchecked") static <T>T fake(Class<T> type,InvocationHandler handler){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},handler);}
 static Object defaultValue(Class<?> type){if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;return null;}
 static Connection connection(boolean banned){return fake(Connection.class,(p,m,a)->{if(m.getName().equals("prepareStatement"))return fake(PreparedStatement.class,(p2,m2,a2)->{if(m2.getName().equals("executeQuery")){AtomicInteger position=new AtomicInteger();return fake(ResultSet.class,(p3,m3,a3)->switch(m3.getName()){
  case "next"->banned&&position.getAndIncrement()==0;case "wasNull"->true;
  case "getLong"->a3[0].equals("created_at")?1793232000000L:0L;
  case "getString"->switch((String)a3[0]){case "id","player_uuid","actor_uuid"->ID.toString();case "player_name"->"Demo";case "actor_name"->"Moderator";case "reason"->"Reviewed test reason";case "source"->"arrakis";default->null;};
  default->defaultValue(m3.getReturnType());});}return defaultValue(m2.getReturnType());});return defaultValue(m.getReturnType());});}
 static void execute(EventTask task){check(task.requiresAsync(),"JDBC must use awaiting async task");AtomicInteger completed=new AtomicInteger();task.execute(new Continuation(){public void resume(){completed.incrementAndGet();}public void resumeWithException(Throwable t){throw new AssertionError(t);}});check(completed.get()==1,"Event continuation must resume exactly once");}
 static void setStore(NetworkBanGuard guard,BanStore store)throws Exception{Field f=NetworkBanGuard.class.getDeclaredField("store");f.setAccessible(true);f.set(guard,store);}
 public static void main(String[] args)throws Exception {
  AtomicInteger kicks=new AtomicInteger();Player player=fake(Player.class,(p,m,a)->switch(m.getName()){case "getUniqueId"->ID;case "getUsername"->"Demo";case "getCurrentServer"->Optional.empty();case "isActive"->true;case "disconnect"->{kicks.incrementAndGet();yield null;}default->defaultValue(m.getReturnType());});
  ProxyServer proxy=fake(ProxyServer.class,(p,m,a)->m.getName().equals("getAllPlayers")?List.of(player):defaultValue(m.getReturnType()));
  RegisteredServer server=fake(RegisteredServer.class,(p,m,a)->m.getName().equals("getServerInfo")?new ServerInfo("arrakis",new InetSocketAddress("127.0.0.1",25566)):defaultValue(m.getReturnType()));
  NetworkBanGuard guard=new NetworkBanGuard(proxy,null,Path.of("unused-private-directory"));
  LoginEvent missing=new LoginEvent(player);execute(guard.login(missing));check(!missing.getResult().isAllowed(),"Missing configuration must deny login");
  setStore(guard,new BanStore(()->connection(false)));LoginEvent clean=new LoginEvent(player);execute(guard.login(clean));check(clean.getResult().isAllowed(),"Verified clean account may log in");
  LoginEvent priorDenied=new LoginEvent(player);priorDenied.setResult(ResultedEvent.ComponentResult.denied(net.kyori.adventure.text.Component.text("other guard")));execute(guard.login(priorDenied));check(!priorDenied.getResult().isAllowed(),"Never undo another plugin's rejection");
  setStore(guard,new BanStore(()->connection(true)));LoginEvent banned=new LoginEvent(player);execute(guard.login(banned));check(!banned.getResult().isAllowed(),"Global ban must deny login");
  ServerPreConnectEvent transfer=new ServerPreConnectEvent(player,server);execute(guard.transfer(transfer));check(!transfer.getResult().isAllowed(),"Global ban must block transfer");check(kicks.get()==1,"Global ban must disconnect active transfer player");
  Method poll=NetworkBanGuard.class.getDeclaredMethod("poll");poll.setAccessible(true);poll.invoke(guard);check(kicks.get()==2,"Bounded live check must kick a newly banned active player");
  setStore(guard,new BanStore(()->{throw new SQLException("private password must not escape");}));LoginEvent outage=new LoginEvent(player);execute(guard.login(outage));check(!outage.getResult().isAllowed(),"DB outage must fail closed");
  ServerPreConnectEvent outageTransfer=new ServerPreConnectEvent(player,server);execute(guard.transfer(outageTransfer));check(!outageTransfer.getResult().isAllowed(),"Outage must block transfer");poll.invoke(guard);check(kicks.get()==4,"Outage disconnects use service-unavailable result, not a fabricated ban record");
  System.out.println("DuneBan Velocity smoke PASS: "+assertions+" assertions, no external data or real players touched.");
 }
}

