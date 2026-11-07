package com.example.duneterrain;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DesertSietchTest {
    record P(int x, int y, int z) {}
    private static long mix(long n) {
        n=(n^(n>>>30))*0xBF58476D1CE4E5B9L;
        n=(n^(n>>>27))*0x94D049BB133111EBL;
        return n^(n>>>31);
    }
    private static int[] site() {
        for (int a=-4;a<=4;a++) for(int b=-4;b<=4;b++) {
            int x=a*2000+8+Math.floorMod(mix(a*0x9E3779B97F4A7C15L^b*0xC2B2AE3D27D4EB4FL^0x534945544348L),1984);
            int z=b*2000+8+Math.floorMod(mix(b*0x165667B19E3779F9L^a*0x85EBCA77C2B2AE63L^~0x534945544348L),1984);
            boolean ok=true;
            for(int dx=-40;dx<=40;dx+=4) for(int dz=-40;dz<=40;dz+=4)
                if(RockPlateaus.regionDepth(x+dx,z+dz,300,500,65,90,.02)>=-.15)ok=false;
            if(ok)return new int[]{x,z};
        }
        throw new AssertionError("No desert test site");
    }
    private Map<P,Material> generate(boolean reverse) throws Exception {
        int[] site=site(); int x=site[0],z=site[1];
        var plugin=mock(DuneTerrain.class);
        var config=new YamlConfiguration(); config.set("sietches.highlands.enabled",true);
        when(plugin.getConfig()).thenReturn(config);
        var world=mock(WorldInfo.class);
        when(world.getName()).thenReturn("arrakis");
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        var cls=Class.forName("com.example.duneterrain.DuneTerrain$SietchPopulator");
        var ctor=cls.getDeclaredConstructor(DuneTerrain.class);ctor.setAccessible(true);
        var pop=ctor.newInstance(plugin);
        var method=cls.getDeclaredMethod("populate",WorldInfo.class,Random.class,int.class,int.class,LimitedRegion.class);
        method.setAccessible(true);
        List<int[]> chunks=new ArrayList<>();
        for(int cx=Math.floorDiv(x-40,16);cx<=Math.floorDiv(x+40,16);cx++)
            for(int cz=Math.floorDiv(z-40,16);cz<=Math.floorDiv(z+40,16);cz++)chunks.add(new int[]{cx,cz});
        if(reverse)Collections.reverse(chunks);
        Map<P,Material> map=new HashMap<>();
        for(var chunk:chunks) {
            int cx=chunk[0],cz=chunk[1];
            var region=mock(LimitedRegion.class);
            when(region.isInRegion(anyInt(),anyInt(),anyInt())).thenAnswer(a-> {
                int xx=a.getArgument(0),yy=a.getArgument(1),zz=a.getArgument(2);
                return Math.floorDiv(xx,16)==cx && Math.floorDiv(zz,16)==cz && yy>=-64 && yy<320;
            });
            doAnswer(a->{
                int xx=a.getArgument(0),yy=a.getArgument(1),zz=a.getArgument(2);
                assertEquals(cx,Math.floorDiv(xx,16));assertEquals(cz,Math.floorDiv(zz,16));
                map.put(new P(xx,yy,zz),a.getArgument(3));return null;
            }).when(region).setType(anyInt(),anyInt(),anyInt(),any(Material.class));
            method.invoke(pop,world,new Random(1),cx,cz,region);
            verify(region,never()).getHighestBlockYAt(anyInt(),anyInt());
        }
        return map;
    }
    @Test void slicedGenerationIsOrderIndependentAndReservoirIsSealed() throws Exception {
        var map=generate(false);assertEquals(map,generate(true));
        int[] s=site();int x=s[0],z=s[1];
        int surface=70+(int)Math.round((Math.sin(x*.02)*Math.cos(z*.02*1.13)
            +Math.sin(x*.02*2.7+z*.02*1.45)*.35)*11),hy=surface-11;
        int water=0,caches=0;
        for(var e:map.entrySet()) {
            P p=e.getKey();
            if(e.getValue()==Material.CHEST)caches++;
            if(e.getValue()!=Material.WATER || p.y<hy-28 || p.y>hy-25)continue;
            water++;
            if(p.y==hy-28)assertEquals(Material.STONE,map.get(new P(p.x,p.y-1,p.z)));
            for(int[] step:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                Material m=map.get(new P(p.x+step[0],p.y,p.z+step[1]));
                assertTrue(m==Material.WATER||m==Material.STONE, "Uncontained reservoir at "+p);
            }
        }
        assertEquals(324,water);assertEquals(1,caches,"No extra reward caches");
        for(int dx:new int[]{-18,18})assertEquals(Material.AIR,map.get(new P(x+dx,hy-7,z-26)));
    }
}
