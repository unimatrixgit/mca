package com.example.duneterrain;

import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.event.*;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.generator.*;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/** Arrakis temple adaptation: stepped roof, upper chamber, stair and buried vault. */
final class HarkonnenFortress extends BlockPopulator implements Listener {
    static final NamespacedKey LOOT=new NamespacedKey("dunerealms","chests/old-harkonnen-fortress");
    private final DuneTerrain plugin;
    HarkonnenFortress(DuneTerrain plugin) { this.plugin=plugin; }
    static boolean attempt(long seed,int cx,int cz,int rarity) {
        return Math.floorMod(DenseSpice.mix(seed^cx*0x9e3779b97f4a7c15L^cz*0xc2b2ae3d27d4eb4fL^0x4841524b4f4e4e45L),Math.max(1,rarity))==0;
    }
    @Override public void populate(WorldInfo world,Random random,int cx,int cz,LimitedRegion r) {
        if(!world.getName().equalsIgnoreCase("arrakis")||!plugin.getConfig().getBoolean("harkonnen-fortress.enabled",true)
            || !attempt(world.getSeed(),cx,cz,plugin.getConfig().getInt("harkonnen-fortress.rarity",1000)))return;
        int x=cx*16+8,z=cz*16+8,y=r.getHighestBlockYAt(x,z);
        if(y-7<=world.getMinHeight()||y+10>=world.getMaxHeight())return;
        for(int dx:new int[]{-7,7})for(int dz:new int[]{-8,8})
            if(!r.isInRegion(x+dx,y,z+dz)||Math.abs(r.getHighestBlockYAt(x+dx,z+dz)-y)>6)return;
        // Jungle-temple proportions translated into sandstone and Harkonnen dark stone.
        for(int dx=-6;dx<=6;dx++)for(int dz=-7;dz<=7;dz++)for(int dy=-6;dy<=6;dy++) {
            boolean shell=dy==-6||dy==0||dy==6||Math.abs(dx)==6||Math.abs(dz)==7;
            Material m=shell?((dx+dz+dy)%5==0?Material.CRACKED_POLISHED_BLACKSTONE_BRICKS:Material.SANDSTONE):Material.AIR;
            r.setType(x+dx,y+dy,z+dz,m);
        }
        for(int level=0;level<3;level++)for(int dx=-5+level;dx<=5-level;dx++)for(int dz=-6+level;dz<=6-level;dz++)
            r.setType(x+dx,y+7+level,z+dz,Material.CUT_SANDSTONE);
        for(int dx:new int[]{-5,5})for(int dz:new int[]{-6,6})for(int dy=1;dy<=5;dy++)r.setType(x+dx,y+dy,z+dz,Material.POLISHED_BLACKSTONE_BRICKS);
        for(int dy=1;dy<=3;dy++)r.setType(x,y+dy,z-7,Material.AIR);
        for(int step=0;step<6;step++) {
            r.setType(x-3,y-step,z+4-step,Material.SANDSTONE);
            for(int clear=1;clear<=3;clear++)r.setType(x-3,y-step+clear,z+4-step,Material.AIR);
        }
        r.setType(x+3,y-5,z+3,Material.CHEST);
        if(r.getBlockState(x+3,y-5,z+3) instanceof Chest chest) {
            chest.setCustomName("Old Harkonnen Fortress");
            var table=Bukkit.getLootTable(LOOT);
            if(table!=null){chest.setLootTable(table);chest.setSeed(DenseSpice.mix(world.getSeed()^cx^((long)cz<<32)));}
            else for(ItemStack item:kit())chest.getSnapshotInventory().addItem(item);
            chest.update(true,false);
        }
        // Ruined red banner and lanterns, no moss, jungle trees, farms or hay.
        r.setType(x,y+4,z-8,Material.RED_WALL_BANNER);
        var banner=Material.RED_WALL_BANNER.createBlockData();
        ((org.bukkit.block.data.Directional)banner).setFacing(org.bukkit.block.BlockFace.NORTH);
        r.setBlockData(x,y+4,z-8,banner);
        r.setType(x+3,y-2,z+3,Material.LANTERN);
    }
    @SuppressWarnings("unchecked") private List<ItemStack> kit() {
        var core=Bukkit.getPluginManager().getPlugin("DuneCore");
        if(core!=null && core.isEnabled())try {
            return (List<ItemStack>)core.getClass().getMethod("createFactionKit",String.class).invoke(core,"Harkonnen");
        }catch(ReflectiveOperationException ex){plugin.getLogger().warning("Harkonnen kit requires updated DuneCore: "+ex.getMessage());}
        return List.of(new ItemStack(Material.IRON_SWORD),new ItemStack(Material.BREAD,8));
    }
    @EventHandler(ignoreCancelled=true) public void loot(LootGenerateEvent e) {
        if(!e.getLootTable().getKey().equals(LOOT))return;
        List<ItemStack> items=new ArrayList<>(e.getLoot());items.addAll(kit());e.setLoot(items);
    }
}
