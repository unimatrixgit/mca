package com.example.duneess;

import com.google.gson.*;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.WeightNode;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** DuneEss owns all managed LuckPerms writes; Core only supplies factual house/office state. */
final class RankBridge {
    private final DuneEss plugin;
    private final LuckPerms api;
    private final Map<UUID,Set<String>> identities=new ConcurrentHashMap<>();
    private final Map<UUID,String> houses=new ConcurrentHashMap<>();
    private final Map<UUID,Set<String>> contracts=new ConcurrentHashMap<>();
    private final Set<UUID> dirty=ConcurrentHashMap.newKeySet();
    
    
    private volatile boolean busy;
    

    RankBridge(DuneEss plugin){
        this.plugin=plugin;
        var registration=Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        api=registration==null?null:registration.getProvider();
        if(api==null){plugin.getLogger().warning("LuckPerms is absent; managed rank synchronisation is unavailable.");return;}
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,this::sync,60L,200L);
    }
    static String houseKey(String house){return switch(house.toLowerCase(Locale.ROOT)){
        case "atreides","aerdrecht"->"aerdrecht";case "harkonnen","harkveen"->"harkveen";
        case "corrino","korenholt"->"korenholt";case "ordos","oordwijk"->"oordwijk";
        case "fremen","vrijduin"->"vrijduin";case "guild","spacing_guild","vaerkring"->"vaerkring";default->"";};}
    void identity(UUID uuid,String house,Set<String> offices){
        String canonical=houseKey(house);houses.put(uuid,canonical);
        Set<String> groups=new HashSet<>();if(!canonical.isBlank())groups.add("dr_house_"+canonical);
        for(String office:offices)if(Set.of("emperor","house_leader","specerhuis","welvaart","guild_staff","landsraad","spice_smuggler","water_smuggler").contains(office))
            groups.add("dr_"+(office.endsWith("smuggler")?"path_":"office_")+office);
        if(!groups.equals(identities.put(uuid,Set.copyOf(groups))))dirty.add(uuid);
    }
    void paths(UUID uuid,boolean spice,boolean water){Set<String> desired=new HashSet<>();if(spice)desired.add("dr_path_spice_smuggler");if(water)desired.add("dr_path_water_smuggler");if(!desired.equals(contracts.put(uuid,Set.copyOf(desired))))dirty.add(uuid);}
    private Set<String> groups(UUID uuid){Set<String> all=new HashSet<>(identities.getOrDefault(uuid,Set.of()));all.addAll(contracts.getOrDefault(uuid,Set.of()));return all;}
    String house(UUID uuid){String house=houses.get(uuid);if(house!=null)return house;var user=api==null?null:api.getUserManager().getUser(uuid);return user==null?"":user.getInheritedGroups(user.getQueryOptions()).stream().filter(g->g.getName().startsWith("dr_house_")).map(g->g.getName().substring(9)).findFirst().orElse("");}
    boolean hasRole(UUID uuid,String role){if(groups(uuid).contains("dr_office_"+role)||groups(uuid).contains("dr_path_"+role))return true;var user=api==null?null:api.getUserManager().getUser(uuid);return user!=null&&user.getInheritedGroups(user.getQueryOptions()).stream().anyMatch(g->g.getName().equals("dr_office_"+role)||g.getName().equals("dr_path_"+role));}
    String access(UUID uuid){
        if(api==null)return "user";
        var user=api.getUserManager().getUser(uuid);if(user==null)return "user";
        return user.getInheritedGroups(user.getQueryOptions()).stream().filter(g->g.getName().startsWith("dr_access_")).max(Comparator.comparingInt(g->g.getWeight().orElse(0))).map(g->g.getName().substring(10)).orElse("user");
    }
    String prefix(UUID uuid){var u=api==null?null:api.getUserManager().getUser(uuid);return u==null?"":Objects.toString(u.getCachedData().getMetaData().getPrefix(),"");}
    String suffix(UUID uuid){var u=api==null?null:api.getUserManager().getUser(uuid);return u==null?"":Objects.toString(u.getCachedData().getMetaData().getSuffix(),"");}
    private void sync(){
        if(api==null||busy)return;busy=true;
        try{
            factualRoles();
        }catch(Exception ex){plugin.getLogger().warning("Managed permission sync unavailable; retaining last valid groups.");}
        finally{try{applyIdentities();}catch(Exception ignored){}busy=false;}
    }
    private void applyIdentities(){
            for(UUID uuid:new HashSet<>(dirty)){
                var user=api.getUserManager().loadUser(uuid).join();Set<String> desired=groups(uuid);
                user.data().clear(n->n instanceof InheritanceNode i&&(i.getGroupName().startsWith("dr_house_")||i.getGroupName().startsWith("dr_office_")||i.getGroupName().startsWith("dr_path_")));
                for(String group:desired)user.data().add(InheritanceNode.builder(group).build());
                api.getUserManager().saveUser(user).join();if(desired.equals(groups(uuid)))dirty.remove(uuid);
            }
    }
    void path(UUID uuid,String source,String commodity,long until){
        if(!Set.of("career","contract").contains(source)||!Set.of("spice","water").contains(commodity))return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{try(var c=plugin.connectEconomyDatabase();var q=c.prepareStatement("INSERT INTO dune_player_paths(uuid,source,commodity,expires_at) VALUES(?,?,?,?) ON CONFLICT(uuid,source,commodity) DO UPDATE SET expires_at=EXCLUDED.expires_at")){q.setObject(1,uuid);q.setString(2,source);q.setString(3,commodity);q.setLong(4,Math.max(0,until));q.executeUpdate();}catch(Exception ex){plugin.getLogger().warning("Smuggler path update could not be saved.");}});
    }
    private void factualRoles() throws Exception {
        try(var c=plugin.connectEconomyDatabase();var statement=c.createStatement()){
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS dune_player_paths(uuid UUID NOT NULL,source VARCHAR(16) NOT NULL,commodity VARCHAR(16) NOT NULL,expires_at BIGINT NOT NULL,PRIMARY KEY(uuid,source,commodity))");
            YamlConfiguration politics=new YamlConfiguration();try(var rows=statement.executeQuery("SELECT yaml FROM dune_politics WHERE id=1")){if(rows.next())politics.loadFromString(rows.getString(1));}
            Map<UUID,Set<String>> paths=new HashMap<>();try(var q=c.prepareStatement("SELECT uuid,commodity FROM dune_player_paths WHERE expires_at>?")){q.setLong(1,System.currentTimeMillis());try(var rows=q.executeQuery()){while(rows.next())paths.computeIfAbsent(rows.getObject(1,UUID.class),ignored->new HashSet<>()).add(rows.getString(2)+"_smuggler");}}
            long week=java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS).getEpochSecond()/604800;
            try(var rows=statement.executeQuery("SELECT uuid,faction FROM dune_players")){while(rows.next()){UUID uuid=rows.getObject(1,UUID.class);String house=rows.getString(2);Set<String> roles=PoliticalRanks.resolve(politics,week,uuid,house);roles.addAll(paths.getOrDefault(uuid,Set.of()));identity(uuid,house,roles);}}
        }
    }
}
