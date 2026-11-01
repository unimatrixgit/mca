package com.dunerealms.ban;
import com.google.inject.Inject;
import com.velocitypowered.api.event.*;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.*;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import java.io.InputStream;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Network-wide enforcement at the gateway; no world or rank access. */
public final class NetworkBanGuard {
 private final ProxyServer proxy;private final Logger logger;private final Path directory;
 private volatile BanStore store;private ScheduledTask task;private final AtomicBoolean polling=new AtomicBoolean();
 private static final Component UNAVAILABLE=Component.text("DuneRealms moderation verification is temporarily unavailable. Please reconnect shortly.");
 @Inject public NetworkBanGuard(ProxyServer proxy,Logger logger,@DataDirectory Path directory){this.proxy=proxy;this.logger=logger;this.directory=directory;}
 @Subscribe public void initialize(ProxyInitializeEvent event){
  try{Files.createDirectories(directory);Properties p=new Properties();try(InputStream input=Files.newInputStream(directory.resolve("database.properties"))){p.load(input);}store=new BanStore(p.getProperty("jdbc-url",""),p.getProperty("username",""),p.getProperty("password",""));logger.info("DuneBan network guard enabled; live MySQL verification required.");}catch(Exception e){logger.error("DuneBan private database.properties is unavailable. Network login remains fail-closed.");}
  task=proxy.getScheduler().buildTask(this,this::poll).repeat(Duration.ofSeconds(2)).schedule();
 }
 @Subscribe public void shutdown(ProxyShutdownEvent event){if(task!=null)task.cancel();}
 @Subscribe public EventTask login(LoginEvent event){return EventTask.async(()->{try{if(store==null)throw new IllegalStateException();BanRecord ban=store.find(event.getPlayer().getUniqueId());if(ban!=null)event.setResult(ResultedEvent.ComponentResult.denied(Component.text(ban.notice())));}catch(Exception e){event.setResult(ResultedEvent.ComponentResult.denied(UNAVAILABLE));}});}
 @Subscribe public EventTask transfer(ServerPreConnectEvent event){return EventTask.async(()->{try{if(store==null)throw new IllegalStateException();BanRecord ban=store.find(event.getPlayer().getUniqueId());if(ban!=null){event.setResult(ServerPreConnectEvent.ServerResult.denied());event.getPlayer().disconnect(Component.text(ban.notice()));}}catch(Exception e){event.setResult(ServerPreConnectEvent.ServerResult.denied());event.getPlayer().disconnect(UNAVAILABLE);}});}
 private void poll(){if(!polling.compareAndSet(false,true))return;try{List<Player> online=List.copyOf(proxy.getAllPlayers());if(online.isEmpty())return;try{if(store==null)throw new IllegalStateException();Map<UUID,BanRecord> records=new HashMap<>();for(BanRecord ban:store.online(online.stream().map(Player::getUniqueId).toList()))records.put(ban.player(),ban);for(Player player:online){BanRecord ban=records.get(player.getUniqueId());if(ban!=null&&player.isActive())player.disconnect(Component.text(ban.notice()));}}catch(Exception e){for(Player player:online)if(player.isActive())player.disconnect(UNAVAILABLE);}}finally{polling.set(false);}}
}

