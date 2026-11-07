package com.dunerealms.heraldry;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class DuneHeraldry extends JavaPlugin implements TabCompleter {
    static final List<String> FACTIONS = List.of("atreides", "corrino", "harkonnen", "fremen", "ordos");
    static final int KIT_SIZE = 8;
    private final Map<String, ItemStack> banners = new LinkedHashMap<>();

    @Override public void onEnable() {
        try {
            for (String faction : FACTIONS) banners.put(faction, create(faction));
            verify();
            Objects.requireNonNull(getCommand("staffkit")).setTabCompleter(this);
            getLogger().info("HERALDRY_READY factions=5 kitSize=8; no items issued automatically.");
        } catch (RuntimeException missing) {
            getLogger().severe("Faction banner datapack unavailable or invalid: " + missing.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private ItemStack create(String faction) {
        PatternType pattern = Objects.requireNonNull(Registry.BANNER_PATTERN.get(new NamespacedKey("dunerealms", faction)), faction);
        ItemStack item = new ItemStack(Material.BLACK_BANNER);
        BannerMeta meta = (BannerMeta) item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + title(faction) + " Standard");
        meta.setLore(List.of(ChatColor.GRAY + "DuneRealms · Faction Heraldry", ChatColor.DARK_GRAY + "Staff construction kit", ChatColor.DARK_GRAY + "Place on a wall or on the ground."));
        meta.setPatterns(List.of(new Pattern(DyeColor.WHITE, pattern)));
        meta.getPersistentDataContainer().set(new NamespacedKey(this, "faction"), PersistentDataType.STRING, faction);
        item.setItemMeta(meta);
        return item;
    }

    private void verify() {
        for (var entry : banners.entrySet()) {
            ItemStack restored = ItemStack.deserializeBytes(entry.getValue().serializeAsBytes());
            if (!restored.isSimilar(entry.getValue())) throw new IllegalStateException("Item roundtrip: " + entry.getKey());
            BannerMeta meta = (BannerMeta) restored.getItemMeta();
            if (meta.numberOfPatterns() != 1 || !new NamespacedKey("dunerealms",entry.getKey()).equals(Registry.BANNER_PATTERN.getKey(meta.getPattern(0).getPattern())))
                throw new IllegalStateException("Pattern roundtrip: " + entry.getKey());
        }
        List<ItemStack> all = banners.values().stream().map(i -> { ItemStack copy=i.clone(); copy.setAmount(KIT_SIZE); return copy; }).toList();
        ItemStack[] full = new ItemStack[36]; Arrays.setAll(full, n -> new ItemStack(Material.STONE,64));
        if (fits(full,all) || !fits(new ItemStack[36],all)) throw new IllegalStateException("Capacity guard");
        ItemStack[] four = full.clone(); for (int i=0;i<4;i++) four[i]=null;
        if (fits(four,all)) throw new IllegalStateException("Partial kit would overflow");
        ItemStack[] shared = full.clone(); shared[0]=all.get(0).clone(); shared[0].setAmount(8);
        if (!fits(shared,List.of(all.get(0))) || shared[0].getAmount()!=8 || full[0].getAmount()!=64)
            throw new IllegalStateException("Capacity simulation must not mutate inventory");
        getLogger().info("HERALDRY_VERIFY registry=5 nativeItemRoundtrips=5 capacityChecks=4 no inventories changed");
        getLogger().info("HERALDRY_PACK hash=" + getServer().getResourcePackHash() + " url=" + getServer().getResourcePack());
    }

    private static String title(String faction) { return faction.substring(0, 1).toUpperCase(Locale.ROOT) + faction.substring(1); }

    // Check the complete storage inventory before delivery. Never overwrite slots or drop overflow.
    static boolean fits(ItemStack[] original, List<ItemStack> incoming) {
        ItemStack[] storage = Arrays.stream(original).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
        for (ItemStack wanted : incoming) {
            int left = wanted.getAmount();
            for (int i = 0; i < storage.length && left > 0; i++) {
                ItemStack at = storage[i];
                if (at != null && !at.getType().isAir() && at.isSimilar(wanted)) {
                    int moved = Math.min(left, Math.max(0, at.getMaxStackSize() - at.getAmount()));
                    at.setAmount(at.getAmount() + moved); left -= moved;
                }
            }
            for (int i = 0; i < storage.length && left > 0; i++) {
                if (storage[i] == null || storage[i].getType().isAir()) {
                    int moved = Math.min(left, wanted.getMaxStackSize());
                    storage[i] = wanted.clone(); storage[i].setAmount(moved); left -= moved;
                }
            }
            if (left > 0) return false;
        }
        return true;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("dune.staffkit.banners")) { sender.sendMessage("This building kit is available to authorised staff only."); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("verify") && sender instanceof ConsoleCommandSender) { verify(); return true; }
        Player target;
        String requested;
        if (sender instanceof Player player) {
            if (args.length < 1 || args.length > 2 || !args[0].equalsIgnoreCase("banners")) return false;
            target = player; requested = args.length == 2 ? args[1] : "all";
        } else if (sender instanceof ConsoleCommandSender && args.length == 3 && args[0].equalsIgnoreCase("give")) {
            target = getServer().getPlayerExact(args[1]); requested = args[2];
        } else return false;
        if (target == null || !target.isOnline()) { sender.sendMessage("An exact online player is required; nothing issued."); return true; }
        requested = requested.toLowerCase(Locale.ROOT);
        if (!requested.equals("all") && !FACTIONS.contains(requested)) return false;
        List<ItemStack> delivery = new ArrayList<>();
        for (var entry : banners.entrySet()) if (requested.equals("all") || entry.getKey().equals(requested)) {
            ItemStack item = entry.getValue().clone(); item.setAmount(KIT_SIZE); delivery.add(item);
        }
        if (!fits(target.getInventory().getStorageContents(), delivery)) { sender.sendMessage("Please make room for the complete banner kit first. Nothing issued."); return true; }
        for (ItemStack item : delivery) if (!target.getInventory().addItem(item).isEmpty()) throw new IllegalStateException("Unexpected banner inventory change on main thread");
        target.sendMessage(ChatColor.GOLD + "Faction standards received: " + (requested.equals("all") ? "all five factions" : title(requested)) + ". Eight of each; place normally to build.");
        getLogger().info("HERALDRY_ISSUED actor=" + sender.getName() + " recipient=" + target.getUniqueId() + " faction=" + requested + " quantityEach=8");
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("dune.staffkit.banners")) return List.of();
        List<String> choices = args.length == 1 ? List.of("banners") : args.length == 2 && args[0].equalsIgnoreCase("banners") ? List.of("all", "atreides", "corrino", "harkonnen", "fremen", "ordos") : List.of();
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(s -> s.startsWith(prefix)).toList();
    }
}
