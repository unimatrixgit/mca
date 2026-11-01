package com.example.duneess;

import org.bukkit.configuration.file.YamlConfiguration;
import java.util.*;

/** Current political facts, interpreted identically to Core elections and appointment expiry. */
final class PoliticalRanks {
    static String winner(YamlConfiguration state,String prefix){
        var tally=state.getConfigurationSection(prefix+".tally");if(tally==null)return "";
        String best="";int score=0;for(String id:tally.getKeys(false)){
            int n=tally.getInt(id);if(n>score||(n==score&&id.compareTo(best)<0)){best=id;score=n;}
        }return best;
    }
    static Set<String> resolve(YamlConfiguration state,long week,UUID uuid,String faction){
        Set<String> roles=new HashSet<>();String id=uuid.toString(),emperor=winner(state,"votes.emperor."+week);
        if(!emperor.isBlank()&&emperor.equals(id))roles.add("emperor");
        if(!faction.isBlank()&&id.equals(winner(state,"votes.house."+week+"."+faction)))roles.add("house_leader");
        for(String key:List.of("steward","water")){
            String root="appointments."+key;
            if(!emperor.isBlank()&&state.getLong(root+".week",-1)==week&&emperor.equals(state.getString(root+".emperor",""))&&faction.equalsIgnoreCase(state.getString(root+".house","")))roles.add(key.equals("water")?"welvaart":"specerhuis");
        }
        if(RankBridge.houseKey(faction).equals("vaerkring"))roles.add("guild_staff");
        if(!roles.isEmpty())roles.add("landsraad");
        return roles;
    }
}
