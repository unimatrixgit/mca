package com.example.dunemenus;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import java.io.File;

/** Virtual menu renderer: Java books and native Bedrock forms, never chest containers. */
@SuppressWarnings("deprecation")
public final class DuneMenus extends JavaPlugin implements Listener,CommandExecutor,TabCompleter {
 private static final SecureRandom RANDOM=new SecureRandom();
 private static final long LIFETIME=30_000;
 private static final Key FONT=Key.key("dunerealms:imperial_menus");
 public boolean hasStaffTools(Player player){return StaffConsole.TOOLS.stream().anyMatch(tool->StaffConsole.available(tool,player));}
 private final Map<UUID,View> views=new HashMap<>();
 private final Set<UUID> discoveries=new HashSet<>();
 record Card(String label,String body,Runnable action,String image,String glyph,ItemStack item){
  Card(String label,String body,Runnable action,String image,String glyph){this(label,body,action,image,glyph,null);}
  Card { item=item==null?null:item.clone(); }
  @Override public ItemStack item(){return item==null?null:item.clone();}
 }
 record View(ViewGate gate,String title,List<Card> cards,AtomicLong revision){}
 @Override public void onEnable(){for(String name:List.of("dunemenus","navigator","dunestaff")){Objects.requireNonNull(getCommand(name)).setExecutor(this);getCommand(name).setTabCompleter(this);}Bukkit.getPluginManager().registerEvents(this,this);
  getLogger().info("Graphical inventory navigator ready · Alpha 1.0.0.3. Java dialog route disabled; Bedrock native skin retained.");
 }
 @Override public void onDisable(){views.values().forEach(v->v.gate.cancel());views.clear();GraphicalMenu.clear();}
 static String token(){byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);return HexFormat.of().formatHex(bytes);}
 static String plain(String input){return ChatColor.stripColor(Objects.toString(input,"")).replace("CHOAM","Koopkring");}
 static List<String> chunks(String text,int limit){
  List<String> result=new ArrayList<>();StringBuilder current=new StringBuilder();
  for(String line:plain(text).replace('\r',' ').split("\n",-1)){
   for(int start=0;start<Math.max(1,line.length());start+=limit){String part=line.substring(Math.min(start,line.length()),Math.min(start+limit,line.length()));
    if(current.length()+part.length()+1>limit*9){result.add(current.toString());current.setLength(0);}
    current.append(part).append('\n');
   }
  }
  if(!current.isEmpty())result.add(current.toString());return result;
 }
 public boolean show(JavaPlugin caller,Player player,String title,Inventory model,Map<Integer,Runnable> actions,BooleanSupplier allowed){
  if(!Bukkit.isPrimaryThread()||!caller.isEnabled()||!player.isOnline()||!allowed.getAsBoolean())return false;
  List<Card> cards=new ArrayList<>();
  for(int slot=0;slot<model.getSize();slot++){
   ItemStack item=model.getItem(slot);if(item==null||item.getType().isAir())continue;
   ItemMeta meta=item.getItemMeta();String label=meta!=null&&meta.hasDisplayName()?plain(meta.getDisplayName()):item.getType().name();
   if(label.isBlank())continue;
   String body=meta!=null&&meta.hasLore()?String.join("\n",meta.getLore()):"";
   if(item.getAmount()>1)body="Quantity: "+item.getAmount()+"\n"+body;
   String key=meta!=null&&meta.hasItemModel()?meta.getItemModel().getKey():"";
   String glyph=glyph(key,label);cards.add(new Card(label,plain(body),actions.get(slot),image(glyph),glyph,item));
  }
  return present(player,title,model,cards,()->caller.isEnabled()&&allowed.getAsBoolean());
 }
 public boolean showChoices(JavaPlugin caller,Player player,String title,String content,List<?> choices){
  List<Card> cards=new ArrayList<>();
  if(!content.isBlank())cards.add(new Card(title,content,null,image("\ue201"),"\ue201"));
  try{for(Object c:choices){Class<?> type=c.getClass();String label=(String)type.getMethod("label").invoke(c),description=(String)type.getMethod("description").invoke(c),url=(String)type.getMethod("imageUrl").invoke(c);Runnable action=(Runnable)type.getMethod("action").invoke(c);String glyph=glyph("",label),provided=safeImage(url);cards.add(new Card(plain(label),plain(description),action,provided==null?image(glyph):provided,glyph));}}
  catch(ReflectiveOperationException error){return false;}
  return present(player,title,new Object(),cards,caller::isEnabled);
 }
 private boolean present(Player p,String title,Object model,List<Card> cards,BooleanSupplier allowed){
  if(!Bukkit.isPrimaryThread()||!p.isOnline()||!allowed.getAsBoolean()||cards.size()>60)return false;
  cancel(p);p.closeInventory();ViewGate gate=new ViewGate(p.getUniqueId(),model,token(),System.currentTimeMillis()+LIFETIME,allowed);
  View view=new View(gate,plain(title),List.copyOf(cards),new AtomicLong());views.put(p.getUniqueId(),view);
  if(isBedrock(p)){try{Bedrock.show(this,p,view,0);}catch(LinkageError|RuntimeException failure){cancel(p);p.sendMessage(ChatColor.RED+"The Bedrock menu could not be opened. Reopen the navigator.");return false;}}
  else render(p,view,-1);
  Bukkit.getScheduler().runTaskLater(this,()->{if(views.get(p.getUniqueId())==view){cancel(p);if(p.isOnline()){p.closeInventory();p.sendMessage(ChatColor.GRAY+"Navigator view expired. Open it again for current information.");}}},600L);
  return true;
 }
 public boolean isCurrent(Player p,Inventory exact){View view=views.get(p.getUniqueId());return view!=null&&p.isOnline()&&view.gate.current(p.getUniqueId(),exact,System.currentTimeMillis());}
 public void close(Player p,Inventory exact){View v=views.get(p.getUniqueId());if(v!=null&&v.gate.model==exact)cancel(p);}
 public void cancel(Player p){GraphicalMenu.forget(p);View old=views.remove(p.getUniqueId());if(old!=null)old.gate.cancel();}
 private static boolean isBedrock(Player p){return Bukkit.getPluginManager().isPluginEnabled("floodgate")&&Bedrock.isPlayer(p);}
 private static String safeImage(String url){return url!=null&&url.startsWith("https://www.dunerealms.com/assets/")?url:null;}
 static String image(String glyph){return "https://www.dunerealms.com/assets/gui/"+switch(glyph){case "\ue210"->"faction-fremen-vrijduin-heraldic.png";case "\ue211"->"faction-atreides-aerdrecht-heraldic.png";case "\ue212"->"faction-harkonnen-harkveen-heraldic.png";case "\ue213"->"faction-corrino-korenholt-heraldic.png";case "\ue214"->"faction-ordos-oordwijk-heraldic.png";case "\ue220"->"imperial-shop-v1.png";case "\ue221"->"imperial-jobs-v1.png";default->"imperial-portal.png";};}
 private static String glyph(String key,String label){
  String lower=plain(label).toLowerCase(Locale.ROOT);
  if(lower.contains("vrijduin")||lower.contains("fremen"))return "\ue210";
  if(lower.contains("aerdrecht")||lower.contains("aedrecht")||lower.contains("atreides"))return "\ue211";
  if(lower.contains("harkveen")||lower.contains("harkonnen"))return "\ue212";
  if(lower.contains("korenholt")||lower.contains("corrino"))return "\ue213";
  if(lower.contains("oordwijk")||lower.contains("ordos"))return "\ue214";
  return switch(key){case "gui_market"->"\ue220";case "gui_contract"->"\ue221";case "gui_confirm"->"\ue203";default->"\ue202";};
 }
 private Component link(String text,View v,String operation,int index){return Component.text(text,NamedTextColor.DARK_AQUA).clickEvent(ClickEvent.runCommand("/dunemenus "+operation+" "+v.gate.token+" "+index+" "+v.revision.get()));}
 private void render(Player p,View view,int detail){
  if(!current(p,view))return;
  GraphicalMenu.show(this,p,view,detail,0);
 }
 boolean current(Player p,View view){return views.get(p.getUniqueId())==view&&p.isOnline()&&view.gate.current(p.getUniqueId(),view.gate.model,System.currentTimeMillis());}
 void navigate(Player p,View view,String operation,int index,long revision){
  if(!current(p,view)||view.revision.get()!=revision)return;
  onCommand(p,null,"dunemenus",new String[]{operation,view.gate.token,Integer.toString(index),Long.toString(revision)});
 }
 private void book(Player p,View view,int detail){
  if(views.get(p.getUniqueId())!=view||!view.gate.current(p.getUniqueId(),view.gate.model,System.currentTimeMillis()))return;
  view.revision.incrementAndGet();
  List<Component> pages=new ArrayList<>();
  if(detail<0){
   Component ornament=Component.text("\ue204\n\n\n\n\n",NamedTextColor.WHITE).font(FONT);
   if(discoveryView(view))ornament=ornament.clickEvent(ClickEvent.runCommand("/dunemenus ornament "+view.gate.token+" 0 "+view.revision.get()));
   pages.add(Component.text("DUNEREALMS\n",NamedTextColor.GOLD).append(Component.text(view.title+"\n",NamedTextColor.DARK_AQUA)).append(ornament).append(Component.text("Turn pages to browse.\nSelect a card for details.\nView expires in 30 seconds.\n\n",NamedTextColor.DARK_GRAY)).append(link("[Close]",view,"close",0)));
   for(int n=0;n<view.cards.size();n++){Card card=view.cards.get(n);String excerpt=card.body.length()>96?card.body.substring(0,96)+"…":card.body;
    pages.add(Component.empty().append(Component.text(card.glyph+"\n\n\n\n",NamedTextColor.WHITE).font(FONT)).append(Component.text(card.label+"\n\n",NamedTextColor.GOLD)).append(Component.text(excerpt+"\n\n",NamedTextColor.DARK_GRAY)).append(link(card.action==null?"[Read]":"[Review / Select]",view,"detail",n)));
   }
  }else{
   Card card=view.cards.get(detail);List<String> chunks=chunks(card.body,32);
   for(int i=0;i<chunks.size();i++)pages.add(Component.text(card.label+"\n\n",NamedTextColor.GOLD).append(Component.text(chunks.get(i),NamedTextColor.DARK_GRAY)));
   Component confirm=Component.text(card.label+"\n\n",NamedTextColor.GOLD).append(Component.text("Review the previous pages before continuing.\n\n",NamedTextColor.DARK_GRAY));
   if(card.action!=null)confirm=confirm.append(link("[Select / Continue]\n\n",view,"action",detail));
   pages.add(confirm.append(link("[Back]",view,"back",0)).append(Component.text("\n\n")).append(link("[Close]",view,"close",0)));
  }
  if(pages.size()>100){cancel(p);p.sendMessage(ChatColor.RED+"This record is too long for the navigator. Use the website or ask staff to inspect it.");return;}
  p.openBook(Book.book(Component.text(view.title),Component.text("DuneRealms"),pages));
 }
 @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
  if(sender instanceof Player administrator&&args.length==1&&args[0].equalsIgnoreCase("governance")){GovernanceScreen.open(this,administrator);return true;}
  if(sender instanceof Player staff&&((cmd!=null&&cmd.getName().equals("dunestaff"))||(args.length==1&&args[0].equalsIgnoreCase("staff")))){StaffConsole.open(this,staff);return true;}
  if(sender instanceof Player player&&args.length==0){
   Plugin core=Bukkit.getPluginManager().getPlugin("DuneCore");
   if(core!=null&&core.isEnabled()){player.performCommand("dune menu");return true;}
   Plugin lobby=Bukkit.getPluginManager().getPlugin("DuneLobby");
   if(lobby!=null&&lobby.isEnabled())try{lobby.getClass().getMethod("openNavigator",Player.class).invoke(lobby,player);return true;}catch(ReflectiveOperationException unavailable){}
   player.sendMessage("The Imperial Navigator is not available on this backend yet.");return true;
  }
  if(!(sender instanceof Player p)||args.length!=4)return true;
  View view=views.get(p.getUniqueId());if(view==null||!view.gate.token.equals(args[1])||!view.gate.current(p.getUniqueId(),view.gate.model,System.currentTimeMillis())){p.sendMessage(ChatColor.RED+"This navigator view has expired.");return true;}
  int n;long revision;try{n=Integer.parseInt(args[2]);revision=Long.parseLong(args[3]);}catch(NumberFormatException invalid){return true;}if(revision!=view.revision.get())return true;
  switch(args[0]){
   case "close"->{cancel(p);if(DialogSupport.supported(p))DialogScreen.close(p);p.closeInventory();}
   case "back"->render(p,view,-1);
   case "page"->{if(n>=0&&n<Math.max(1,(view.cards.size()+5)/6))GraphicalMenu.show(this,p,view,-1,n);}
   case "detail"->{if(n>=0&&n<view.cards.size())render(p,view,n);}
   case "action"->dispatch(p,view,n);
   case "ornament"->{if(discoveryView(view))discover(p,view);}
  }
  return true;
 }
 @Override public List<String> onTabComplete(CommandSender sender,Command cmd,String label,String[] args){return List.of();}
 private static boolean discoveryView(View view){String title=view.title.toLowerCase(Locale.ROOT);return title.contains("main menu")||title.equals("dune | main menu")||title.contains("imperial navigator");}
 private void discover(Player p,View view){
  if(discoveries.contains(p.getUniqueId())||views.get(p.getUniqueId())!=view)return;
  Plugin ess=Bukkit.getPluginManager().getPlugin("DuneEss");if(ess==null||!ess.isEnabled()){p.sendMessage(ChatColor.GRAY+"The old gate is quiet. Return when the account bridge is available.");return;}
  YamlConfiguration config=YamlConfiguration.loadConfiguration(new File(ess.getDataFolder(),"website-bridge.yml"));String secret=config.getString("secret","");
  if(!config.getString("origin","").equals("https://cp.dunerealms.com")||secret.length()<32)return;
  if(!view.gate.claim(p.getUniqueId(),view.gate.token,System.currentTimeMillis()))return;
  UUID uuid=p.getUniqueId();discoveries.add(uuid);views.remove(uuid);GraphicalMenu.forget(p);p.closeInventory();
  Bukkit.getScheduler().runTaskAsynchronously(this,()->{
   QuestDiscovery.Reply result=null;try{result=QuestDiscovery.request(secret,uuid,view.gate.token,view.gate.expires);}catch(Exception unavailable){/* No codes, proofs or credentials are logged. */}
   QuestDiscovery.Reply reply=result;if(!isEnabled())return;
   Bukkit.getScheduler().runTask(this,()->{discoveries.remove(uuid);if(!p.isOnline()||!uuid.equals(p.getUniqueId()))return;
    if(reply==null){p.sendMessage(ChatColor.GRAY+"The discovery could not be confirmed. Return to the navigator and try again.");return;}
    p.sendMessage(Component.text(reply.message(),NamedTextColor.GOLD));
    if(!reply.code().isBlank()){p.sendMessage(Component.text(reply.code(),NamedTextColor.AQUA).clickEvent(ClickEvent.copyToClipboard(reply.code())));p.sendMessage(Component.text("Open Account Center to redeem →",NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl("https://cp.dunerealms.com/#game")));}
   });
  });
 }
 private void dispatch(Player p,View view,int index){
  if(index<0||index>=view.cards.size()||view.cards.get(index).action==null||views.get(p.getUniqueId())!=view||!view.gate.claim(p.getUniqueId(),view.gate.token,System.currentTimeMillis()))return;
  Bukkit.getScheduler().runTask(this,()->{
   if(!p.isOnline()||views.get(p.getUniqueId())!=view||!view.gate.allowedAfterClaim(p.getUniqueId(),System.currentTimeMillis()))return;
   views.remove(p.getUniqueId());GraphicalMenu.forget(p);p.closeInventory();
   try{view.cards.get(index).action.run();}catch(RuntimeException failure){p.sendMessage(ChatColor.RED+"Action could not be confirmed. Open a fresh menu before retrying.");getLogger().warning("Navigator callback failed for actor UUID "+p.getUniqueId());}
  });
 }
 @EventHandler public void graphicalClick(org.bukkit.event.inventory.InventoryClickEvent event){GraphicalMenu.click(this,event);}
 @EventHandler public void graphicalDrag(org.bukkit.event.inventory.InventoryDragEvent event){GraphicalMenu.drag(event);}
 @EventHandler public void graphicalClose(org.bukkit.event.inventory.InventoryCloseEvent event){GraphicalMenu.closed(this,event);}
 @EventHandler public void quit(PlayerQuitEvent event){cancel(event.getPlayer());}
 @EventHandler public void inventory(InventoryOpenEvent event){if(event.getPlayer() instanceof Player p&&!GraphicalMenu.owned(event.getInventory(),p))cancel(p);}
 private static final class Bedrock {
  static boolean isPlayer(Player p){return org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId());}
  static void show(DuneMenus plugin,Player p,View view,int page){
   if(plugin.views.get(p.getUniqueId())!=view||!view.gate.current(p.getUniqueId(),view.gate.model,System.currentTimeMillis()))return;
   long revision=view.revision.incrementAndGet();
   var form=org.geysermc.cumulus.form.SimpleForm.builder().title("DRUI|"+view.title).content(MenuTheme.of(view.title).name()+" · "+view.title+" · choose a service");
   int start=page*12,end=Math.min(start+12,view.cards.size());
   List<Runnable> actions=new ArrayList<>();
   for(int i=start;i<end;i++){int index=i;Card card=view.cards.get(i);String image=safeImage(card.image);if(image==null)form.button(card.label);else form.button(card.label,org.geysermc.cumulus.util.FormImage.Type.URL,image);actions.add(()->detail(plugin,p,view,index));}
   if(page==0&&discoveryView(view)){form.button("✧",org.geysermc.cumulus.util.FormImage.Type.URL,"https://www.dunerealms.com/assets/gui/imperial-portal.png");actions.add(()->plugin.discover(p,view));}
   if(page>0){form.button("← Previous");actions.add(()->show(plugin,p,view,page-1));}
   if(end<view.cards.size()){form.button("Next →");actions.add(()->show(plugin,p,view,page+1));}
   form.button("Close");actions.add(()->plugin.cancel(p));
   form.closedOrInvalidResultHandler(()->Bukkit.getScheduler().runTask(plugin,()->{if(plugin.views.get(p.getUniqueId())==view&&view.revision.get()==revision)plugin.cancel(p);}));
   form.validResultHandler(r->{int n=r.clickedButtonId();Bukkit.getScheduler().runTask(plugin,()->{if(n>=0&&n<actions.size()&&plugin.views.get(p.getUniqueId())==view&&view.revision.get()==revision&&view.gate.current(p.getUniqueId(),view.gate.model,System.currentTimeMillis()))actions.get(n).run();});});
   org.geysermc.floodgate.api.FloodgateApi.getInstance().sendForm(p.getUniqueId(),form.build());
  }
  static void detail(DuneMenus plugin,Player p,View view,int index){
   long revision=view.revision.incrementAndGet();
   Card card=view.cards.get(index);var form=org.geysermc.cumulus.form.SimpleForm.builder().title("DRUI|"+card.label).content(card.body);
   if(card.action!=null)form.button("Select / Continue");form.button("Back");form.button("Close");
   form.closedOrInvalidResultHandler(()->Bukkit.getScheduler().runTask(plugin,()->{if(plugin.views.get(p.getUniqueId())==view&&view.revision.get()==revision)plugin.cancel(p);}));
   form.validResultHandler(r->Bukkit.getScheduler().runTask(plugin,()->{if(plugin.views.get(p.getUniqueId())!=view||view.revision.get()!=revision||!view.gate.current(p.getUniqueId(),view.gate.model,System.currentTimeMillis()))return;int n=r.clickedButtonId();if(card.action!=null&&n==0)plugin.dispatch(p,view,index);else if(n==(card.action!=null?1:0))show(plugin,p,view,index/12);else plugin.cancel(p);}));
   org.geysermc.floodgate.api.FloodgateApi.getInstance().sendForm(p.getUniqueId(),form.build());
  }
 }
}
