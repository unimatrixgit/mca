package com.example.dunerealms;
import com.example.dunegui.NativeMenus;
import org.bukkit.entity.Player;
import java.util.*;

/** Presentation only: the existing ServerJobs engine remains the sole contract authority. */
final class JobsScreens {
 private static final String ART="https://www.dunerealms.com/assets/gui/imperial-jobs-v1.png";
 private final DuneCore plugin;private final ServerJobs jobs;private final ConfigurableGui gui;private final Player player;
 private JobsScreens(DuneCore p,ServerJobs j,ConfigurableGui g,Player u){plugin=p;jobs=j;gui=g;player=u;}
 static void open(DuneCore p,ServerJobs j,ConfigurableGui g,Player u){new JobsScreens(p,j,g,u).home();}
 private NativeMenus.Choice choice(String name,String text,Runnable action){return new NativeMenus.Choice(name,text,action,ART);}
 private void show(String title,String body,List<NativeMenus.Choice> buttons){if(plugin.isEnabled()&&player.isOnline())NativeMenus.show(plugin,player,title,body,buttons);}
 private void home(){
  var active=jobs.activeView(player);
  show("Spice Jobs","KOOPKRING GUILD\nChoose your work. Contracts rotate hourly; one server contract at a time.",List.of(
   choice("Spice Miner","Browse harvesting contracts · review quota, pay, deadline and 5% failure fine",this::miner),
   choice("Manage Job",active==null?"No active server contract":status(active),this::manage),
   choice("Player-sponsored jobs","Separate player listings · worker 15% / sponsor 85%",()->gui.open(player,"exchange")),
   choice("Player menu","Return to the Imperial Navigator",()->gui.open(player,"main"))));
 }
 private void miner(){
  List<NativeMenus.Choice> buttons=new ArrayList<>();boolean working=jobs.activeView(player)!=null;
  for(var offer:jobs.offerViews())buttons.add(choice(offer.title(),terms(offer)+(working?"\nFinish your current contract first":"\nReview before accepting"),()->review(offer)));
  if(buttons.isEmpty())buttons.add(choice("No contracts available","Check after the next hourly rotation",this::miner));
  buttons.add(choice("Manage Job","Read your current contract",this::manage));buttons.add(choice("Back to Spice Jobs","Return without changing your job",this::home));
  show("Spice Miner","HARVESTING CONTRACTS\nRewards and deadlines are set by the server, never by this menu.",buttons);
 }
 static boolean sameOffer(ServerJobs.OfferView expected,List<ServerJobs.OfferView> current){return current.contains(expected);}
 static String terms(ServerJobs.OfferView offer){return "Harvest: "+offer.required()+" spice\nCompletion pay: "+offer.pay()+" Dunemarks\nDeadline: "+offer.minutes()+" minutes after acceptance\nFailure fine: "+offer.fine()+" Dunemarks (5%)";}
 static String status(ServerJobs.ActiveView active){long left=Math.max(0,active.secondsLeft());return active.title()+"\nProgress: "+active.harvested()+" / "+active.required()+" spice\nTime remaining: "+left/60+"m "+left%60+"s\nCompletion pay: "+active.pay()+" Dunemarks";}
 private void review(ServerJobs.OfferView offer){
  if(jobs.activeView(player)!=null){manage();return;}
  if(!sameOffer(offer,jobs.offerViews())){player.sendMessage("This contract changed. Review the current offers.");miner();return;}
  show("Review Spice Contract",offer.title()+"\n"+terms(offer),List.of(choice("Accept contract","Starts the deadline. Current availability is checked again.",()->{
   if(!plugin.isEnabled()||!player.isOnline())return;
   if(jobs.activeView(player)!=null||!sameOffer(offer,jobs.offerViews())){player.sendMessage("Contract availability changed. No new job was accepted.");home();return;}
   plugin.runGuiCommand(player,"serverjobs accept "+offer.id());manage();
  }),choice("Back to contracts","No job changes",this::miner)));
 }
 private void manage(){
  var active=jobs.activeView(player);
  String body=active==null?"No active server contract. Choose Spice Miner to start.":status(active)+"\n\nProgress is saved automatically. Completion pays automatically. Expiry applies the existing 5% failure fine.\nThis menu cannot cancel a contract or claim payment early.";
  show("Manage Job",body,List.of(choice("Refresh progress","Read the latest saved job status",this::manage),choice("Spice Miner","Browse currently available contracts",this::miner),choice("Back to Spice Jobs","Return to the guild desk",this::home)));
 }
}
