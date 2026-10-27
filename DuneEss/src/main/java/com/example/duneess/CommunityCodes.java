package com.example.duneess;

import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Authenticated website bridge; rewards are always credited by the UUID/month ledger. */
final class CommunityCodes implements CommandExecutor,TabCompleter {
    private final DuneEss plugin;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final Set<UUID> busy=new HashSet<>();
    private final Map<UUID,Long> cooldown=new HashMap<>();
    private final String endpoint,secret;
    private boolean syncing;
    CommunityCodes(DuneEss plugin){
        this.plugin=plugin;
        File file=new File(plugin.getDataFolder(),"website-bridge.yml");if(!file.exists())plugin.saveResource("website-bridge.yml",false);
        YamlConfiguration settings=YamlConfiguration.loadConfiguration(file);endpoint=settings.getString("origin","https://cp.dunerealms.com");secret=settings.getString("secret","");
        for(String name:List.of("communitycode","linkwebsite")){PluginCommand command=Objects.requireNonNull(plugin.getCommand(name));command.setExecutor(this);command.setTabCompleter(this);}
        Bukkit.getScheduler().runTaskTimer(plugin,this::syncReceipts,200L,1200L);
    }
    private JsonObject post(String path,JsonObject body) throws Exception {
        if(secret.length()<32||!endpoint.equals("https://cp.dunerealms.com"))throw new IllegalStateException("Website bridge is not configured");
        HttpRequest request=HttpRequest.newBuilder(URI.create(endpoint+path)).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+secret).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
        if(response.body().length()>16384)throw new IllegalStateException("Unexpected bridge response");
        JsonObject data=JsonParser.parseString(response.body()).getAsJsonObject();
        if(response.statusCode()!=200)throw new IllegalStateException(data.has("message")?data.get("message").getAsString():"Website unavailable");return data;
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player player)){sender.sendMessage(com.example.dunegui.PublicNames.text("Players only."));return true;}
        boolean linking=command.getName().equals("linkwebsite");
        if(args.length!=1){player.sendMessage(com.example.dunegui.PublicNames.text(linking?"Generate a private link command at https://cp.dunerealms.com":"Public community codes: https://www.dunerealms.com/donate.html | Personal codes: https://cp.dunerealms.com | /communitycode CODE"));return true;}
        String code=linking?args[0].toLowerCase(Locale.ROOT):args[0].toUpperCase(Locale.ROOT);
        if(!(linking?code.matches("[a-f0-9]{32}"):code.matches("[A-Z0-9-]{4,48}"))){player.sendMessage(com.example.dunegui.PublicNames.text("Invalid code format."));return true;}
        UUID uuid=player.getUniqueId();long now=System.currentTimeMillis();cooldown.entrySet().removeIf(entry->now-entry.getValue()>60000);
        if(busy.contains(uuid)||now-cooldown.getOrDefault(uuid,0L)<3000){player.sendMessage(com.example.dunegui.PublicNames.text("Please wait a few seconds before trying again."));return true;}
        cooldown.put(uuid,now);busy.add(uuid);
        JsonObject body=new JsonObject();body.addProperty("uuid",uuid.toString());body.addProperty("name",player.getName());body.addProperty("code",code);
        player.sendMessage(com.example.dunegui.PublicNames.text(linking?"Verifying website link…":"Checking community reward…"));
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{try{
            JsonObject response=post(linking?"/api/game-link/consume":"/api/community/game/prepare",body);
            Bukkit.getScheduler().runTask(plugin,()->{try{
                if(linking){player.sendMessage(com.example.dunegui.PublicNames.text("Website account linked to your Minecraft UUID. Refresh the account panel."));return;}
                if(response.has("delivered")&&response.get("delivered").getAsBoolean()){player.sendMessage("This Minecraft UUID has already received that month's reward.");return;}
                String month=response.get("month").getAsString(),receipt=response.get("receipt").getAsString();int amount=response.get("amount").getAsInt();
                boolean granted=((DuneEconomy)plugin.economy()).grantCommunity(uuid,month,amount,receipt);
                player.sendMessage(com.example.dunegui.PublicNames.text(granted?"Community reward credited: "+amount+" Dunemarks. Thank you, everyone!":"This UUID has already claimed that month's reward. No duplicate currency credited."));
                syncReceipts();
            }catch(Exception error){plugin.getLogger().warning("Community reward could not be committed: "+error.getClass().getSimpleName());player.sendMessage(com.example.dunegui.PublicNames.text("The reward could not be confirmed. Your code remains available; try again later."));}finally{busy.remove(uuid);}});
        }catch(Exception error){Bukkit.getScheduler().runTask(plugin,()->{busy.remove(uuid);player.sendMessage(com.example.dunegui.PublicNames.text("Website request failed. Check the account panel or public code page and try again later."));});}});
        return true;
    }
    private void syncReceipts(){
        if(syncing||secret.length()<32)return;
        Map<UUID,List<String>> pending;try{pending=((DuneEconomy)plugin.economy()).pendingCommunityReceipts();}catch(Exception error){return;}
        if(pending.isEmpty())return;syncing=true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{List<Map.Entry<UUID,String>> confirmed=new ArrayList<>();
            try{for(var player:pending.entrySet())for(String receipt:player.getValue()){
                JsonObject body=new JsonObject();body.addProperty("uuid",player.getKey().toString());body.addProperty("receipt",receipt);
                try{post("/api/community/game/ack",body);confirmed.add(Map.entry(player.getKey(),receipt));}catch(Exception unavailable){return;}
            }}finally{Bukkit.getScheduler().runTask(plugin,()->{try{for(var row:confirmed)((DuneEconomy)plugin.economy()).acknowledgeCommunity(row.getKey(),row.getValue());}catch(Exception error){plugin.getLogger().warning("Community receipts will retry on the next sync.");}finally{syncing=false;}});}
        });
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){return List.of();}
}
