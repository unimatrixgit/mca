package com.example.duneterrain;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HighlandSietchesTest {
    @Test void everyDefaultHighlandHasThreeDistinctSpacedCavesInsideItsFootprint() {
        for(int cx=-10;cx<=10;cx++)for(int cz=-10;cz<=10;cz++) {
            long seed=RockPlateaus.formationSeed(cx,cz);
            var shape=RockPlateaus.shape(seed,RockPlateaus.centerX(cx,300,500),RockPlateaus.centerZ(cz,300,500),RockPlateaus.radius(seed,45,55),.02);
            var sites=HighlandSietches.sites(shape,3,3);assertEquals(3,sites.size(),"region "+cx+","+cz);
            assertEquals(sites,HighlandSietches.sites(shape,3,3));
            assertEquals(3,sites.stream().map(HighlandSietches.Site::cacheId).distinct().count());
            for(var a:sites){assertTrue(shape.depth(a.x(),a.z())>=.25);for(var b:sites)if(a!=b){int dx=a.x()-b.x(),dz=a.z()-b.z();assertTrue(dx*dx+dz*dz>=676);}}
        }
    }
    @Test void highlandPopulationPlacesThreeSeparateCachesWithoutChangingDesertSpacing() throws Exception {
        var plugin=org.mockito.Mockito.mock(DuneTerrain.class);
        var config=new org.bukkit.configuration.file.YamlConfiguration();
        org.mockito.Mockito.when(plugin.getConfig()).thenReturn(config);
        var world=org.mockito.Mockito.mock(org.bukkit.generator.WorldInfo.class);
        org.mockito.Mockito.when(world.getMinHeight()).thenReturn(-64);
        org.mockito.Mockito.when(world.getMaxHeight()).thenReturn(320);
        var region=org.mockito.Mockito.mock(org.bukkit.generator.LimitedRegion.class);
        org.mockito.Mockito.when(region.getHighestBlockYAt(org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt())).thenReturn(110);
        org.mockito.Mockito.when(region.isInRegion(org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        var cls=Class.forName("com.example.duneterrain.DuneTerrain$SietchPopulator");
        var constructor=cls.getDeclaredConstructor(DuneTerrain.class);constructor.setAccessible(true);
        var populator=constructor.newInstance(plugin);
        var method=cls.getDeclaredMethod("populateHighlands",org.bukkit.generator.WorldInfo.class,int.class,int.class,org.bukkit.generator.LimitedRegion.class);method.setAccessible(true);
        long seed=RockPlateaus.formationSeed(0,0);
        var shape=RockPlateaus.shape(seed,RockPlateaus.centerX(0,300,500),RockPlateaus.centerZ(0,300,500),RockPlateaus.radius(seed,45,55),.02);
        var chunks=new java.util.HashSet<String>();
        for(var site:HighlandSietches.sites(shape,3,3))chunks.add(Math.floorDiv(site.x(),16)+","+Math.floorDiv(site.z(),16));
        for(var chunk:chunks){var parts=chunk.split(",");method.invoke(populator,world,Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),region);}
        org.mockito.Mockito.verify(region,org.mockito.Mockito.times(3)).setType(org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.eq(org.bukkit.Material.CHEST));
        config.set("sietches.highlands.enabled",false);
        org.mockito.Mockito.clearInvocations(region);
        method.invoke(populator,world,10,10,region);
        org.mockito.Mockito.verifyNoInteractions(region);
        assertFalse(config.contains("sietches.spacing"));
    }
}
