package com.example.duneterrain;

import org.junit.jupiter.api.Test;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.ChunkGenerator;
import java.io.InputStreamReader;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Executes the shipped generator's ore and mountain methods, not a copied formula. */
class PlanetGenerationTest {
    private static final class Blocks implements ChunkGenerator.ChunkData {
        final Material[] blocks=new Material[16*384*16];
        Blocks(){Arrays.fill(blocks,Material.AIR);}
        int key(int x,int y,int z){return ((y+64)*16+x)*16+z;}
        public int getMinHeight(){return -64;}public int getMaxHeight(){return 320;}
        public org.bukkit.block.Biome getBiome(int x,int y,int z){return null;}
        public void setBlock(int x,int y,int z,Material m){blocks[key(x,y,z)]=m;}
        public Material getType(int x,int y,int z){return blocks[key(x,y,z)];}
        public void setBlock(int x,int y,int z,org.bukkit.material.MaterialData m){throw new UnsupportedOperationException();}
        public void setBlock(int x,int y,int z,org.bukkit.block.data.BlockData m){throw new UnsupportedOperationException();}
        public void setRegion(int x,int y,int z,int xx,int yy,int zz,Material m){for(int a=x;a<xx;a++)for(int b=y;b<yy;b++)for(int c=z;c<zz;c++)setBlock(a,b,c,m);}
        public void setRegion(int x,int y,int z,int xx,int yy,int zz,org.bukkit.material.MaterialData m){throw new UnsupportedOperationException();}
        public void setRegion(int x,int y,int z,int xx,int yy,int zz,org.bukkit.block.data.BlockData m){throw new UnsupportedOperationException();}
        public org.bukkit.material.MaterialData getTypeAndData(int x,int y,int z){throw new UnsupportedOperationException();}
        public org.bukkit.block.data.BlockData getBlockData(int x,int y,int z){throw new UnsupportedOperationException();}
        public byte getData(int x,int y,int z){return 0;}
        public int getHeight(org.bukkit.HeightMap map,int x,int z){for(int y=319;y>=-64;y--)if(getType(x,y,z)!=Material.AIR)return y;return -64;}
    }
    private static final class Harness {
        final YamlConfiguration config;
        final Object generator;
        final Map<String,Material> blocks = new HashMap<>();
        final ChunkGenerator.ChunkData data = mock(ChunkGenerator.ChunkData.class);
        Harness(String planet) throws Exception {
            config=YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/config.yml"),java.nio.charset.StandardCharsets.UTF_8));
            DuneTerrain plugin=mock(DuneTerrain.class);when(plugin.getConfig()).thenReturn(config);
            Class<?> type=Class.forName("com.example.duneterrain.DuneTerrain$PlanetChunkGenerator");
            Constructor<?> ctor=type.getDeclaredConstructor(DuneTerrain.class,String.class);ctor.setAccessible(true);generator=ctor.newInstance(plugin,planet);
            when(data.getType(anyInt(),anyInt(),anyInt())).thenAnswer(i->blocks.getOrDefault(key(i.getArgument(0),i.getArgument(1),i.getArgument(2)),Material.STONE));
            doAnswer(i->{blocks.put(key(i.getArgument(0),i.getArgument(1),i.getArgument(2)),i.getArgument(3));return null;}).when(data).setBlock(anyInt(),anyInt(),anyInt(),any(Material.class));
        }
        static String key(int x,int y,int z){return x+","+y+","+z;}
        void ores(String planet,long seed)throws Exception {
            Method m=generator.getClass().getDeclaredMethod("placeOres",ChunkGenerator.ChunkData.class,Random.class,String.class,int.class,int.class);m.setAccessible(true);m.invoke(generator,data,new Random(seed),"planets."+planet+".",-64,319);
        }
    }
    @Test void orePalettesMatchPlanetSpecificationsAndArrakisHasNoMineableOre() throws Exception {
        Map<String,Set<Material>> expected=Map.of(
            "arrakis",Set.of(),
            "kaitain",Set.of(Material.GOLD_ORE,Material.EMERALD_ORE,Material.DIAMOND_ORE,Material.LAPIS_ORE),
            "giedi_prime",Set.of(Material.COAL_ORE,Material.IRON_ORE,Material.COPPER_ORE,Material.REDSTONE_ORE,Material.ANCIENT_DEBRIS));
        for(var entry:expected.entrySet()) {
            Harness h=new Harness(entry.getKey());
            for(int seed=0;seed<12;seed++)h.ores(entry.getKey(),seed);
            assertEquals(entry.getValue(),new HashSet<>(h.blocks.values()),entry.getKey());
            for(var b:h.blocks.entrySet()) {
                int y=Integer.parseInt(b.getKey().split(",")[1]);
                if(b.getValue()==Material.ANCIENT_DEBRIS)assertTrue(y>=-59&&y<=16);
                else assertTrue(y>=-48&&y<=48);
            }
        }
    }
    @Test void oresNeverReplacePlayerLikeBuildingBlocksOrAir() throws Exception {
        for(Material background:List.of(Material.AIR,Material.BLACKSTONE,Material.OAK_PLANKS,Material.CHEST)) {
            Harness h=new Harness("giedi_prime");
            when(h.data.getType(anyInt(),anyInt(),anyInt())).thenReturn(background);
            h.ores("giedi_prime",123);assertTrue(h.blocks.isEmpty(),background.name());
        }
    }
    @Test void actualMountainHasTallRockShouldersAndFlatSandyUpperTerraces() throws Exception {
        Harness h=new Harness("arrakis");
        Method m=h.generator.getClass().getDeclaredMethod("placeDarkstoneIsland",ChunkGenerator.ChunkData.class,int.class,int.class,int[][].class,int.class,int.class);m.setAccessible(true);
        long seed=RockPlateaus.formationSeed(0,0);int cx=(int)RockPlateaus.centerX(0,300,500),cz=(int)RockPlateaus.centerZ(0,300,500);
        Set<Integer> sandLevels=new HashSet<>();int tallest=0,rocks=0;
        for(int x=Math.floorDiv(cx-65,16);x<=Math.floorDiv(cx+65,16);x++)for(int z=Math.floorDiv(cz-65,16);z<=Math.floorDiv(cz+65,16);z++) {
            h.blocks.clear();int[][] heights=new int[16][16];for(int[] row:heights)Arrays.fill(row,70);
            m.invoke(h.generator,h.data,x,z,heights,-64,319);
            for(var b:h.blocks.entrySet()) {
                int y=Integer.parseInt(b.getKey().split(",")[1]);tallest=Math.max(tallest,y);
                if(b.getValue()==Material.SAND)sandLevels.add(y);else rocks++;
            }
        }
        assertTrue(tallest>=120,"mountain must rise, not remain a short circular island");
        assertTrue(rocks>10000);
        assertEquals(6,sandLevels.size(),"three lower ledges plus three upper terraces");
        List<Integer> levels=sandLevels.stream().sorted().toList();
        assertTrue(levels.get(3)-levels.get(2)>10,"rock shoulder separates the two terrace groups");
        assertTrue(levels.get(2)-levels.get(1)<=5&&levels.get(1)-levels.get(0)<=5);
        assertTrue(levels.get(5)-levels.get(4)<=5&&levels.get(4)-levels.get(3)<=5);
    }
    @Test void fullNoiseGeneratesExpectedPlanetSurfacesOresAndWritesMeasuredArrakisHeightmap() throws Exception {
        var world=mock(org.bukkit.generator.WorldInfo.class);when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);when(world.getSeed()).thenReturn(104729L);
        Map<String,Set<Material>> ores=Map.of("arrakis",Set.of(),"kaitain",Set.of(Material.GOLD_ORE,Material.EMERALD_ORE,Material.DIAMOND_ORE,Material.LAPIS_ORE),"giedi_prime",Set.of(Material.COAL_ORE,Material.IRON_ORE,Material.COPPER_ORE,Material.REDSTONE_ORE,Material.ANCIENT_DEBRIS));
        for(String planet:List.of("arrakis","kaitain","giedi_prime")) {
            Harness h=new Harness(planet);h.config.set("planets.arrakis.oasis-trees.enabled",false);when(world.getName()).thenReturn(planet);
            Set<Material> found=new HashSet<>();Map<Material,Integer> counts=new EnumMap<>(Material.class);
            StringBuilder csv=new StringBuilder("x,z,height,material\n");
            int start=planet.equals("giedi_prime")?-32:0,end=planet.equals("giedi_prime")?-23:3;
            int sx=planet.equals("arrakis")?(int)Math.floorDiv(RockPlateaus.centerX(0,300,500),16)-8:start,ex=planet.equals("arrakis")?sx+15:end;
            int sz=planet.equals("arrakis")?(int)Math.floorDiv(RockPlateaus.centerZ(0,300,500),16)-8:start,ez=planet.equals("arrakis")?sz+15:end;
            for(int cx=sx;cx<=ex;cx++)for(int cz=sz;cz<=ez;cz++) {
                Blocks b=new Blocks();((ChunkGenerator)h.generator).generateNoise(world,new Random(104729L+cx*31L+cz*17L),cx,cz,b);
                for(Material m:b.blocks){counts.merge(m,1,Integer::sum);if(m.name().endsWith("_ORE")||m==Material.ANCIENT_DEBRIS)found.add(m);}
                if(planet.equals("arrakis"))for(int x=0;x<16;x++)for(int z=0;z<16;z++){int y=b.getHeight(org.bukkit.HeightMap.WORLD_SURFACE,x,z);csv.append(cx*16+x).append(',').append(cz*16+z).append(',').append(y).append(',').append(b.getType(x,y,z).name()).append('\n');}
            }
            assertEquals(ores.get(planet),found,planet);
            if(planet.equals("kaitain")){assertTrue(counts.getOrDefault(Material.GRASS_BLOCK,0)>3000);assertTrue(counts.getOrDefault(Material.POPPY,0)>0);assertTrue(counts.getOrDefault(Material.BIRCH_LOG,0)>0);assertTrue(counts.getOrDefault(Material.OAK_LOG,0)>0);}
            if(planet.equals("giedi_prime")){assertTrue(counts.getOrDefault(Material.BLACKSTONE,0)>3000);assertTrue(counts.getOrDefault(Material.WATER,0)>0);assertTrue(counts.getOrDefault(Material.DIRT,0)>3000);assertTrue(counts.getOrDefault(Material.OAK_LOG,0)>0);}
            if(planet.equals("arrakis")){assertTrue(counts.getOrDefault(Material.BLACKSTONE,0)>10000);assertTrue(counts.getOrDefault(Material.SUSPICIOUS_SAND,0)>0);java.nio.file.Files.writeString(java.nio.file.Path.of("build/arrakis-measured-heightmap.csv"),csv);}
            System.out.println("ACTUAL_GENERATOR "+planet+" ore palette="+found+" material counts="+counts);
        }
    }
}


