package com.example.duneterrain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.persistence.*;
import org.bukkit.event.block.*;
import org.bukkit.entity.Player;
import java.util.*;

class ArrakisFeaturesTest {
    @Test void densePatchesAreExactlyThirtyByThirtyIncludingNegativeCoordinates() {
        for(long seed:new long[]{-90,0,123456})for(int cx=-3;cx<=3;cx++)for(int cz=-3;cz<=3;cz++) {
            int[] a=DenseSpice.anchor(seed,cx*512,cz*512,512);
            int count=0;
            for(int x=a[0]-1;x<=a[0]+30;x++)for(int z=a[1]-1;z<=a[1]+30;z++)
                if(DenseSpice.contains(seed,x,z,512))count++;
            assertEquals(900,count);
            assertFalse(DenseSpice.contains(seed,a[0]-1,a[1],512));
            assertFalse(DenseSpice.contains(seed,a[0]+30,a[1],512));
        }
    }
    @Test void fortressAttemptsAreDeterministicAndWellRare() {
        int count=0;
        for(int cx=-500;cx<500;cx++)for(int cz=-100;cz<100;cz++) {
            boolean yes=HarkonnenFortress.attempt(1701,cx,cz,1000);
            assertEquals(yes,HarkonnenFortress.attempt(1701,cx,cz,1000));if(yes)count++;
        }
        assertTrue(count>140&&count<260,"observed "+count+" in 200000 chunks");
    }
    @Test void depositIdentitySurvivesListenerRestartAndCreativeMiningConsumesIt() {
        DuneTerrain plugin=mock(DuneTerrain.class);Chunk chunk=mock(Chunk.class);Block block=mock(Block.class);
        PersistentDataContainer pdc=mock(PersistentDataContainer.class);
        when(chunk.getPersistentDataContainer()).thenReturn(pdc);when(block.getChunk()).thenReturn(chunk);
        when(block.getX()).thenReturn(-17);when(block.getY()).thenReturn(80);when(block.getZ()).thenReturn(35);
        when(block.getType()).thenReturn(Material.ORANGE_TERRACOTTA);
        int[][] saved={new int[]{DenseSpice.encode(block)}};
        when(pdc.getOrDefault(any(),eq(PersistentDataType.INTEGER_ARRAY),any())).thenAnswer(i->saved[0]);
        doAnswer(i->{saved[0]=i.getArgument(2);return null;}).when(pdc).set(any(),eq(PersistentDataType.INTEGER_ARRAY),any());
        Player player=mock(Player.class);when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        BlockBreakEvent event=new BlockBreakEvent(block,player);
        new DenseSpice(plugin).harvest(event);assertFalse(event.isDropItems());
        new DenseSpice(plugin).reward(event);assertEquals(0,saved[0].length);
        BlockBreakEvent repeated=new BlockBreakEvent(block,player);new DenseSpice(plugin).harvest(repeated);
        assertTrue(repeated.isDropItems(),"ordinary replacement blocks have no spice identity");
        verify(block,never()).getWorld();
    }
    @Test void villageBuildsFourHousesAndChestsWithoutFarmsOrHay() {
        DuneTerrain plugin=mock(DuneTerrain.class);
        var config=new org.bukkit.configuration.file.YamlConfiguration();when(plugin.getConfig()).thenReturn(config);
        var world=mock(org.bukkit.generator.WorldInfo.class);when(world.getName()).thenReturn("arrakis");when(world.getMaxHeight()).thenReturn(320);
        var region=mock(org.bukkit.generator.LimitedRegion.class);
        when(region.isInRegion(anyInt(),anyInt(),anyInt())).thenReturn(true);
        when(region.getHighestBlockYAt(anyInt(),anyInt())).thenReturn(70);
        when(region.getType(anyInt(),anyInt(),anyInt())).thenReturn(Material.SAND);
        int[] a=DenseSpice.anchor(0x56494c4c414745L,0,0,768);
        List<Material> palette=new ArrayList<>();
        doAnswer(call->{palette.add(call.getArgument(3));return null;}).when(region).setType(anyInt(),anyInt(),anyInt(),any(Material.class));
        try(var api=mockStatic(Bukkit.class)) {
            var bed=mock(org.bukkit.block.data.type.Bed.class);when(bed.clone()).thenReturn(mock(org.bukkit.block.data.type.Bed.class));
            api.when(()->Bukkit.createBlockData(Material.RED_BED)).thenReturn(bed);
            new DesertVillages(plugin).populate(world,new Random(0),Math.floorDiv(a[0]+15,16),Math.floorDiv(a[1]+15,16),region);
        }
        verify(region,times(8)).setBlockData(anyInt(),anyInt(),anyInt(),any(org.bukkit.block.data.BlockData.class));
        assertEquals(4,Collections.frequency(palette,Material.CHEST));
        assertFalse(palette.contains(Material.HAY_BLOCK));assertFalse(palette.contains(Material.FARMLAND));assertFalse(palette.contains(Material.WHEAT));
        verify(region,times(4)).spawnEntity(any(Location.class),eq(org.bukkit.entity.EntityType.VILLAGER));
    }
    @Test void fortressHasAccessibleEntryVaultAndSteppedRoof() {
        DuneTerrain plugin=mock(DuneTerrain.class);
        var config=new org.bukkit.configuration.file.YamlConfiguration();config.set("harkonnen-fortress.rarity",1);when(plugin.getConfig()).thenReturn(config);
        var world=mock(org.bukkit.generator.WorldInfo.class);when(world.getName()).thenReturn("arrakis");when(world.getMaxHeight()).thenReturn(320);when(world.getMinHeight()).thenReturn(-64);
        var region=mock(org.bukkit.generator.LimitedRegion.class);when(region.isInRegion(anyInt(),anyInt(),anyInt())).thenReturn(true);when(region.getHighestBlockYAt(anyInt(),anyInt())).thenReturn(70);
        Map<String,Material> blocks=new HashMap<>();
        doAnswer(call->{blocks.put(call.getArgument(0)+","+call.getArgument(1)+","+call.getArgument(2),call.getArgument(3));return null;}).when(region).setType(anyInt(),anyInt(),anyInt(),any(Material.class));
        try(var api=mockStatic(Bukkit.class)) {
            api.when(()->Bukkit.createBlockData(Material.RED_WALL_BANNER)).thenReturn(mock(org.bukkit.block.data.Directional.class));
            new HarkonnenFortress(plugin).populate(world,new Random(0),0,0,region);
        }
        assertEquals(Material.AIR,blocks.get("8,71,1"));
        assertEquals(Material.CHEST,blocks.get("11,65,11"));
        assertEquals(Material.CUT_SANDSTONE,blocks.get("8,79,8"));
        assertEquals(Material.AIR,blocks.get("5,66,7"));
        assertFalse(blocks.containsValue(Material.HAY_BLOCK));assertFalse(blocks.containsValue(Material.FARMLAND));
    }
}
