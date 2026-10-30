package com.example.dunemenus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.*;

/** Current elected offices and house membership; administrative changes remain assigned-case actions. */
final class GovernanceScreen {
 static final Map<String,String> HOUSES=Map.of("Fremen","Vrijduin","Atreides","Aerdrecht","Harkonnen","Harkveen","Corrino","Korenholt","Ordos","Oordwijk");
 public record Choice(String label,String description,Runnable action,String imageUrl){}
 private GovernanceScreen(){}
 static boolean allowed(Player player){return player.isOnline()&&player.hasPermission("dune.admin");}
 static void open(DuneMenus renderer,Player player){
  if(!allowed(player))return;
  Plugin core=Bukkit.getPluginManager().getPlugin("DuneCore");if(core==null||!core.isEnabled())return;
  try{
   Object politics=core.getClass().getMethod("politics").invoke(core);
   Object raw=politics.getClass().getMethod("websiteSnapshot").invoke(politics);
   if(!(raw instanceof Map<?,?> snapshot))throw new IllegalStateException();
   List<Choice> choices=new ArrayList<>();
   choices.add(new Choice("Imperial Offices",summary(snapshot),()->details(renderer,player,"Imperial Offices",summary(snapshot)),null));
   Object leaders=snapshot.get("leaders");
   if(leaders instanceof List<?> list)for(Object value:list)if(value instanceof Map<?,?> house&&house.get("faction") instanceof String faction&&HOUSES.containsKey(faction)){
    String title=HOUSES.get(faction),leader=text(house.get("leader"));
    List<String> members=Bukkit.getOnlinePlayers().stream().filter(member->{try{return faction.equals(core.getClass().getMethod("factionOf",Player.class).invoke(core,member));}catch(ReflectiveOperationException unavailable){return false;}}).map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    String body="Current elected leader: "+leader+"\nOnline members on this server: "+members.size()+"\n"+String.join(", ",members)+"\n\nFaction corrections use an assigned case and are audited. Elections and appointment expiry remain authoritative.";
    choices.add(new Choice(title,body,()->details(renderer,player,title,body),null));
   }
   if(Bukkit.getPluginManager().isPluginEnabled("DuneStaff"))choices.add(new Choice("Assigned Case Corrections","Review the suspect and change house only through an assigned, authorised case.",()->{if(allowed(player))player.performCommand("dunestaff:dunecase");},null));
   choices.add(new Choice("Refresh","Read current offices and online house membership.",()->open(renderer,player),null));
   renderer.showChoices(renderer,player,"Houses & Imperial Governance","Election week: "+text(snapshot.get("electionWeek")),choices);
  }catch(ReflectiveOperationException|RuntimeException unavailable){player.sendMessage("Current political records are unavailable. No membership or office was changed.");}
 }
 static String text(Object value){return value==null?"Not appointed":String.valueOf(value);}
 static String summary(Map<?,?> data){return "Emperor: "+text(data.get("emperor"))+"\nSpice steward: "+text(data.get("spiceSteward"))+"\nHouse of Water: "+text(data.get("houseOfWater"))+"\n\nOffices follow the current election and Emperor. Reviewing a record does not grant office powers or regalia.";}
 private static void details(DuneMenus renderer,Player player,String title,String body){if(allowed(player))renderer.showChoices(renderer,player,title,body,List.of(new Choice("Back to Governance","Refresh elected offices and houses.",()->open(renderer,player),null)));}
}
