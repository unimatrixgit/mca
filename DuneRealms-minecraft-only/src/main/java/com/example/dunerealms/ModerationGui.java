package com.example.dunerealms;

import org.bukkit.*;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import java.io.File;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.nio.charset.StandardCharsets;

/** Explicit local/network actions use the authoritative DuneBan service. */
final class ModerationGui implements Listener {
    private static ModerationGui current;
    private static final String[] REASONS={"Repeated rule violations","Harassment or abusive behaviour","Cheating or exploiting","Temporary staff review"};
    private static final long[] DURATIONS={900000,3600000,86400000,604800000,0};
    private static final String[] DURATION_NAMES={"15 minutes","1 hour","24 hours","7 days","Permanent"};
    private final DuneCore plugin;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final Set<UUID> ticketBusy=new HashSet<>();
    private final Map<UUID,Long> ticketCooldown=new HashMap<>();
    static final class Screen implements InventoryHolder {
        final UUID owner;final BooleanSupplier allowed;final Map<Integer,Runnable> actions=new HashMap<>();
        Inventory inventory;boolean claimed;String title;
        Screen(UUID owner,BooleanSupplier allowed){this.owner=owner;this.allowed=allowed;}
        @Override public Inventory getInventory(){return inventory;}
    }
    record BanSnapshot(UUID uuid,String name,long created,String reason,Long expires,String source) {}
    ModerationGui(DuneCore plugin){this.plugin=plugin;current=this;Bukkit.getPluginManager().registerEvents(this,plugin);}
    static boolean canBan(Player p){return p.isOp()||p.hasPermission("dune.moderation.ban")||canScope(p,"local",false)||canScope(p,"global",false);}
    static boolean canUnban(Player p){return p.isOp()||p.hasPermission("dune.moderation.unban")||canScope(p,"local",true)||canScope(p,"global",true);}
    static boolean canStaffTickets(Player p){return p.isOp()||p.hasPermission("dune.tickets.staff");}
    static boolean canOwnTickets(Player p){return p.isOp()||p.hasPermission("dune.tickets.view");}
    static boolean eligibleTarget(Player actor,Player target){return target!=null&&target.isOnline()&&!actor.getUniqueId().equals(target.getUniqueId())&&!target.isOp()&&!target.hasPermission("dune.moderation.protected")&&!target.hasPermission("dune.ban.protected");}
    static void ticketsFor(DuneCore plugin,Player player){if(current!=null&&current.plugin==plugin)current.tickets(player);else player.sendMessage(ChatColor.RED+"Ticket menu is not ready. Use https://support.dunerealms.com/");}
    private Screen screen(Player p,String title,BooleanSupplier allowed){
        Screen s=new Screen(p.getUniqueId(),allowed);s.title=title;s.inventory=Bukkit.createInventory(s,54,ChatColor.GOLD+title);
        for(int i=0;i<54;i++)s.inventory.setItem(i,icon(Material.BLACK_STAINED_GLASS_PANE," "));
        return s;
    }
    private ItemStack icon(Material material,String name,String...lore){
        ItemStack i=new ItemStack(material);ItemMeta m=i.getItemMeta();m.setDisplayName(ChatColor.GOLD+name);m.setLore(Arrays.stream(lore).map(line->ChatColor.GRAY+line).toList());
        if(name.equals("Confirm ban")||name.equals("Confirm unban"))m.setItemModel(new NamespacedKey("dunerealms","gui_confirm"));
        else if(name.contains("Ticket"))m.setItemModel(new NamespacedKey("dunerealms","gui_contract"));
        else if(Set.of("Back","Previous","Next","Open Game Identity").contains(name))m.setItemModel(new NamespacedKey("dunerealms","gui_compass"));
        i.setItemMeta(m);return i;
    }
    private void button(Screen s,int slot,Material material,String title,Runnable action,String...lore){s.inventory.setItem(slot,icon(material,title,lore));s.actions.put(slot,action);}
    private void show(Player p,Screen s){if(!s.allowed.getAsBoolean()){denied(p);return;}com.example.dunegui.NativeMenus.inventory(plugin,p,s.title==null?"DuneRealms · Moderation":s.title,s.inventory,s.actions,s.allowed);}
    private void denied(Player p){p.sendMessage(ChatColor.RED+"Your current permissions do not allow this moderation action.");}
    static boolean canScope(Player p,String scope,boolean remove){return (scope.equals("local")||scope.equals("global"))&&(p.isOp()||p.hasPermission("dune."+(remove?"unban":"ban")+"."+scope));}
    static String scopeName(String scope){return scope.equals("global")?"Entire Network":"Local Server";}
    private Plugin banService(){Plugin service=Bukkit.getPluginManager().getPlugin("DuneBan");return service!=null&&service.isEnabled()?service:null;}
    void open(Player p){
        if(!canBan(p)&&!canUnban(p)){denied(p);return;}
        Screen s=screen(p,"Player Moderation",()->canBan(p)||canUnban(p));
        s.inventory.setItem(4,icon(Material.SHIELD,"Choose the scope explicitly","Local affects this server only","Global blocks the entire network"));
        if(canBan(p))button(s,20,Material.IRON_AXE,"Ban a player",()->chooseScope(p,false),"Choose Local Server or Entire Network first");
        if(canUnban(p))button(s,24,Material.LIME_DYE,"Review / remove bans",()->chooseScope(p,true),"Review the exact record in the selected scope");
        button(s,49,Material.WRITABLE_BOOK,"Tickets",()->tickets(p),"Open your canonical Support Center links");
        button(s,53,Material.BARRIER,"Close",p::closeInventory);show(p,s);
    }
    private void chooseScope(Player p,boolean remove){
        Screen s=screen(p,remove?"Choose Unban Scope":"Choose Ban Scope",()->canBan(p)||canUnban(p));
        if(banService()==null)s.inventory.setItem(22,icon(Material.BARRIER,"Moderation service unavailable","No local or global action will be guessed"));
        else for(String scope:List.of("local","global"))if(canScope(p,scope,remove))button(s,scope.equals("local")?20:24,scope.equals("local")?Material.GRASS_BLOCK:Material.BEACON,scopeName(scope),()->{if(remove)bans(p,0,scope);else players(p,0,scope);},scope.equals("local")?"Only this game server":"All servers and network login",remove?"Load current records before removal":"Reason and duration are reviewed next");
        button(s,49,Material.ARROW,"Back",()->open(p));show(p,s);
    }
    private void players(Player p,int requested,String scope){
        List<? extends Player> targets=Bukkit.getOnlinePlayers().stream().filter(target->eligibleTarget(p,target)).sorted(Comparator.comparing(Player::getName,String.CASE_INSENSITIVE_ORDER)).toList();
        int page=page(requested,targets.size()),start=page*28;Screen s=screen(p,scopeName(scope)+" · Player",()->canScope(p,scope,false));
        for(int n=start;n<Math.min(start+28,targets.size());n++){Player target=targets.get(n);UUID id=target.getUniqueId();button(s,card(n-start),Material.PLAYER_HEAD,target.getName(),()->configure(p,id,0,2,scope),"UUID: "+id,"Scope: "+scopeName(scope));}
        if(targets.isEmpty())s.inventory.setItem(22,icon(Material.PAPER,"No eligible online players","Yourself, operators and protected players are excluded"));
        if(page>0)button(s,45,Material.ARROW,"Previous",()->players(p,page-1,scope));if(start+28<targets.size())button(s,53,Material.ARROW,"Next",()->players(p,page+1,scope));
        button(s,49,Material.ARROW,"Back",()->chooseScope(p,false));show(p,s);
    }
    private void configure(Player p,UUID targetId,int reason,int duration,String scope){
        Player target=Bukkit.getPlayer(targetId);if(!eligibleTarget(p,target)){p.sendMessage(ChatColor.RED+"That player is no longer eligible. Refresh the player list.");players(p,0,scope);return;}
        Screen s=screen(p,"Review "+scopeName(scope)+" Ban",()->canScope(p,scope,false));
        s.inventory.setItem(4,icon(Material.PLAYER_HEAD,target.getName(),"UUID: "+targetId,"Scope: "+scopeName(scope)));
        for(int n=0;n<REASONS.length;n++){int index=n;button(s,19+n,Material.PAPER,(n==reason?"✓ ":"")+REASONS[n],()->configure(p,targetId,index,duration,scope),"Choose a recorded reason");}
        for(int n=0;n<DURATIONS.length;n++){int index=n;button(s,28+n,Material.CLOCK,(n==duration?"✓ ":"")+DURATION_NAMES[n],()->configure(p,targetId,reason,index,scope),"Choose ban duration");}
        button(s,48,Material.LIME_DYE,"Review confirmation",()->confirmBan(p,targetId,reason,duration,scope),"Scope: "+scopeName(scope),"Reason: "+REASONS[reason],"Duration: "+DURATION_NAMES[duration]);
        button(s,50,Material.ARROW,"Back",()->players(p,0,scope));show(p,s);
    }
    private void confirmBan(Player p,UUID target,int reason,int duration,String scope){
        Screen s=screen(p,"Confirm "+scopeName(scope)+" Ban",()->canScope(p,scope,false));
        s.inventory.setItem(22,icon(Material.PAPER,"Decision summary","Scope: "+scopeName(scope),"Target UUID: "+target,"Reason: "+REASONS[reason],"Duration: "+DURATION_NAMES[duration],"Current target and permission are rechecked"));
        button(s,30,Material.RED_CONCRETE,"Confirm ban",()->requestBan(p,target,reason,duration,scope));
        button(s,32,Material.ARROW,"Cancel",()->configure(p,target,reason,duration,scope));show(p,s);
    }
    static CompletableFuture<?> scopedCall(Object service,Player actor,String scope,UUID target,String reason,long duration,String created,boolean remove){
        if(service==null||!actor.isOnline()||!canScope(actor,scope,remove))return CompletableFuture.failedFuture(new IllegalStateException("Moderation service or current permission unavailable"));
        if(!remove&&(!Arrays.asList(REASONS).contains(reason)||Arrays.stream(DURATIONS).noneMatch(value->value==duration)||!eligibleTarget(actor,Bukkit.getPlayer(target))))return CompletableFuture.failedFuture(new IllegalStateException("Current target is no longer eligible"));
        try{Object result=remove?service.getClass().getMethod("unbanScoped",Player.class,UUID.class,String.class,String.class,String.class).invoke(service,actor,target,created,reason,scope):service.getClass().getMethod("banScoped",Player.class,UUID.class,String.class,Duration.class,String.class).invoke(service,actor,target,reason,duration==0?null:Duration.ofMillis(duration),scope);if(result instanceof CompletableFuture<?> future)return future;}catch(Exception unavailable){}
        return CompletableFuture.failedFuture(new IllegalStateException("Moderation service did not accept the request"));
    }
    static boolean scopeResultCurrent(Player p,UUID owner,Inventory loading,String scope,boolean remove){return p.isOnline()&&owner.equals(p.getUniqueId())&&canScope(p,scope,remove)&&com.example.dunegui.NativeMenus.isCurrent(p,loading);}
    private Screen loading(Player p,String scope,boolean remove,String title){Screen s=screen(p,title,()->canScope(p,scope,remove));s.inventory.setItem(22,icon(Material.CLOCK,"Checking "+scopeName(scope),"No success is shown before authoritative confirmation"));button(s,49,Material.ARROW,"Back",()->chooseScope(p,remove));show(p,s);return s;}
    private void completeAction(Player p,Screen loading,String scope,boolean remove,CompletableFuture<?> future){UUID owner=p.getUniqueId();future.whenComplete((result,error)->{if(!plugin.isEnabled())return;Bukkit.getScheduler().runTask(plugin,()->{if(!scopeResultCurrent(p,owner,loading.inventory,scope,remove))return;boolean ok=false;String message="Moderation action could not be confirmed. Refresh before retrying.";if(error==null&&result!=null)try{ok=Boolean.TRUE.equals(result.getClass().getMethod("ok").invoke(result));Object value=result.getClass().getMethod("message").invoke(result);if(value instanceof String text&&text.length()<=600&&!text.contains("\n"))message=text;}catch(Exception ignored){}p.sendMessage((ok?ChatColor.GREEN:ChatColor.RED)+scopeName(scope)+": "+message);open(p);});});}
    private void requestBan(Player p,UUID target,int reason,int duration,String scope){Plugin service=banService();Screen waiting=loading(p,scope,false,"Applying "+scopeName(scope)+" Ban");completeAction(p,waiting,scope,false,scopedCall(service,p,scope,target,REASONS[reason],DURATIONS[duration],null,false));}
    static boolean applyBan(Player actor,UUID targetId,String reason,long duration){
        if(!canBan(actor)||!Arrays.asList(REASONS).contains(reason)||Arrays.stream(DURATIONS).noneMatch(value->value==duration))return false;
        Player target=Bukkit.getPlayer(targetId);
        if(!eligibleTarget(actor,target)){actor.sendMessage(ChatColor.RED+"Ban cancelled: target left, is protected, or is no longer eligible.");return false;}
        ProfileBanList list=Bukkit.getBanList(BanList.Type.PROFILE);PlayerProfile profile=target.getPlayerProfile();
        if(list.isBanned(profile)){actor.sendMessage(ChatColor.RED+"This player is already banned. Review the existing record first.");return false;}
        Date expiry=duration==0?null:new Date(Math.addExact(System.currentTimeMillis(),duration));
        if(list.addBan(profile,reason,expiry,actor.getName())==null||!list.isBanned(profile)){actor.sendMessage(ChatColor.RED+"The ban could not be confirmed. No success was recorded.");return false;}
        target.kickPlayer("Ban: "+reason+"\nAppeal at https://support.dunerealms.com/appeal.php");
        Bukkit.getLogger().info("Dune moderation: "+actor.getName()+" banned UUID "+targetId+"; reason="+reason+"; durationMillis="+duration);
        actor.sendMessage(ChatColor.GREEN+"Local ban confirmed. The ban register will refresh from this server's records.");return true;
    }
    static List<BanSnapshot> snapshots(ProfileBanList list){
        List<BanSnapshot> result=new ArrayList<>();
        for(BanEntry<?> entry:list.getBanEntries())if(entry.getBanTarget() instanceof PlayerProfile profile&&profile.getUniqueId()!=null){
            result.add(new BanSnapshot(profile.getUniqueId(),profile.getName()==null?profile.getUniqueId().toString():profile.getName(),entry.getCreated()==null?0:entry.getCreated().getTime(),entry.getReason(),entry.getExpiration()==null?null:entry.getExpiration().getTime(),entry.getSource()));
        }
        return result.stream().sorted(Comparator.comparing(BanSnapshot::name,String.CASE_INSENSITIVE_ORDER).thenComparing(BanSnapshot::uuid)).toList();
    }
    record ScopeBan(UUID uuid,String name,String reason,String created,String expires,String source){}
    static List<ScopeBan> scopeRows(Object value)throws Exception {if(!(value instanceof List<?> list)||list.size()>5000)throw new IllegalArgumentException("Invalid ban list");List<ScopeBan> rows=new ArrayList<>();for(Object item:list){UUID id=(UUID)item.getClass().getMethod("uuid").invoke(item);String name=Objects.toString(item.getClass().getMethod("name").invoke(item),""),reason=Objects.toString(item.getClass().getMethod("reason").invoke(item),""),created=(String)item.getClass().getMethod("created").invoke(item),expires=(String)item.getClass().getMethod("expires").invoke(item),source=Objects.toString(item.getClass().getMethod("source").invoke(item),"");if(id==null||name.length()>40||reason.length()>2000||source.length()>120||created.length()>100||expires.length()>100)throw new IllegalArgumentException("Invalid ban card");Instant.parse(created);if(!expires.equals("forever"))Instant.parse(expires);rows.add(new ScopeBan(id,name,reason,created,expires,source));}return rows;}
    private void bans(Player p,int requested,String scope){
        Plugin service=banService();Screen waiting=loading(p,scope,true,"Loading "+scopeName(scope)+" Bans");UUID owner=p.getUniqueId();CompletableFuture<?> future;
        try{if(service==null||!canScope(p,scope,true))throw new IllegalStateException();Object response=service.getClass().getMethod("listBans",Player.class,String.class).invoke(service,p,scope);if(!(response instanceof CompletableFuture<?> result))throw new IllegalStateException();future=result;}catch(Exception unavailable){p.sendMessage(ChatColor.RED+"Current ban records could not be verified.");chooseScope(p,true);return;}
        future.whenComplete((response,error)->{if(!plugin.isEnabled())return;Bukkit.getScheduler().runTask(plugin,()->{if(!scopeResultCurrent(p,owner,waiting.inventory,scope,true))return;try{if(error!=null)throw new IllegalStateException();renderBans(p,requested,scope,scopeRows(response));}catch(Exception unavailable){p.sendMessage(ChatColor.RED+"Current ban records could not be verified.");chooseScope(p,true);}});});
    }
    private void renderBans(Player p,int requested,String scope,List<ScopeBan> rows){int page=page(requested,rows.size()),start=page*28;Screen s=screen(p,scopeName(scope)+" Bans · "+(page+1),()->canScope(p,scope,true));
        for(int n=start;n<Math.min(start+28,rows.size());n++){ScopeBan ban=rows.get(n);button(s,card(n-start),Material.PLAYER_HEAD,ban.name(),()->confirmUnban(p,ban,scope),"Scope: "+scopeName(scope),"UUID: "+ban.uuid(),"Reason: "+ban.reason(),"Created: "+ban.created(),"Expires: "+ban.expires());}
        if(rows.isEmpty())s.inventory.setItem(22,icon(Material.PAPER,"No current bans",scopeName(scope)));
        if(page>0)button(s,45,Material.ARROW,"Previous",()->bans(p,page-1,scope));if(start+28<rows.size())button(s,53,Material.ARROW,"Next",()->bans(p,page+1,scope));button(s,49,Material.ARROW,"Back",()->chooseScope(p,true));show(p,s);
    }
    private void confirmUnban(Player p,ScopeBan ban,String scope){Screen s=screen(p,"Confirm "+scopeName(scope)+" Unban",()->canScope(p,scope,true));s.inventory.setItem(22,icon(Material.PAPER,ban.name(),"Scope: "+scopeName(scope),"UUID: "+ban.uuid(),"Reason: "+ban.reason(),"Created: "+ban.created(),"A changed ban will not be removed"));button(s,30,Material.LIME_CONCRETE,"Confirm unban",()->{Plugin service=banService();Screen waiting=loading(p,scope,true,"Removing "+scopeName(scope)+" Ban");completeAction(p,waiting,scope,true,scopedCall(service,p,scope,ban.uuid(),"Reviewed removal from staff GUI",0,ban.created(),true));});button(s,32,Material.ARROW,"Cancel",()->bans(p,0,scope));show(p,s);}
    static boolean removeBan(Player actor,BanSnapshot expected){
        if(!canUnban(actor))return false;
        ProfileBanList list=Bukkit.getBanList(BanList.Type.PROFILE);BanSnapshot actual=snapshots(list).stream().filter(b->b.uuid().equals(expected.uuid())).findFirst().orElse(null);
        if(!expected.equals(actual)){actor.sendMessage(ChatColor.RED+"The ban changed or was already removed. Refresh before acting.");return false;}
        PlayerProfile profile=Bukkit.createPlayerProfile(expected.uuid(),expected.name());list.pardon(profile);
        if(list.isBanned(profile)){actor.sendMessage(ChatColor.RED+"Ban removal was not confirmed. Refresh before retrying.");return false;}
        Bukkit.getLogger().info("Dune moderation: "+actor.getName()+" removed local ban UUID "+expected.uuid());actor.sendMessage(ChatColor.GREEN+"Local ban removed and confirmed.");return true;
    }
    void tickets(Player p){
        Screen s=screen(p,"Support Tickets",()->canOwnTickets(p)||canStaffTickets(p));
        if(canOwnTickets(p)){
        button(s,20,Material.WRITABLE_BOOK,"My Tickets",()->loadTickets(p,false,0),"Read current ticket status and department","Only your verified Minecraft account's tickets");
        button(s,24,Material.PAPER,"Open Support Request",()->{if(!canOwnTickets(p)){denied(p);return;}p.closeInventory();p.sendMessage(Component.text("Open a support ticket →").clickEvent(ClickEvent.openUrl("https://support.dunerealms.com/open.php")));},"Create a ticket in the canonical Support Center");
        }
        if(canStaffTickets(p))button(s,31,Material.SHIELD,"Staff Ticket Desk",()->loadTickets(p,true,0),"Read currently authorised staff ticket cards","Website department permissions are checked on every load");
        button(s,49,Material.BARRIER,"Close",p::closeInventory);show(p,s);
    }
    private void loadTickets(Player p,boolean staff,int page){
        if(staff?!canStaffTickets(p):!canOwnTickets(p)){denied(p);return;}
        UUID uuid=p.getUniqueId();long now=System.currentTimeMillis();ticketCooldown.entrySet().removeIf(entry->now-entry.getValue()>60000);
        if(ticketBusy.contains(uuid)||now-ticketCooldown.getOrDefault(uuid,0L)<3000){p.sendMessage(ChatColor.YELLOW+"Please wait a few seconds before refreshing tickets.");tickets(p);return;}
        Plugin ess=Bukkit.getPluginManager().getPlugin("DuneEss");
        if(ess==null||!ess.isEnabled()){p.sendMessage(ChatColor.RED+"The verified website bridge is unavailable.");tickets(p);return;}
        YamlConfiguration settings=YamlConfiguration.loadConfiguration(new File(ess.getDataFolder(),"website-bridge.yml"));String origin=settings.getString("origin",""),secret=settings.getString("secret","");
        if(!origin.equals("https://cp.dunerealms.com")||secret.length()<32){p.sendMessage(ChatColor.RED+"The verified website bridge is not configured.");tickets(p);return;}
        Screen loading=screen(p,"Loading Tickets",()->staff?canStaffTickets(p):canOwnTickets(p));loading.inventory.setItem(22,icon(Material.CLOCK,"Checking current tickets","Only bounded ticket metadata is loaded"));button(loading,49,Material.ARROW,"Back",()->tickets(p));show(p,loading);
        ticketBusy.add(uuid);ticketCooldown.put(uuid,now);
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
            TicketCards.Page data=null;
            try{
                HttpRequest request=HttpRequest.newBuilder(URI.create(origin+"/api/game/tickets")).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+secret).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(TicketCards.request(uuid,staff))).build();
                HttpResponse<byte[]> response=http.sendAsync(request,info->new TicketCards.LimitedBody()).orTimeout(10,java.util.concurrent.TimeUnit.SECONDS).join();
                if(response.statusCode()!=200)throw new IllegalStateException("Ticket request unavailable");data=TicketCards.parse(new String(response.body(),StandardCharsets.UTF_8),staff);
            }catch(Exception unavailable){/* Never log bridge credentials, response bodies or private ticket data. */}
            TicketCards.Page result=data;
            if(!plugin.isEnabled())return;
            Bukkit.getScheduler().runTask(plugin,()->{
                ticketBusy.remove(uuid);
                if(!ticketResultCurrent(p,uuid,loading.inventory,staff))return;
                if(result==null){p.sendMessage(ChatColor.RED+"Tickets could not be verified. Check your website account or retry shortly.");tickets(p);return;}
                ticketCards(p,staff,page,result);
            });
        });
    }
    static boolean ticketResultCurrent(Player player,UUID owner,Inventory loading,boolean staff){
        return player.isOnline()&&owner.equals(player.getUniqueId())&&com.example.dunegui.NativeMenus.isCurrent(player,loading)&&(staff?canStaffTickets(player):canOwnTickets(player));
    }
    private void ticketCards(Player p,boolean staff,int requested,TicketCards.Page data){
        int page=Math.max(0,Math.min(requested,Math.max(0,(data.cards().size()-1)/21))),start=page*21,end=Math.min(start+21,data.cards().size());
        Screen s=screen(p,staff?"Staff Tickets":"My Tickets",()->staff?canStaffTickets(p):canOwnTickets(p));
        s.inventory.setItem(4,icon(Material.WRITABLE_BOOK,staff?"Staff Tickets":"My Tickets","Current records from the Support Center","Read-only cards · Page "+(page+1)));
        for(int i=start;i<end;i++){
            TicketCards.Card ticket=data.cards().get(i);List<String> lore=new ArrayList<>();for(int n=0;n<ticket.subject().length();n+=36)lore.add(ticket.subject().substring(n,Math.min(n+36,ticket.subject().length())));lore.add("Status: "+ticket.status());lore.add("Department: "+ticket.department());if(!ticket.appealStatus().isBlank())lore.add("Appeal: "+ticket.appealStatus());lore.add("Click to open the exact native ticket");
            int slot=10+((i-start)/7)*9+(i-start)%7;
            button(s,slot,Material.PAPER,"Ticket #"+ticket.number(),()->{if(staff?!canStaffTickets(p):!canOwnTickets(p)){denied(p);return;}p.closeInventory();p.sendMessage(Component.text("Open ticket #"+ticket.number()+" →").clickEvent(ClickEvent.openUrl(ticket.url())));},lore.toArray(String[]::new));
        }
        if(!data.linked()){
            s.inventory.setItem(22,icon(Material.PAPER,"Link your Minecraft account","No ticket records were returned","Verify your UUID in Account Center first"));
            button(s,24,Material.COMPASS,"Open Game Identity",()->{p.closeInventory();p.sendMessage(Component.text("Link Minecraft account →").clickEvent(ClickEvent.openUrl("https://cp.dunerealms.com/#game")));});
        }else if(data.cards().isEmpty())s.inventory.setItem(22,icon(Material.PAPER,"No current tickets","Only records authorised for this view are returned"));
        if(page>0)button(s,45,Material.ARROW,"Previous",()->loadTickets(p,staff,page-1));if(end<data.cards().size())button(s,53,Material.ARROW,"Next",()->loadTickets(p,staff,page+1));
        button(s,48,Material.CLOCK,"Refresh Tickets",()->loadTickets(p,staff,page));button(s,49,Material.ARROW,"Back",()->tickets(p));button(s,50,Material.WRITABLE_BOOK,"Open Support Center",()->sendLink(p,staff));show(p,s);
        Bukkit.getScheduler().runTaskLater(plugin,()->{if(p.isOnline()&&com.example.dunegui.NativeMenus.isCurrent(p,s.inventory)){p.closeInventory();p.sendMessage(ChatColor.GRAY+"Ticket view expired. Open it again for current permissions and status.");}},600L);
    }
    private void sendLink(Player p,boolean staff){
        if(staff?!canStaffTickets(p):!canOwnTickets(p)){denied(p);return;}
        p.closeInventory();String url=staff?"https://support.dunerealms.com/dune-sso.php?staff=1":"https://support.dunerealms.com/tickets.php";
        p.sendMessage(Component.text(staff?"Open staff ticket desk →":"Open My Tickets →").clickEvent(ClickEvent.openUrl(url)));
        p.sendMessage(ChatColor.GRAY+"Tickets remain in osTicket. No private ticket data is copied into game chat.");
    }
    static int page(int requested,int count){return Math.max(0,Math.min(requested,Math.max(0,(count-1)/28)));}
    private static int card(int index){return 10+(index/7)*9+index%7;}
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof Screen s))return;e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(s.owner)||s.claimed||e.getClickedInventory()!=s.inventory||e.getClick()!=ClickType.LEFT)return;
        if(!s.allowed.getAsBoolean()){p.closeInventory();denied(p);return;}
        Runnable action=s.actions.get(e.getRawSlot());if(action==null)return;s.claimed=true;
        Bukkit.getScheduler().runTask(plugin,()->{if(!p.isOnline()||!com.example.dunegui.NativeMenus.isCurrent(p,s.inventory)||!s.allowed.getAsBoolean())return;p.closeInventory();try{action.run();}catch(RuntimeException failure){p.sendMessage(ChatColor.RED+"The action could not be confirmed. Refresh before retrying.");plugin.getLogger().warning("Moderation GUI action failed for actor UUID "+p.getUniqueId());}});
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Screen)e.setCancelled(true);}
}


