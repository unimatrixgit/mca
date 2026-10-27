package com.example.dunegui;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.List;

/** Presentation-only note: never a bearer token or a source of wallet credit. */
public final class DunemarkItems {
    public static final NamespacedKey MODEL = new NamespacedKey("dunerealms", "dunemark_note");
    public static final String ICON_URL = "https://www.dunerealms.com/assets/dunemark-icon-128.png";
    private DunemarkItems() {}
    public static int denomination(double balance) {
        for (int value : new int[]{5000,1000,100,5,1}) if (balance >= value) return value;
        return 1;
    }
    @SuppressWarnings("deprecation")
    public static ItemStack icon(String name, List<String> lore) {
        return icon(100,name,lore);
    }
    @SuppressWarnings("deprecation")
    public static ItemStack icon(int value, String name, List<String> lore) {
        if (value!=1 && value!=5 && value!=100 && value!=1000 && value!=5000) throw new IllegalArgumentException("Unsupported Dunemark denomination");
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(PublicNames.text(name));
        meta.setLore(lore);
        meta.setItemModel(new NamespacedKey("dunerealms", "dunemark_"+value));
        item.setItemMeta(meta);
        return item;
    }
    public static boolean isNote(ItemStack item) {
        if(item==null || !item.hasItemMeta()) return false;
        NamespacedKey key=item.getItemMeta().getItemModel();
        return key!=null && key.getNamespace().equals("dunerealms") && (key.equals(MODEL) || java.util.Set.of("dunemark_1","dunemark_5","dunemark_100","dunemark_1000","dunemark_5000").contains(key.getKey()));
    }
}
