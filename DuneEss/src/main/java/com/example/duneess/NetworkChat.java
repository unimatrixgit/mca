package com.example.duneess;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import java.sql.*;
import java.util.*;

/** DuneEss channels. Private delivery is checked against current recipient permissions and identity. */
final class NetworkChat implements Listener,CommandExecutor,AutoCloseable {
    private final DuneEss plugin;
    private final RankBridge ranks;
    private final Map<UUID,String> channels=new HashMap<>();
    private final Map<UUID,Long> cooldown=new HashMap<>();
    private volatile boolean polling,closed;
    private boolean initialized;
    private long cursor;
    private final String server;
    private final MiniMessage colors=MiniMessage.builder().tags(TagResolver.builder().resolver(StandardTags.color()).resolver(StandardTags.gradient()).resolver(StandardTags.rainbow()).resolver(StandardTags.reset()).build()).build();
    private final MiniMessage fancy=MiniMessage.builder().tags(TagResolver.builder().resolver(StandardTags.color()).resolver(StandardTags.gradient()).resolver(StandardTags.rainbow()).resolver(StandardTags.decorations()).resolver(StandardTags.reset()).build()).build();
    record Message(String channel,String target,String sender,String prefix,String suffix,String body,boolean color,boolean format){}
    NetworkChat(DuneEss plugin,RankBridge ranks){
        this.plugin=plugin;this.ranks=ranks;server=plugin.getConfig().getString("chat.server-id","arrakis");
        Bukkit.getPluginManager().registerEvents(this,plugin);Objects.requireNonNull(plugin.getCommand("dchat")).setExecutor(this);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,this::poll,40L,20L);
    }
    static String canonical(String name){return switch(name.toLowerCase(Locale.ROOT)){case "local","proxy","global","network"->"network";case "near","nearby","proximity"->"nearby";case "house","rank","faction"->"faction";case "team"->"team";case "imperial"->"imperial";case "server"->"server";default->"";};}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player p))return true;
        if(args.length==0){p.sendMessage("/dchat local|proxy|nearby|team|faction|imperial|server [message] · Nearby is 50 blocks; Local and Proxy are the same channel.");return true;}
        String channel=canonical(args[0]);if(channel.isBlank()){p.sendMessage("Unknown chat channel.");return true;}
        String node=channel.equals("server")?"duneess.chat.network":"duneess.chat."+channel;
        if(!p.hasPermission(node)){p.sendMessage("Permission required: "+node);return true;}
        if(args.length>1)publish(p,channel,String.join(" ",Arrays.copyOfRange(args,1,args.length)));
        else{channels.put(p.getUniqueId(),channel);p.sendMessage(Component.text("Chat channel: "+channel,NamedTextColor.GOLD));}
        return true;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void chat(AsyncChatEvent event){
        event.setCancelled(true);String text=PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin,()->{Player p=event.getPlayer();if(p.isOnline())publish(p,channels.getOrDefault(p.getUniqueId(),"network"),text);});
    }
    private String team(Player p){var team=p.getScoreboard().getEntryTeam(p.getName());return team==null?"":team.getName();}
    private void publish(Player p,String channel,String body){
        if(body.isBlank()||body.length()>256){p.sendMessage("Messages must contain 1–256 characters.");return;}
        String permission="duneess.chat."+(channel.equals("server")?"network":channel);
        if(!p.hasPermission(permission)){p.sendMessage("Permission required: "+permission);return;}
        long now=System.currentTimeMillis();if(now-cooldown.getOrDefault(p.getUniqueId(),0L)<1000){p.sendMessage("Wait a moment before sending another message.");return;}
        cooldown.put(p.getUniqueId(),now);
        String target=channel.equals("faction")?ranks.house(p.getUniqueId()):channel.equals("team")?team(p):"";
        if((channel.equals("faction")||channel.equals("team"))&&target.isBlank()){p.sendMessage("Join a house or team before using this channel.");return;}
        Message message=new Message(channel,target,p.getName(),ranks.prefix(p.getUniqueId()),ranks.suffix(p.getUniqueId()),body,p.hasPermission("duneess.chat.colors"),p.hasPermission("duneess.chat.format"));
        if(channel.equals("nearby")||channel.equals("server")){
            for(Player viewer:Bukkit.getOnlinePlayers())if(channel.equals("server")||viewer.getWorld()==p.getWorld()&&viewer.getLocation().distanceSquared(p.getLocation())<=2500)deliver(viewer,message);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
            try(Connection c=plugin.connectEconomyDatabase();PreparedStatement q=SqlDialect.prepare(c,"INSERT INTO dune_chat_messages(server_id,channel,target,sender,prefix,suffix,body,colors,formatting,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)")){
                q.setString(1,server);q.setString(2,message.channel());q.setString(3,message.target());q.setString(4,message.sender());q.setString(5,message.prefix());q.setString(6,message.suffix());q.setString(7,message.body());q.setBoolean(8,message.color());q.setBoolean(9,message.format());q.setLong(10,now);q.executeUpdate();
            }catch(SQLException ex){Bukkit.getScheduler().runTask(plugin,()->{if(p.isOnline())p.sendMessage("Network chat is temporarily unavailable. Your message was not sent.");});}
        });
    }
    private void deliver(Player viewer,Message m){
        if(!viewer.hasPermission("duneess.chat."+(m.channel().equals("server")?"network":m.channel())))return;
        if(m.channel().equals("faction")&&!m.target().equals(ranks.house(viewer.getUniqueId())))return;
        if(m.channel().equals("team")&&!m.target().equals(team(viewer)))return;
        Component body=m.format()?fancy.deserialize(m.body()):m.color()?colors.deserialize(m.body()):Component.text(m.body());
        Component prefix=LegacyComponentSerializer.legacyAmpersand().deserialize(m.prefix()),suffix=LegacyComponentSerializer.legacyAmpersand().deserialize(m.suffix());
        viewer.sendMessage(Component.text("["+m.channel()+"] ",m.channel().equals("imperial")?NamedTextColor.GOLD:NamedTextColor.DARK_AQUA).append(prefix).append(Component.text(m.sender(),NamedTextColor.WHITE)).append(suffix).append(Component.text(" » ",NamedTextColor.GRAY)).append(body));
    }
    private void poll(){
        if(polling||closed)return;polling=true;
        try(Connection c=plugin.connectEconomyDatabase();Statement s=c.createStatement()){
            if(!initialized){s.executeUpdate(SqlDialect.sql(c,"CREATE TABLE IF NOT EXISTS dune_chat_messages(id BIGSERIAL PRIMARY KEY,server_id VARCHAR(48) NOT NULL,channel VARCHAR(16) NOT NULL,target VARCHAR(80) NOT NULL,sender VARCHAR(32) NOT NULL,prefix TEXT NOT NULL,suffix TEXT NOT NULL,body TEXT NOT NULL,colors BOOLEAN NOT NULL,formatting BOOLEAN NOT NULL,created_at BIGINT NOT NULL)"));try(ResultSet r=s.executeQuery("SELECT COALESCE(MAX(id),0) FROM dune_chat_messages")){r.next();cursor=r.getLong(1);}initialized=true;}
            List<Message> messages=new ArrayList<>();try(PreparedStatement q=SqlDialect.prepare(c,"SELECT * FROM dune_chat_messages WHERE id>? ORDER BY id LIMIT 100")){q.setLong(1,cursor);try(ResultSet rows=q.executeQuery()){while(rows.next()){cursor=rows.getLong("id");if(System.currentTimeMillis()-rows.getLong("created_at")<30000)messages.add(new Message(rows.getString("channel"),rows.getString("target"),rows.getString("sender"),rows.getString("prefix"),rows.getString("suffix"),rows.getString("body"),rows.getBoolean("colors"),rows.getBoolean("formatting")));}}}
            if(!messages.isEmpty()&&!closed)Bukkit.getScheduler().runTask(plugin,()->{for(Message message:messages)for(Player viewer:Bukkit.getOnlinePlayers())deliver(viewer,message);});
            if(Math.random()<.01)try(PreparedStatement q=SqlDialect.prepare(c,"DELETE FROM dune_chat_messages WHERE created_at<?")){q.setLong(1,System.currentTimeMillis()-86400000);q.executeUpdate();}
        }catch(SQLException ignored){}finally{polling=false;}
    }
    @EventHandler public void quit(PlayerQuitEvent event){channels.remove(event.getPlayer().getUniqueId());cooldown.remove(event.getPlayer().getUniqueId());}
    @Override public void close(){closed=true;}
}
