package com.example.duneitems;
import org.bukkit.event.block.CrafterCraftEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.plugin.Plugin;
import java.util.*;

/** Independent, cosmetic water item. Vanilla crafting retains ingredient remainders. */
public final class DuneItems extends JavaPlugin implements CommandExecutor, Listener {
    static final NamespacedKey KEY = new NamespacedKey("dunerealms", "water_vial");
    static final NamespacedKey SMUGGLER_KEY = new NamespacedKey("dunerealms", "smugglers_water_vial");
    static final int MODEL = 13101;
    private boolean registered;
    private OfficeGear officeGear;
    @Override public void onEnable() {
        ShapelessRecipe recipe = new ShapelessRecipe(KEY, waterVial(2));
        recipe.addIngredient(Material.WATER_BUCKET);
        recipe.addIngredient(Material.GLASS);
        // Never intercept CraftItemEvent: the server must own shift-craft and bucket remainders.
        if (!getServer().addRecipe(recipe)) throw new IllegalStateException("Water vial recipe key already registered");
        registered=true;
        getServer().getPluginManager().registerEvents(this,this);
        Objects.requireNonNull(getCommand("duneitems")).setExecutor(this);
        officeGear=new OfficeGear(this);officeGear.start();
    }
    @Override public void onDisable() { if(registered)getServer().removeRecipe(KEY); }
    public static ItemStack waterVial(int amount) {
        if (amount < 1 || amount > 16) throw new IllegalArgumentException("Vial stack must be 1..16");
        ItemStack item = new ItemStack(Material.POTION, amount);
        PotionMeta meta = (PotionMeta)item.getItemMeta();
        decorate(meta);
        item.setItemMeta(meta);
        return item;
    }
    public static ItemStack smugglersWaterVial(int amount) {
        ItemStack item=waterVial(amount);PotionMeta meta=(PotionMeta)item.getItemMeta();decorate(meta,true);item.setItemMeta(meta);return item;
    }
    static void decorate(PotionMeta meta) { decorate(meta,false); }
    static void decorate(PotionMeta meta, boolean smuggler) {
        meta.setBasePotionType(PotionType.WATER);
        meta.getPersistentDataContainer().set(new NamespacedKey("dunerealms","quality"),PersistentDataType.STRING,(smuggler?Quality.UNCOMMON:Quality.COMMON).id());
        meta.displayName(Component.text(smuggler?"Smugglers Vial of Water":"Vial of Water", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(Component.text("Water is the gift of life", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,true),
            Component.text("Quality: "+(smuggler?Quality.UNCOMMON:Quality.COMMON).label(),smuggler?NamedTextColor.GREEN:NamedTextColor.WHITE).decoration(TextDecoration.ITALIC,false),
            Component.text(smuggler?"Unmarked water, carried beyond the registry.":"Sealed under a licensed water seller's authority.",NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC,true)));
        meta.getPersistentDataContainer().set(smuggler?SMUGGLER_KEY:KEY, PersistentDataType.BYTE,(byte)1);
        if(smuggler)meta.getPersistentDataContainer().remove(KEY);
        meta.setItemModel(smuggler?SMUGGLER_KEY:KEY);
        meta.setCustomModelData(smuggler?13102:MODEL);
        meta.setMaxStackSize(16);
    }
    /** Fail closed; no permissions, OP status or career-name guessing can grant a license. */
    private String role(Player player) {
        Plugin core=getServer().getPluginManager().getPlugin("DuneCore");
        if(core==null || !core.isEnabled())return "none";
        try {
            Object result=core.getClass().getMethod("waterVialCraftingRole",UUID.class).invoke(core,player.getUniqueId());
            return "official".equals(result)?"official":"smuggler".equals(result)?"smuggler":"none";
        }catch(ReflectiveOperationException|RuntimeException ex){return "none";}
    }
    private boolean ours(Recipe recipe){return recipe instanceof Keyed keyed && KEY.equals(keyed.getKey());}
    ItemStack authorizedResult(Player player){return switch(role(player)){case "official"->waterVial(2);case "smuggler"->smugglersWaterVial(2);default->null;};}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void automatedCraft(CrafterCraftEvent event){if(ours(event.getRecipe()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent event){
        if(!ours(event.getRecipe()))return;
        event.getInventory().setResult(event.getView().getPlayer() instanceof Player p?authorizedResult(p):null);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void craft(CraftItemEvent event){
        if(!ours(event.getRecipe()))return;
        ItemStack expected=event.getWhoClicked() instanceof Player p?authorizedResult(p):null;
        ItemStack current=event.getCurrentItem();
        if(expected==null || current==null || current.getAmount()!=2 || !current.isSimilar(expected)){
            event.setCancelled(true);event.getInventory().setResult(expected);
        }
    }
    private void verifyRecipe() {
        World world=getServer().getWorlds().getFirst();
        // Paper1.21.4 craftItemResult misaligns cropped input remainder indexes.
        // All placements are validated through recipe matching/output; full-grid cases test native remainders.
        for(int bucket=0;bucket<9;bucket++) for(int glass:new int[]{(bucket+1)%9,(bucket+4)%9}) {
            ItemStack[] matrix=new ItemStack[9];matrix[bucket]=new ItemStack(Material.WATER_BUCKET);matrix[glass]=new ItemStack(Material.GLASS,64);
            Recipe matched=getServer().getCraftingRecipe(matrix,world);
            if(!ours(matched)||matched.getResult().getAmount()!=2||!matched.getResult().isSimilar(waterVial(2)))throw new IllegalStateException("Placement/output mismatch bucket="+bucket+" glass="+glass);
        }
        for(int[] corners:new int[][]{{0,8},{8,0},{2,6},{6,2}})verifyNativeRemainder(world,corners[0],corners[1]);
        for(Material bucket:new Material[]{Material.BUCKET,Material.LAVA_BUCKET}) {
            ItemStack[] invalid=new ItemStack[9];invalid[0]=new ItemStack(bucket);invalid[1]=new ItemStack(Material.GLASS);
            if(ours(getServer().getCraftingRecipe(invalid,world)))throw new IllegalStateException("Invalid bucket accepted");
        }
        ItemStack[] pane=new ItemStack[9];pane[0]=new ItemStack(Material.WATER_BUCKET);pane[1]=new ItemStack(Material.GLASS_PANE);
        if(ours(getServer().getCraftingRecipe(pane,world)))throw new IllegalStateException("Glass pane accepted");
    }
    private void verifyNativeRemainder(World world,int bucket,int glass){
        ItemStack[] matrix=new ItemStack[9];matrix[bucket]=new ItemStack(Material.WATER_BUCKET);matrix[glass]=new ItemStack(Material.GLASS,64);
        ItemCraftResult result=getServer().craftItemResult(matrix,world);ItemStack output=result.getResult();
        if(output==null||output.getAmount()!=2||!output.isSimilar(waterVial(2)))throw new IllegalStateException("Native output mismatch");
        int emptyBuckets=0,glassRemaining=0,fullBuckets=0;
        List<ItemStack> remainder=new ArrayList<>(Arrays.asList(result.getResultingMatrix()));remainder.addAll(result.getOverflowItems());
        for(ItemStack item:remainder)if(item!=null){if(item.getType()==Material.BUCKET)emptyBuckets+=item.getAmount();if(item.getType()==Material.WATER_BUCKET)fullBuckets+=item.getAmount();if(item.getType()==Material.GLASS)glassRemaining+=item.getAmount();}
        if(emptyBuckets!=1||fullBuckets!=0||glassRemaining!=63)throw new IllegalStateException("Ingredient remainder mismatch: grid bucket="+bucket+" glass="+glass+" emptyBuckets="+emptyBuckets+" fullBuckets="+fullBuckets+" glassRemaining="+glassRemaining+" matrix="+describe(result.getResultingMatrix())+" overflow="+describe(result.getOverflowItems().toArray(ItemStack[]::new)));
        if(ours(getServer().getCraftingRecipe(result.getResultingMatrix(),world)))throw new IllegalStateException("Empty bucket incorrectly recrafted");
    }
    private static String describe(ItemStack[] items){
        StringJoiner text=new StringJoiner(",","[","]");for(int i=0;i<items.length;i++)if(items[i]!=null&&!items[i].getType().isAir())text.add(i+":"+items[i].getType()+"x"+items[i].getAmount());return text.toString();
    }
    public void onPoliticalAuthorityChanged(){getServer().getScheduler().runTask(this,()->{for(Player p:getServer().getOnlinePlayers())if(officeGear!=null)officeGear.audit(p);});}
    public boolean isOfficeGearEligible(Player player,ItemStack item){return officeGear!=null&&officeGear.eligible(player,item);}
    static int count(String input) {
        int count;
        try { count=Integer.parseInt(input); } catch(NumberFormatException ex) { throw new IllegalArgumentException("Count must be 1..1024"); }
        if(count<1 || count>1024)throw new IllegalArgumentException("Count must be 1..1024");
        return count;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(args.length==1&&args[0].equalsIgnoreCase("offices")){
            if(sender instanceof Player player&&officeGear!=null)officeGear.menu(player);else sender.sendMessage("This menu is available in game.");return true;
        }
        if (!sender.hasPermission("duneitems.admin")) { sender.sendMessage(Component.text("You do not have permission.",NamedTextColor.RED));return true; }
        if(args.length==1 && args[0].equalsIgnoreCase("verifyrecipe")) {
            if(sender instanceof Player){sender.sendMessage("Verification is console-only.");return true;}
            try { verifyRecipe();sender.sendMessage("DuneItems recipe verification PASS:18 placement matches/output2,4 full-grid native bucket remainders,stacked glass64->63,invalid inputs."); }
            catch(RuntimeException ex){sender.sendMessage("DuneItems recipe verification FAILED: "+ex.getMessage());}
            return true;
        }
        if(args.length<3 || args.length>4 || !args[0].equalsIgnoreCase("give") || (!args[2].equals("water_vial") && !args[2].equals("smugglers_water_vial")))return false;
        Player target=getServer().getPlayerExact(args[1]);
        if(target==null){sender.sendMessage(Component.text("Player must be online.",NamedTextColor.RED));return true;}
        final int count;
        try { count=count(args.length==4?args[3]:"1"); } catch(IllegalArgumentException ex){sender.sendMessage(Component.text(ex.getMessage(),NamedTextColor.RED));return true;}
        // Preflight space without inventory mutation; no drops or partial command grants.
        boolean smuggler=args[2].equals("smugglers_water_vial");
        int capacity=0; ItemStack sample=smuggler?smugglersWaterVial(1):waterVial(1);
        for(ItemStack stack:target.getInventory().getStorageContents()) capacity+=stack==null || stack.getType().isAir()?16:stack.isSimilar(sample)?Math.max(0,16-stack.getAmount()):0;
        if(capacity<count){sender.sendMessage(Component.text("Not enough inventory space; nothing given.",NamedTextColor.RED));return true;}
        int remaining=count;
        while(remaining>0){int size=Math.min(16,remaining);target.getInventory().addItem(smuggler?smugglersWaterVial(size):waterVial(size));remaining-=size;}
        sender.sendMessage(Component.text("Gave "+count+" Vial of Water to "+target.getName()+".",NamedTextColor.AQUA));return true;
    }
}
