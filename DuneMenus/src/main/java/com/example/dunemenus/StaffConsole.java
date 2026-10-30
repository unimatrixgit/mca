package com.example.dunemenus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.*;
import java.util.function.Predicate;

/** Fixed native entry points, always executed as the player. No console delegation. */
final class StaffConsole {
 record Tool(String title,String description,String plugin,String command,Predicate<Player> allowed){}
 static final List<Tool> TOOLS=List.of(
  new Tool("Assigned Cases","Take tickets and work with the verified creator and suspect. Case actions require current assignment and permission.","DuneStaff","dunestaff:dunecase",p->p.hasPermission("dune.tickets.staff")),
  new Tool("Moderation & Tickets","Local and network ban records, player review and your authorised ticket desk.","DuneCore","dunecore:dune admin",p->any(p,"dune.admin","dune.moderation.ban","dune.moderation.unban","dune.ban.local","dune.ban.global","dune.unban.local","dune.unban.global","dune.tickets.staff")),
  new Tool("Quest Administration","Review the quest engine and open its authoritative configuration.","DuneQuests","dunequests:dunequests admin",p->p.hasPermission("dune.quests.admin")),
  new Tool("Network Treasury","Staff Dunemarks balances, transfers and ledger. Website authority is checked by the bank.","DuneBank","dunebank:staffbank",p->p.hasPermission("dunebank.use")&&p.hasPermission("dunebank.staff.view")),
  new Tool("NPC & Mission Management","Routekeepers, managed NPCs and mission status.","DuneNPC","dunenpc:dunenpc admin",p->p.hasPermission("dune.admin")),
  new Tool("Houses & Governance","Current elected offices, house membership and assigned-case faction corrections.","DuneCore","dunemenus:dunemenus governance",p->p.hasPermission("dune.admin")),
  new Tool("Administration","World operations, faction kit tools, menu configuration and political status.","DuneCore","dunecore:dune admin",p->p.hasPermission("dune.admin"))
 );
 private StaffConsole(){}
 static boolean any(Player player,String...nodes){return Arrays.stream(nodes).anyMatch(player::hasPermission);}
 static boolean available(Tool tool,Player player){
  if(!player.isOnline()||!tool.allowed.test(player))return false;
  Plugin dependency=Bukkit.getPluginManager().getPlugin(tool.plugin);
  return dependency!=null&&dependency.isEnabled();
 }
 public record Choice(String label,String description,Runnable action,String imageUrl){}
 static void open(DuneMenus renderer,Player player){
  List<Choice> choices=new ArrayList<>();
  for(Tool tool:TOOLS)if(available(tool,player))choices.add(new Choice(tool.title,tool.description,()->run(tool,player),null));
  if(choices.isEmpty()){player.sendMessage("No staff tools are available for your current permissions on this server.");return;}
  renderer.showChoices(renderer,player,"Staff Operations","Choose a department. Each service checks your current permissions again before an action.",choices);
 }
 static void run(Tool tool,Player player){
  if(!available(tool,player)){player.sendMessage("This staff tool is no longer available to your account on this server.");return;}
  if(!player.performCommand(tool.command))player.sendMessage("The staff service could not open. Please try again shortly.");
 }
}

