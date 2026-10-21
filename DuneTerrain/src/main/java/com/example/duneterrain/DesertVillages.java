package com.example.duneterrain;

import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.entity.EntityType;
import org.bukkit.generator.*;
import java.util.*;

/** Compact custom sandstone villages: no farm pieces or hay ever enter the palette. */
final class DesertVillages extends BlockPopulator {
    private final DuneTerrain plugin;
    DesertVillages(DuneTerrain plugin) { this.plugin=plugin; }
    @Override public void populate(WorldInfo world,Random random,int cx,int cz,LimitedRegion r) {
        if(!world.getName().equalsIgnoreCase("arrakis")||!plugin.getConfig().getBoolean("desert-villages.enabled",true))return;
        int spacing=Math.max(192,plugin.getConfig().getInt("desert-villages.spacing",768));
        // A complete village fits inside one chunk's population buffer. Only the anchor chunk builds it.
        int[] a=DenseSpice.anchor(world.getSeed()^0x56494c4c414745L,cx*16+8,cz*16+8,spacing);
        int x=a[0]+15,z=a[1]+15;
        if(Math.floorDiv(x,16)!=cx||Math.floorDiv(z,16)!=cz)return;
        for(int dx=-16;dx<=16;dx+=8)for(int dz=-16;dz<=16;dz+=8)
            if(!r.isInRegion(x+dx,world.getMinHeight()+1,z+dz))return;
        int y=r.getHighestBlockYAt(x,z);
        if(y+7>=world.getMaxHeight())return;
        // Avoid mountains and existing rich fields rather than bulldozing their terrain.
        for(int dx=-16;dx<=16;dx+=8)for(int dz=-16;dz<=16;dz+=8)
            if(Math.abs(r.getHighestBlockYAt(x+dx,z+dz)-y)>8||r.getType(x+dx,r.getHighestBlockYAt(x+dx,z+dz),z+dz)==Material.ORANGE_TERRACOTTA)return;
        for(int dx=-15;dx<=15;dx++)for(int dz=-15;dz<=15;dz++) {
            int ground=r.getHighestBlockYAt(x+dx,z+dz);
            for(int h=Math.min(ground,y);h<=y;h++)r.setType(x+dx,h,z+dz,Material.SANDSTONE);
            for(int h=y+1;h<=Math.max(ground,y+6);h++)r.setType(x+dx,h,z+dz,Material.AIR);
            r.setType(x+dx,y,z+dz,Math.abs(dx)<=2||Math.abs(dz)<=2?Material.CUT_SANDSTONE:Material.SAND);
        }
        for(int ox:new int[]{-12,6})for(int oz:new int[]{-12,6}) {
            for(int dx=0;dx<7;dx++)for(int dz=0;dz<7;dz++)for(int dy=1;dy<=5;dy++) {
                boolean wall=dx==0||dz==0||dx==6||dz==6;
                r.setType(x+ox+dx,y+dy,z+oz+dz,dy==5?Material.SMOOTH_SANDSTONE:wall?Material.SANDSTONE:Material.AIR);
            }
            r.setType(x+ox+3,y+1,z+oz,Material.AIR);r.setType(x+ox+3,y+2,z+oz,Material.AIR);
            r.setType(x+ox+1,y+2,z+oz,Material.GLASS_PANE);
            r.setType(x+ox+3,y+4,z+oz+3,Material.LANTERN);
            // Beds and non-farming job sites make this a functioning village, not empty scenery.
            var foot=(org.bukkit.block.data.type.Bed)Material.RED_BED.createBlockData();
            foot.setFacing(org.bukkit.block.BlockFace.SOUTH);foot.setPart(org.bukkit.block.data.type.Bed.Part.FOOT);
            var head=(org.bukkit.block.data.type.Bed)foot.clone();head.setPart(org.bukkit.block.data.type.Bed.Part.HEAD);
            r.setBlockData(x+ox+2,y+1,z+oz+3,foot);r.setBlockData(x+ox+2,y+1,z+oz+4,head);
            r.setType(x+ox+1,y+1,z+oz+4,(ox<0?oz<0?Material.BARREL:Material.FLETCHING_TABLE:oz<0?Material.BLAST_FURNACE:Material.CARTOGRAPHY_TABLE));
            int bx=x+ox+4,bz=z+oz+4;
            r.setType(bx,y+1,bz,Material.CHEST);
            if(r.getBlockState(bx,y+1,bz) instanceof Chest chest) {
                var table=Bukkit.getLootTable(new NamespacedKey("dunerealms","chests/desert-village"));
                if(table!=null){chest.setLootTable(table);chest.setSeed(DenseSpice.mix(world.getSeed()^bx*31L^bz));}
                else { // Safe stock when the optional biome/loot datapack has not yet been installed.
                    chest.getSnapshotInventory().addItem(new org.bukkit.inventory.ItemStack(Material.BREAD,4),new org.bukkit.inventory.ItemStack(Material.DRIED_KELP,6),new org.bukkit.inventory.ItemStack(Material.RAW_IRON,3));
                }
                chest.update(true,false);
            }
            r.spawnEntity(new Location(null,x+ox+3.5,y+1,z+oz+3.5),EntityType.VILLAGER);
        }
    }
}
