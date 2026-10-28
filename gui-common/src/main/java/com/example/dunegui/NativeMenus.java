package com.example.dunegui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.time.Duration;
import java.util.*;


/** Shared presentation layer. All gameplay callbacks run once on the server thread. */
public final class NativeMenus {
    private NativeMenus() {}
    public record Choice(String label, String description, Runnable action, String imageUrl) {
        public Choice(String label,String description,Runnable action) { this(label,description,action,null); }
    }
    public static void inventory(JavaPlugin plugin, Player player, String title, Inventory model, Map<Integer,Runnable> actions) {
        player.openInventory(model);
    }
    public static void show(JavaPlugin plugin, Player player, String title, List<Choice> choices) {
        show(plugin,player,title,"",choices);
    }
    public static void show(JavaPlugin plugin, Player player, String title, String content, List<Choice> choices) {
        show(plugin,player,title,content,choices,List.of());
    }
    private static void show(JavaPlugin plugin, Player player, String title, String content, List<Choice> choices,List<ItemStack> illustrations) {
        MenuSession session=new MenuSession(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin,session::expire,2400L);
        List<Choice> guarded=choices.stream().map(c->new Choice(c.label(),c.description(),()-> {
            if(!session.claim(player.getUniqueId())) return;
            Bukkit.getScheduler().runTask(plugin,()-> { if(player.isOnline()) c.action().run(); });
        },c.imageUrl())).toList();
        if(Bukkit.getPluginManager().isPluginEnabled("floodgate") && Bedrock.isPlayer(player)) {
            Bedrock.show(player,title,content,guarded); return;
        }
        player.sendMessage(Component.text(title+" (select an option below)"));
        if(!content.isBlank()) player.sendMessage(Component.text(content));
        for(Choice choice:guarded) player.sendMessage(Component.text("["+choice.label()+"] "+choice.description())
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.callback(audience->{
                if(audience instanceof Player actor && actor.getUniqueId().equals(player.getUniqueId())) choice.action().run();
            },ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(2)).build())));
    }
    static int protocol(Player player) {
        if(Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
            try {
                Class<?> via=Class.forName("com.viaversion.viaversion.api.Via");
                Object api=via.getMethod("getAPI").invoke(null);
                return (int)Class.forName("com.viaversion.viaversion.api.ViaAPI")
                    .getMethod("getPlayerVersion",UUID.class).invoke(api,player.getUniqueId());
            } catch(ReflectiveOperationException | LinkageError ex) { return -1; }
        }
        return player.getProtocolVersion();
    }
    /** Loaded only when the optional Floodgate plugin is enabled. Never shades Cumulus/API classes. */
    private static final class Bedrock {
        static boolean isPlayer(Player player) { return org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId()); }
        static void show(Player player,String title,String content,List<Choice> choices) {
            var form=org.geysermc.cumulus.form.SimpleForm.builder().title(title).content(content);
            for(Choice choice:choices) {
                String label=choice.label()+ (choice.description().isBlank()?"":"\n"+choice.description());
                if(choice.imageUrl()==null)form.button(label);
                else form.button(label,org.geysermc.cumulus.util.FormImage.Type.URL,choice.imageUrl());
            }
            form.validResultHandler(response->{ int index=response.clickedButtonId(); if(index>=0 && index<choices.size()) choices.get(index).action().run(); });
            if(!org.geysermc.floodgate.api.FloodgateApi.getInstance().sendForm(player.getUniqueId(),form.build()))
                player.sendMessage("The menu could not be sent. Close your current screen and try again.");
        }
    }
}
