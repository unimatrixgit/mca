package com.example.dunerealms;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.List;
import java.util.Map;

/** Managed administrator controls. The persistent tag, rather than the display name, identifies the rod. */
final class AdminGui implements Listener {
    private final DuneCore plugin;
    private final NamespacedKey rodKey;
    private final ModerationGui moderation;
    private static final Map<Integer, String> COMMANDS = Map.of(
        10, "summon sandworm", 12, "guildnpc", 14, "givespice",
        16, "guireload", 20, "kit", 22, "npcadmin", 24, "politics status");

    private static final class Menu implements InventoryHolder {
        private Inventory inventory;
        private java.util.UUID owner;
        private boolean claimed;
        @Override public Inventory getInventory() { return inventory; }
    }

    AdminGui(DuneCore plugin) {
        this.plugin = plugin;
        rodKey = new NamespacedKey(plugin, "admin_rod");
        moderation = new ModerationGui(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Also handles newly opped online players and retries when an inventory was full.
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("dune.admin")) giveRod(player);
                else if (!canUse(player) && player.getOpenInventory().getTopInventory().getHolder() instanceof Menu)
                    player.closeInventory();
            }
        }, 1L, 100L);
    }

    boolean isRod(ItemStack item) {
        return item != null && item.getType() == Material.BLAZE_ROD && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(rodKey, PersistentDataType.BYTE);
    }

    void giveRod(Player player) {
        if (!player.hasPermission("dune.admin")) return;
        for (ItemStack item : player.getInventory().getContents()) if (isRod(item)) return;
        if (player.getInventory().firstEmpty() < 0) return;
        ItemStack rod = icon(Material.BLAZE_ROD, "Imperial Admin Scepter",
            "Right-click to open the admin controls.", "Requires dune.admin.");
        ItemMeta meta = rod.getItemMeta();
        meta.getPersistentDataContainer().set(rodKey, PersistentDataType.BYTE, (byte) 1);
        meta.setEnchantmentGlintOverride(true);
        meta.setCustomModelData(1006);
        rod.setItemMeta(meta);
        player.getInventory().addItem(rod);
    }

    void open(Player player) {
        if (!canUse(player)) { player.sendMessage(ChatColor.RED + "Staff moderation permission required."); return; }
        Menu holder = new Menu();
        holder.owner = player.getUniqueId();
        holder.inventory = Bukkit.createInventory(holder, 36, ChatColor.GOLD + "Dune | Imperial Administration");
        for (int slot = 0; slot < 36; slot++) holder.inventory.setItem(slot, icon(Material.BLACK_STAINED_GLASS_PANE, " "));
        if (player.hasPermission("dune.admin")) {
        holder.inventory.setItem(10, icon(Material.SNIFFER_SPAWN_EGG, "Summon Shai-Hulud", "Spawn a sandworm at your position on Arrakis."));
        holder.inventory.setItem(12, icon(Material.VILLAGER_SPAWN_EGG, "Place Vaerkring Routekeeper", "Spawn a Guild NPC at your position."));
        holder.inventory.setItem(14, icon(Material.ORANGE_DYE, "Give Test Spice", "Receive one tagged spice item."));
        holder.inventory.setItem(16, icon(Material.COMPARATOR, "Reload Menus", "Reload gui.yml from disk."));
        holder.inventory.setItem(20, icon(Material.NETHERITE_CHESTPLATE, "Receive Faction Kit", "Receive your selected faction's kit."));
        holder.inventory.setItem(22, icon(Material.WANDERING_TRADER_SPAWN_EGG, "DuneNPC Management", "Open NPC spawning and mission status controls."));
        holder.inventory.setItem(24, icon(Material.WRITABLE_BOOK, "Imperial Status", "View politics, appointments and Trust status."));
        }
        if (ModerationGui.canBan(player) || ModerationGui.canUnban(player)) holder.inventory.setItem(26, icon(Material.SHIELD, "Ban Management", "Choose local or network scope before confirming."));
        holder.inventory.setItem(28, icon(Material.WRITABLE_BOOK, "Support Tickets", "Open My Tickets or your authorised staff ticket desk."));
        if(bankAvailable(player))holder.inventory.setItem(30,icon(Material.ENDER_CHEST,"Staff Bank","Central staff Dunemark treasury","Current bank permissions required."));
        holder.inventory.setItem(31, icon(Material.BARRIER, "Close"));
        java.util.Map<Integer,Runnable> virtualActions=new java.util.HashMap<>();
        if(player.hasPermission("dune.admin")) COMMANDS.forEach((slot,command)->virtualActions.put(slot,()->{if(player.hasPermission("dune.admin")&&canUse(player))plugin.runGuiCommand(player,command);}));
        if(ModerationGui.canBan(player)||ModerationGui.canUnban(player))virtualActions.put(26,()->{if(ModerationGui.canBan(player)||ModerationGui.canUnban(player))moderation.open(player);});
        virtualActions.put(28,()->{if(canUse(player))moderation.tickets(player);});
        if(bankAvailable(player))virtualActions.put(30,()->runStaffBank(player));
        virtualActions.put(31,player::closeInventory);
        com.example.dunegui.NativeMenus.inventory(plugin,player,"Imperial Administration",holder.inventory,virtualActions,()->canUse(player));
    }
    static boolean canUse(Player player) { return player.hasPermission("dune.admin") || ModerationGui.canBan(player) || ModerationGui.canUnban(player) || ModerationGui.canStaffTickets(player) || bankAvailable(player); }
    private static boolean bankAvailable(Player player) {
        if(!player.hasPermission("dunebank.staff.view"))return false;
        org.bukkit.plugin.Plugin bank=Bukkit.getPluginManager().getPlugin("DuneBank");
        return bank!=null&&bank.isEnabled();
    }
    private static void runStaffBank(Player player) {
        if(!player.isOnline()||!bankAvailable(player))return;
        player.closeInventory();if(!player.performCommand("dunebank staff"))player.sendMessage(ChatColor.RED+"The staff bank is unavailable. Please try again later.");
    }

    private ItemStack icon(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        meta.setLore(List.of(lore).stream().map(line -> ChatColor.GRAY + line).toList());
        if (name.equals("Support Tickets")) meta.setItemModel(new NamespacedKey("dunerealms","gui_contract"));
        else if (name.equals("Player Moderation")) meta.setItemModel(new NamespacedKey("dunerealms","gui_compass"));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler public void interact(PlayerInteractEvent event) {
        if (!isRod(event.getItem())) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
            open(event.getPlayer());
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.getUniqueId().equals(menu.owner) || menu.claimed) return;
        if (!canUse(player)) { player.closeInventory(); return; }
        if (event.getClickedInventory() != event.getView().getTopInventory() || event.getClick() != ClickType.LEFT) return;
        String command = COMMANDS.get(event.getRawSlot());
        int slot=event.getRawSlot();
        if (command == null && slot != 31 && slot != 26 && slot != 28) return;
        if (command != null && !player.hasPermission("dune.admin")) return;
        menu.claimed=true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != menu.inventory || !canUse(player)) return;
            player.closeInventory();
            if(slot==26) { moderation.open(player);return; }
            if(slot==28) { moderation.tickets(player);return; }
            if (player.hasPermission("dune.admin") && command != null) plugin.runGuiCommand(player, command);
        });
    }

    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }
}
