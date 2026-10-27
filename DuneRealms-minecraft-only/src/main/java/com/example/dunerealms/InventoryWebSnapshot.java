package com.example.dunerealms;

import com.google.gson.Gson;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import java.time.Instant;
import java.util.*;

/** Safe display projection only. The web server must never deserialize Bukkit inventory blobs. */
final class InventoryWebSnapshot {
    private static final Gson JSON = new Gson();

    static String capture(Player player) {
        ItemStack[] all = player.getInventory().getContents();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", 1);
        result.put("capturedAt", Instant.now().toString());
        result.put("playerName", player.getName());
        result.put("level", player.getLevel());
        result.put("experienceProgress", player.getExp());
        result.put("selectedSlot", player.getInventory().getHeldItemSlot());
        result.put("hotbar", slots(Arrays.copyOfRange(all, 0, 9)));
        result.put("inventory", slots(Arrays.copyOfRange(all, 9, 36)));
        Map<String, Object> armor = new LinkedHashMap<>();
        armor.put("helmet", item(all[39]));
        armor.put("chestplate", item(all[38]));
        armor.put("leggings", item(all[37]));
        armor.put("boots", item(all[36]));
        result.put("armor", armor);
        result.put("offhand", item(all[40]));
        return JSON.toJson(result);
    }

    private static List<Object> slots(ItemStack[] items) {
        List<Object> result = new ArrayList<>(items.length);
        for (ItemStack stack : items) result.add(item(stack));
        return result;
    }

    static Map<String, Object> item(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("material", stack.getType().name());
        result.put("amount", stack.getAmount());
        ItemMeta meta = stack.hasItemMeta() ? stack.getItemMeta() : null;
        String name = meta != null && meta.hasDisplayName() ? ChatColor.stripColor(meta.getDisplayName())
                : stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        result.put("name", name == null ? "Item" : com.example.dunegui.PublicNames.text(name.substring(0, Math.min(name.length(), 160))));
        result.put("enchanted", meta != null && !meta.getEnchants().isEmpty());
        if (meta instanceof Damageable damage && stack.getType().getMaxDurability() > 0) {
            result.put("durability", Math.max(0, stack.getType().getMaxDurability() - damage.getDamage()));
            result.put("maxDurability", (int) stack.getType().getMaxDurability());
        }
        if (meta instanceof LeatherArmorMeta leather)
            result.put("color", String.format("#%06x", leather.getColor().asRGB()));
        return result;
    }
}
