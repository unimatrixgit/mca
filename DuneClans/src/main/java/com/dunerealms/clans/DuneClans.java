package com.dunerealms.clans;

import com.example.dunegui.NativeMenus;
import com.google.gson.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.sql.*;
import java.math.BigDecimal;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Native clan bank/shop; every real item movement shares Core's fenced SQL transaction. */
public final class DuneClans extends JavaPlugin implements CommandExecutor {
 private JavaPlugin core;private Driver driver;private String url;private Properties auth;
 private final ClanStore store=new ClanStore();private final ClanMoneyStore money=new ClanMoneyStore();private volatile boolean ready;
 private final ExecutorService worker=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"DuneClans-storage");t.setDaemon(true);return t;});
 private final Set<UUID> busy=new HashSet<>();
 private final NamespacedKey itemKey=new NamespacedKey("dunerealms","item"),qualityKey=new NamespacedKey("dunerealms","quality");
 @Override public void onEnable(){
  saveDefaultConfig();core=(JavaPlugin)getServer().getPluginManager().getPlugin("DuneCore");getCommand("duneclan").setExecutor(this);
  try{
   var settings=getConfig();url=settings.getString("database.jdbc-url","");if(url.isBlank())settings=core.getConfig();
   url=settings.getString("database.jdbc-url","");if(!url.startsWith("jdbc:mysql:"))throw new IllegalStateException("MySQL required");
   auth=new Properties();auth.setProperty("user",settings.getString("database.username",""));auth.setProperty("password",settings.getString("database.password",""));
   auth.setProperty("connectTimeout","3000");auth.setProperty("socketTimeout","5000");auth.setProperty("allowPublicKeyRetrieval","true");auth.setProperty("useSSL","false");
   driver=(Driver)Class.forName("com.mysql.cj.jdbc.Driver",true,core.getClass().getClassLoader()).getDeclaredConstructor().newInstance();
   worker.execute(()->{try(Connection c=connect()){ClanStore.initialize(c);ClanMoneyStore.initialize(c);ClanMoneyStore.initializeTreasury(c);ready=true;getLogger().info("Dunemarks clan bank and kit store ready; website membership remains authoritative.");}catch(Exception e){getLogger().severe("Clan storage is unavailable; item and bank actions remain disabled.");}});
  }catch(Exception e){getLogger().severe("Private shared MySQL configuration required. Clan transactions disabled.");}
 }
 @Override public void onDisable(){ready=false;worker.shutdown();try{if(!worker.awaitTermination(5,TimeUnit.SECONDS))worker.shutdownNow();}catch(InterruptedException e){worker.shutdownNow();Thread.currentThread().interrupt();}}
 private Connection connect()throws SQLException{return driver.connect(url,auth);}
 private void main(Runnable work){if(isEnabled())getServer().getScheduler().runTask(this,work);}
 @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
  if(!(sender instanceof Player p)){sender.sendMessage("Use this command in game.");return true;}
  if(!p.hasPermission("dune.clans.view")){p.sendMessage("Clan menu access denied.");return true;}
  open(p,args.length>0?args[0].toLowerCase(Locale.ROOT):"home");return true;
 }
 record View(ClanStore.Context ctx,BigDecimal bank,BigDecimal wallet,List<ClanMoneyStore.Entry> ledger){}
 static boolean entryAllowed(Player p){return p.isOnline()&&p.hasPermission("dune.clans.view");}
 static boolean current(Player p,Inventory loading){return entryAllowed(p)&&NativeMenus.isCurrent(p,loading);}
 private void open(Player p,String page){
  if(!entryAllowed(p))return;
  if(!ready){p.sendMessage("The clan bank is still starting. Try again shortly.");return;}
  if(!busy.add(p.getUniqueId()))return;
  Inventory loading=Bukkit.createInventory(null,9,"Clan Center - Loading");
  NativeMenus.inventory(this,p,"Clan Center - Loading",loading,Map.of(),()->entryAllowed(p));
  worker.execute(()->{try(Connection c=connect()){
   ClanStore.Context ctx=store.context(c,p.getUniqueId());View v=new View(ctx,money.balance(c,ctx.clan(),false),store.wallet(c,p.getUniqueId()),money.ledger(c,ctx.clan()));
   main(()->{busy.remove(p.getUniqueId());if(current(p,loading))show(p,page,v);});
  }catch(Exception e){main(()->{busy.remove(p.getUniqueId());if(current(p,loading))p.sendMessage(ChatColor.GOLD+safe(e));});}});
 }
 private static String safe(Throwable error){
  while(error instanceof InvocationTargetException||error instanceof TransactionFailure){if(error.getCause()==null)break;error=error.getCause();}
  String m=error.getMessage();
  if(m!=null&&(m.startsWith("Your clan")||m.startsWith("Link your")||m.startsWith("The kit")||m.startsWith("Not enough")||m.startsWith("The bank")||m.startsWith("The clan bank")||m.startsWith("Only clan")||m.startsWith("Free four")||m.startsWith("Use a Dunemarks")||m.startsWith("This confirmation")))return m;
  return "Clan action could not be confirmed. Reopen the menu before trying again.";
 }
 static Runnable guarded(Player p,Runnable action){return ()->{if(entryAllowed(p))action.run();else p.sendMessage("Clan menu access changed. Reopen your current account menu.");};}
 private NativeMenus.Choice choice(Player p,String title,String description,Runnable action){return new NativeMenus.Choice(title,description,guarded(p,action));}
 private void show(Player p,String page,View v){
  if(!entryAllowed(p))return;List<NativeMenus.Choice> menu=new ArrayList<>();String body="["+v.ctx.tag()+"] "+v.ctx.name()+"\nClan bank: "+v.bank.toPlainString()+" Dunemarks\nYour wallet: "+v.wallet.toPlainString()+" Dunemarks";
  switch(page){
   case "bank"->{
    if(v.wallet.signum()>0)for(BigDecimal amount:amounts(v.wallet))menu.add(choice(p,"Deposit "+amount.toPlainString()+" Dunemarks","Transfer from your wallet into the clan bank.",()->confirm(p,"Deposit Dunemarks",amount.toPlainString()+" Dunemarks to the clan bank",()->bankTransfer(p,v.ctx,amount,false,UUID.randomUUID()))));
    else menu.add(choice(p,"No Dunemarks to deposit","Your wallet has no available Dunemarks.",()->open(p,"bank")));
    if(v.ctx.officer()&&v.bank.signum()>0)for(BigDecimal amount:amounts(v.bank))menu.add(choice(p,"Withdraw "+amount.toPlainString()+" Dunemarks","Transfer from the clan bank into your wallet.",()->confirm(p,"Withdraw Dunemarks",amount.toPlainString()+" Dunemarks to your wallet",()->bankTransfer(p,v.ctx,amount,true,UUID.randomUUID()))));
    menu.add(choice(p,"Bank history","Latest deposits, withdrawals and kit charges.",()->show(p,"history",v)));
   }
   case "history"->{body+="\n\n"+String.join("\n",v.ledger.stream().map(e->e.action()+" · "+(e.delta().signum()>0?"+":"")+e.delta().toPlainString()+" Dunemarks → "+e.balance().toPlainString()).toList());}
   case "shop","kits"->{
    if(v.ctx.blueprint()==null)body+="\n\nYour clan has no stillsuit design yet.";
    else{
     body+="\n\n"+v.ctx.kitName()+"\nQuality: Uncommon\nFour-piece set · "+v.ctx.price()+" Dunemarks\nColor: "+v.ctx.color()+"\n"+String.join("\n",lore(v.ctx.lore()));
     menu.add(choice(p,"Buy kit — Dunemarks wallet","Pay "+v.ctx.price()+" Dunemarks from your wallet.",()->purchasePreview(p,v,false)));
     if(v.ctx.officer())menu.add(choice(p,"Buy kit — clan bank","Pay "+v.ctx.price()+" Dunemarks from the clan bank.",()->purchasePreview(p,v,true)));
    }
    menu.add(choice(p,"Kit designer on the clan site",v.ctx.officer()?"Owners and officers can edit colors and lore.":"Ask an owner or officer to update the design.",()->website(p)));
   }
   default->{menu.add(choice(p,"Shop","Browse your clan's stillsuit shop.",()->open(p,"shop")));menu.add(choice(p,"Kits","Preview the clan's colors, lore and Uncommon quality.",()->open(p,"kits")));menu.add(choice(p,"Clan bank","Deposit Dunemarks, review the ledger and authorized withdrawals.",()->open(p,"bank")));menu.add(choice(p,"Clan Center","Manage membership and designs on the website.",()->website(p)));}
  }
  if(!page.equals("home"))menu.add(choice(p,"Back","Clan Shop",()->open(p,"home")));
  NativeMenus.show(this,p,"Imperial Clan Shop",body,menu);
 }
 private void website(Player p){if(!entryAllowed(p))return;p.sendMessage(net.kyori.adventure.text.Component.text("Open the Clan Center →").clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl("https://clans.dunerealms.com/")));}
 private void confirm(Player p,String title,String details,Runnable yes){NativeMenus.show(this,p,title,details,List.of(choice(p,"Confirm",details,yes),choice(p,"Cancel","Nothing is moved or charged.",()->open(p,"home"))));}
 private void purchasePreview(Player p,View v,boolean bank){
  UUID receipt=UUID.randomUUID();
  confirm(p,"Purchase Uncommon Clan Kit",v.ctx.kitName()+"\nFour armor pieces\nPrice: "+v.ctx.price()+" Dunemarks\nPayment: "+(bank?"clan bank":"your Dunemarks wallet"),()->purchase(p,v.ctx,receipt,bank));
 }
 private static Set<BigDecimal> amounts(BigDecimal available){
  BigDecimal cap=available.min(new BigDecimal("1000000.00"));Set<BigDecimal> result=new LinkedHashSet<>();
  for(BigDecimal n:List.of(new BigDecimal("10.00"),new BigDecimal("100.00"),cap))if(n.signum()>0&&n.compareTo(cap)<=0)result.add(n.setScale(2));return result;
 }
 private static long total(Map<ClanStore.Grade,Long> m){long n=0;for(long v:m.values())n=Math.addExact(n,v);return n;}
 private static String grades(Map<ClanStore.Grade,Long> m){return String.join(" · ",m.entrySet().stream().filter(e->e.getValue()>0).map(e->e.getKey().label()+": "+e.getValue()).toList());}
 static List<String> lore(String json){
  if(json==null)return List.of();JsonArray a=JsonParser.parseString(json).getAsJsonArray();if(a.size()>4)throw new IllegalArgumentException("Too many lore lines");
  List<String> r=new ArrayList<>();for(JsonElement e:a){String s=e.getAsString();if(s.length()>80||s.indexOf('\u00a7')>=0||s.chars().anyMatch(c->c<32))throw new IllegalArgumentException("Invalid lore");r.add(s);}return r;
 }
 private ClanStore.Grade spiceGrade(ItemStack item){
  if(item==null||item.getType()!=Material.ORANGE_DYE||!item.hasItemMeta())return null;
  var data=item.getItemMeta().getPersistentDataContainer();if(!"spice".equals(data.get(itemKey,PersistentDataType.STRING)))return null;
  String q=data.get(qualityKey,PersistentDataType.STRING);try{return q==null?ClanStore.Grade.common:ClanStore.Grade.valueOf(q.toLowerCase(Locale.ROOT).replace(' ','_'));}catch(IllegalArgumentException e){return null;}
 }
 private EnumMap<ClanStore.Grade,Long> inventorySpice(Player p){EnumMap<ClanStore.Grade,Long> r=new EnumMap<>(ClanStore.Grade.class);for(ItemStack item:p.getInventory().getStorageContents()){var g=spiceGrade(item);if(g!=null)r.merge(g,(long)item.getAmount(),Math::addExact);}return r;}
 private void remove(Player p,Map<ClanStore.Grade,Long> plan)throws SQLException{
  var available=inventorySpice(p);for(var e:plan.entrySet())if(e.getValue()<1||available.getOrDefault(e.getKey(),0L)<e.getValue())throw new SQLException("Your spice changed. Review the amount again.");
  var left=new EnumMap<ClanStore.Grade,Long>(ClanStore.Grade.class);left.putAll(plan);
  for(int slot=0;slot<36;slot++){ItemStack item=p.getInventory().getItem(slot);var g=spiceGrade(item);if(g==null)continue;long n=Math.min(item.getAmount(),left.getOrDefault(g,0L));if(n==0)continue;ItemStack next=item.clone();next.setAmount(item.getAmount()-(int)n);p.getInventory().setItem(slot,next.getAmount()==0?null:next);left.put(g,left.get(g)-n);}
 }
 private ItemStack spice(ClanStore.Grade g,int amount){ItemStack item=new ItemStack(Material.ORANGE_DYE,amount);var m=item.getItemMeta();m.setDisplayName(ChatColor.GOLD+"Clan Treasury Spice");m.setLore(List.of(ChatColor.GRAY+"Quality: "+g.label(),ChatColor.GRAY+"Withdrawn from the clan treasury."));m.getPersistentDataContainer().set(itemKey,PersistentDataType.STRING,"spice");m.getPersistentDataContainer().set(qualityKey,PersistentDataType.STRING,g.name());String model=g==ClanStore.Grade.common?"spice_common":g==ClanStore.Grade.uncommon?"spice_smuggled":"spice_dense";m.setItemModel(new NamespacedKey("dunerealms",model));m.setCustomModelData(g==ClanStore.Grade.common?13110:g==ClanStore.Grade.uncommon?13111:13112);item.setItemMeta(m);return item;}
 private void add(Player p,List<ItemStack> items)throws SQLException{if(!p.getInventory().addItem(items.toArray(ItemStack[]::new)).isEmpty())throw new SQLException("Free four inventory slots before claiming the kit or withdrawing spice.");}
 private static final class TransactionFailure extends RuntimeException{TransactionFailure(Exception e){super(e);}}
 @FunctionalInterface interface Work{void run(Connection c)throws Exception;}
 private void transact(Player p,Work work)throws Exception{
  Method api=core.getClass().getMethod("transactNetworkInventory",Player.class,Consumer.class);
  api.invoke(core,p,(Consumer<Connection>)c->{try{work.run(c);}catch(Exception e){throw new TransactionFailure(e);}});
 }
 private void action(Player p,Work work,String success){
  if(!entryAllowed(p)||!ready||!busy.add(p.getUniqueId()))return;
  try{transact(p,c->{if(!entryAllowed(p))throw new SQLException("Your clan menu access changed.");work.run(c);});p.sendMessage(ChatColor.GREEN+success);}
  catch(Exception e){p.sendMessage(ChatColor.GOLD+safe(e));}
  finally{busy.remove(p.getUniqueId());}
  if(entryAllowed(p))open(p,"bank");
 }
 private void deposit(Player p,ClanStore.Context expected,Map<ClanStore.Grade,Long> plan,UUID id){
  action(p,c->{var ctx=store.context(c,p.getUniqueId());ctx.sameClan(expected.clan());store.operation(c,id,ctx,"deposit");remove(p,plan);store.change(c,ctx,plan,"deposit",id.toString());},"Spice deposited. Its quality is preserved.");
 }
 private void withdraw(Player p,ClanStore.Context expected,ClanStore.Grade grade,long amount,UUID id){
  action(p,c->{var ctx=store.context(c,p.getUniqueId());ctx.sameClan(expected.clan());if(!ctx.officer())throw new SQLException("Only clan owners and officers may withdraw treasury spice.");if(amount<1||amount>64)throw new SQLException("Invalid withdrawal quantity");store.operation(c,id,ctx,"withdraw");store.change(c,ctx,Map.of(grade,-amount),"withdraw",id.toString());add(p,List.of(spice(grade,(int)amount)));},"Spice withdrawn from the clan bank.");
 }
 @SuppressWarnings("unchecked") private List<ItemStack> kit(Player p,ClanStore.Context ctx)throws Exception{
  if(ctx.kitName()==null||ctx.kitName().length()>60||ctx.kitName().indexOf('\u00a7')>=0||ctx.color()==null||!ctx.color().matches("#[a-fA-F0-9]{6}"))throw new SQLException("The kit design is invalid.");
  String faction=(String)core.getClass().getMethod("factionOf",Player.class).invoke(core,p);
  List<ItemStack> base=(List<ItemStack>)core.getClass().getMethod("createFactionKit",String.class).invoke(core,faction);
  List<ItemStack> result=new ArrayList<>();for(ItemStack source:base){if(!(source.getItemMeta() instanceof LeatherArmorMeta))continue;ItemStack item=source.clone();LeatherArmorMeta m=(LeatherArmorMeta)item.getItemMeta();m.setColor(Color.fromRGB(Integer.parseInt(ctx.color().substring(1),16)));m.setDisplayName(ChatColor.GREEN+"["+ctx.tag()+"] "+ctx.kitName()+" "+item.getType().name().replace("LEATHER_","").toLowerCase(Locale.ROOT));List<String> lines=new ArrayList<>();lines.add(ChatColor.GREEN+"Quality: Uncommon");lines.add(ChatColor.GRAY+"Tailored for "+ctx.name()+".");for(String line:lore(ctx.lore()))lines.add(ChatColor.GRAY+line);m.setLore(lines);m.getPersistentDataContainer().set(qualityKey,PersistentDataType.STRING,"uncommon");m.getPersistentDataContainer().set(new NamespacedKey("dunerealms","clan-kit"),PersistentDataType.STRING,ctx.blueprint());m.getPersistentDataContainer().set(new NamespacedKey("dunerealms","clan-id"),PersistentDataType.STRING,ctx.clan());item.setItemMeta(m);result.add(item);}
  if(result.size()!=4)throw new SQLException("The kit factory did not provide four armor pieces.");return result;
 }
 private void bankTransfer(Player p,ClanStore.Context expected,BigDecimal amount,boolean withdraw,UUID id){
  action(p,c->{var ctx=store.context(c,p.getUniqueId());ctx.sameClan(expected.clan());store.operation(c,id,ctx,withdraw?"withdraw-dunemarks":"deposit-dunemarks");money.transfer(c,ctx,amount,withdraw,id);},"Dunemarks bank transfer confirmed.");
 }
 private void purchase(Player p,ClanStore.Context expected,UUID id,boolean bank){
  action(p,c->{
   var ctx=store.context(c,p.getUniqueId());ctx.kitMatches(expected);
   store.operation(c,id,ctx,"kit-purchase");List<ItemStack> items=kit(p,ctx);
   if(bank)money.purchase(c,ctx,id);else store.payDunemarks(c,ctx);money.treasuryFee(c,ctx,id);add(p,items);store.orderDunemarks(c,id,ctx,bank?"clan-bank":"wallet");
  },"Uncommon clan stillsuit kit delivered. Dunemarks payment confirmed.");
 }
}
