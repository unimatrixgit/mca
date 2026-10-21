package com.example.duneterrain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RockPlateausTest {
    @Test void capsHoldEvenWithInvalidConfigurationAndNegativeSeeds() {
        for (int maximum : new int[]{-10, 0, 1, 2, 3, 100}) {
            for (long seed = -100; seed <= 100; seed++) {
                int count = RockPlateaus.count(seed, maximum);
                assertTrue(count >= 1 && count <= 3);
                for (int configured : new int[]{-5, 0, 1, 5, 100}) {
                    int interval = RockPlateaus.interval(configured, 14, count);
                    assertTrue(interval >= 1 && interval <= 5);
                    assertTrue(count * interval <= 14);
                }
            }
        }
    }

    @Test void levelsStayFlatAndBoundedForIrregularContours() {
        for (int count = 1; count <= 3; count++) {
            java.util.Set<Integer> levels = new java.util.HashSet<>();
            for (int i = -10; i <= 120; i++) {
                int level = RockPlateaus.level(i / 100.0, count);
                assertTrue(level >= 0 && level <= count);
                levels.add(level);
            }
            assertEquals(count + 1, levels.size());
        }
    }
    @Test void outlinesAreNotRotationallySymmetricAndVaryWithTheSeed() {
        var a = RockPlateaus.shape(123, 0, 0, 50, .02);
        var b = RockPlateaus.shape(456, 0, 0, 50, .02);
        int asymmetric = 0, different = 0;
        for (int x = -60; x <= 60; x++) for (int z = -60; z <= 60; z++) {
            if ((a.depth(x,z) >= 0) != (a.depth(-z,x) >= 0)) asymmetric++;
            if ((a.depth(x,z) >= 0) != (b.depth(x,z) >= 0)) different++;
        }
        assertTrue(asymmetric > 500);
        assertTrue(different > 200);
    }
    @Test void contourIsContinuousAcrossChunkBoundariesAndOutsideSearchBounds() {
        for (long seed : new long[]{-98, 0, 123, 999}) {
            var shape = RockPlateaus.shape(seed, -54, -668, 48, .02);
            for (int z = -760; z <= -576; z++) {
                assertTrue(Math.abs(shape.depth(-49,z) - shape.depth(-48,z)) < .2);
            }
            for (int p = -120; p <= 120; p++) {
                assertTrue(shape.depth(-54 + 82, -668 + p) < 0);
                assertTrue(shape.depth(-54 - 82, -668 + p) < 0);
                assertTrue(shape.depth(-54 + p, -668 + 82) < 0);
                assertTrue(shape.depth(-54 + p, -668 - 82) < 0);
            }
        }
    }
    @Test void allCardinalNeighboursAreBetweenThreeAndFiveHundredBlocksApart() {
        for (int cell = -10000; cell < 10000; cell++) {
            long east = RockPlateaus.centerX(cell + 1,300,500) - RockPlateaus.centerX(cell,300,500);
            long north = RockPlateaus.centerZ(cell + 1,300,500) - RockPlateaus.centerZ(cell,300,500);
            assertTrue(east >= 300 && east <= 500);
            assertTrue(north >= 300 && north <= 500);
        }
    }
    @Test void biomeMaskMatchesTheTerrainFootprint() {
        for (int cx=-1;cx<=1;cx++) for(int cz=-1;cz<=1;cz++) {
            long seed=RockPlateaus.formationSeed(cx,cz);
            long x=RockPlateaus.centerX(cx,300,500), z=RockPlateaus.centerZ(cz,300,500);
            var shape=RockPlateaus.shape(seed,x,z,RockPlateaus.radius(seed,45,55),.02);
            for(int dx=-80;dx<=80;dx+=8) for(int dz=-80;dz<=80;dz+=8)
                assertEquals(shape.depth(x+dx,z+dz)>=0,
                    RockPlateaus.regionDepth((int)x+dx,(int)z+dz,300,500,45,55,.02)>=0);
        }
    }
    @Test void erosionSpiresStayOnShouldersAndRespectHeightLimits() {
        int tallest=0, varying=0, previous=-1;
        for(int x=-100;x<=100;x++)for(int z=-100;z<=100;z++) {
            int rise=RockPlateaus.spireRise(123,x,z,.355,40);
            assertTrue(rise>=0&&rise<=40);tallest=Math.max(tallest,rise);
            if(previous>=0&&previous!=rise)varying++;previous=rise;
            assertEquals(0,RockPlateaus.spireRise(123,x,z,.46,40));
            assertEquals(0,RockPlateaus.spireRise(123,x,z,-.1,40));
        }
        assertTrue(tallest>=30);assertTrue(varying>1000);
    }
    @Test void lowerTerracesAreThreeFlatStepsSeparatedFromUpperTerraces() {
        java.util.Set<Integer> levels=new java.util.HashSet<>();
        for(int d=0;d<=1000;d++) {
            double depth=d/1000.0;int lower=RockPlateaus.lowerTerrace(depth);
            if(lower>0){levels.add(lower);assertEquals(0,RockPlateaus.level((depth-.40)/.60,3));assertEquals(0,RockPlateaus.spireRise(1,d,0,depth,40));}
        }
        assertEquals(java.util.Set.of(1,2,3),levels);
    }
}
