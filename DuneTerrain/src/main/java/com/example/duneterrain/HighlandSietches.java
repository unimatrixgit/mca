package com.example.duneterrain;

import java.util.*;

/** Threefold regional placement, independent of the legacy open-desert sietch grid. */
final class HighlandSietches {
    record Site(int x,int z,long cacheId) {}
    static List<Site> sites(RockPlateaus.Shape shape,int multiplier,int minimum) {
        int target=Math.max(3,Math.min(12,Math.max(minimum,multiplier)));
        List<Site> candidates=new ArrayList<>();
        int extent=(int)Math.ceil(shape.radius()*1.6);
        for(int dx=-extent;dx<=extent;dx+=2)for(int dz=-extent;dz<=extent;dz+=2) {
            int x=(int)shape.centerX()+dx,z=(int)shape.centerZ()+dz;
            if(shape.depth(x,z)<.25)continue;
            candidates.add(new Site(x,z,DenseSpice.mix(shape.seed()^x*341873128712L^z*132897987541L^0x5349455443484849L)));
        }
        List<Site> result=new ArrayList<>();
        // Start at one end, then choose the farthest remaining sites, keeping compact caves apart.
        Site center=new Site((int)shape.centerX(),(int)shape.centerZ(),0);
        candidates.sort(Comparator.<Site>comparingLong(s->distance(s,center)).reversed().thenComparingLong(Site::cacheId));
        while(result.size()<target&&!candidates.isEmpty()) {
            Site best=null;long distance=-1;
            for(Site candidate:candidates) {
                long nearest=result.isEmpty()?Long.MAX_VALUE:result.stream().mapToLong(s->distance(s,candidate)).min().orElse(0);
                if(nearest>distance){distance=nearest;best=candidate;}
            }
            if(!result.isEmpty()&&distance<26L*26)break;
            result.add(best);candidates.remove(best);
        }
        return List.copyOf(result);
    }
    private static long distance(Site a,Site b) {long dx=a.x()-b.x(),dz=a.z()-b.z();return dx*dx+dz*dz;}
}
