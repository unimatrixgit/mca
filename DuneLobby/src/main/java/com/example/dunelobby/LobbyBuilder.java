package com.example.dunelobby;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Slab;
import java.util.*;

/** Deterministic, bounded build. Coordinates are also used by the saved-world verifier. */
final class LobbyBuilder {
    static final int FLOOR = 80;
    static final List<String> PLANETS = List.of("arrakis", "caladan", "giedi_prime", "kaitain", "sigma_draconis_iv");
    static final int[][] PADS = {{-100,-73},{-78,-75},{-56,-73},{-101,-39},{-55,-39}};
    private final World w;
    private long count;
    LobbyBuilder(World world) { w = world; }
    void block(int x,int y,int z,Material m) { w.getBlockAt(x,y,z).setType(m,false); count++; }
    void box(int x1,int y1,int z1,int x2,int y2,int z2,Material m) {
        for(int x=x1;x<=x2;x++) for(int z=z1;z<=z2;z++) for(int y=y1;y<=y2;y++) block(x,y,z,m);
    }
    private boolean inside(int x,int z,int rx,int rz) {
        return Math.abs(x)<=rx && Math.abs(z)<=rz && Math.abs(x)+Math.abs(z)<=rx+rz-5;
    }
    /** Octagonal rooms, glazed clerestories, stepped barrel vaults, repeated ribs. */
    private void room(int cx,int cz,int rx,int rz,int height,boolean shady) {
        for(int x=-rx;x<=rx;x++) for(int z=-rz;z<=rz;z++) {
            if(!inside(x,z,rx,rz)) continue;
            boolean edge = !inside(x+1,z,rx,rz)||!inside(x-1,z,rx,rz)||!inside(x,z+1,rx,rz)||!inside(x,z-1,rx,rz);
            block(cx+x,78,cz+z,Material.DEEPSLATE_TILES);
            block(cx+x,79,cz+z,Material.POLISHED_BLACKSTONE_BRICKS);
            Material floor = (Math.floorMod(x,8)==0 || Math.floorMod(z,8)==0) ? Material.POLISHED_BLACKSTONE : Material.DEEPSLATE_TILES;
            if(Math.abs(x)==rx-2 || Math.abs(z)==rz-2) floor=shady?Material.WAXED_WEATHERED_COPPER:Material.WAXED_CUT_COPPER;
            block(cx+x,80,cz+z,floor);
            int roof = FLOOR+height-(int)Math.round(7.0*Math.pow(Math.abs(x)/(double)rx,2));
            block(cx+x,roof,cz+z,Material.POLISHED_BLACKSTONE_BRICKS);
            if(Math.floorMod(z+rz,8)==0) {
                block(cx+x,roof-1,cz+z,Material.DEEPSLATE_BRICKS);
                if(Math.abs(x)%9==0) block(cx+x,roof-2,cz+z,Material.WAXED_CUT_COPPER);
            }
            if(edge) for(int y=81;y<roof;y++) {
                Material m=Material.POLISHED_BLACKSTONE_BRICKS;
                if(y>=85 && y<=roof-4) m=Material.PURPLE_STAINED_GLASS;
                if(y==84 || y==roof-3) m=Material.WAXED_CUT_COPPER;
                if(Math.floorMod(x+z,8)==0) m=Material.DEEPSLATE_BRICKS;
                block(cx+x,y,cz+z,m);
            }
        }
        for(int x=-rx+7;x<=rx-7;x+=8) for(int z=-rz+7;z<=rz-7;z+=8) {
            if(!shady || Math.floorMod(x+z,3)==0) lamp(cx+x,81,cz+z,shady);
        }
        // Gold centre stripe serves as a continuous wayfinding route.
        for(int z=-rz+4;z<=rz-4;z++) block(cx,80,cz+z,shady?Material.WAXED_WEATHERED_COPPER:Material.GOLD_BLOCK);
    }
    private void lamp(int x,int y,int z,boolean dim) {
        block(x,y,z,Material.POLISHED_BLACKSTONE_BRICK_WALL);
        block(x,y+1,z,dim?Material.SOUL_LANTERN:Material.LANTERN);
    }
    private void corridor(int x1,int z1,int x2,int z2,boolean shady) {
        int minX=Math.min(x1,x2)-3,maxX=Math.max(x1,x2)+3,minZ=Math.min(z1,z2)-3,maxZ=Math.max(z1,z2)+3;
        box(minX,78,minZ,maxX,80,maxZ,Material.DEEPSLATE_TILES);
        box(minX,81,minZ,maxX,87,maxZ,Material.AIR);
        box(minX,88,minZ,maxX,88,maxZ,Material.DEEPSLATE_BRICKS);
        for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++) {
            if((x1==x2 && (x==minX||x==maxX)) || (z1==z2 && (z==minZ||z==maxZ))) {
                // Leave junctions open wherever another supported floor joins this edge.
                int outsideX=x+(x1==x2?(x==minX?-1:1):0);
                int outsideZ=z+(z1==z2?(z==minZ?-1:1):0);
                if(w.getBlockAt(outsideX,80,outsideZ).getType().isSolid()) continue;
                block(x,81,z,Material.POLISHED_BLACKSTONE_BRICK_WALL);
                block(x,82,z,Material.POLISHED_BLACKSTONE_BRICK_WALL);
                if(Math.floorMod(x+z,6)==0) {
                    block(x,83,z,shady?Material.SOUL_LANTERN:Material.LANTERN);
                    box(x,84,z,x,87,z,Material.CHAIN);
                }
            } else if((x1==x2 && x==x1)||(z1==z2 && z==z1)) block(x,80,z,shady?Material.WAXED_WEATHERED_COPPER:Material.GOLD_BLOCK);
        }
    }
    void sign(int x,int y,int z,BlockFace face,String... lines) {
        block(x,y,z,Material.DARK_OAK_WALL_SIGN);
        Directional d=(Directional)w.getBlockAt(x,y,z).getBlockData(); d.setFacing(face); w.getBlockAt(x,y,z).setBlockData(d,false);
        Sign s=(Sign)w.getBlockAt(x,y,z).getState();
        for(int i=0;i<Math.min(4,lines.length);i++) s.getSide(Side.FRONT).setLine(i,lines[i]);
        s.getSide(Side.FRONT).setGlowingText(true); s.getSide(Side.FRONT).setColor(DyeColor.YELLOW); s.setWaxed(true); s.update(true,false);
    }
    private void ellipsoid(double cx,double cy,double cz,double rx,double ry,double rz,Material m) {
        for(int x=(int)Math.floor(cx-rx);x<=Math.ceil(cx+rx);x++) for(int y=(int)Math.floor(cy-ry);y<=Math.ceil(cy+ry);y++) for(int z=(int)Math.floor(cz-rz);z<=Math.ceil(cz+rz);z++) {
            double n=Math.pow((x-cx)/rx,2)+Math.pow((y-cy)/ry,2)+Math.pow((z-cz)/rz,2);
            if(n<=1) block(x,y,z,m);
        }
    }
    private void navigator() {
        box(-11,81,-25,11,83,-11,Material.DEEPSLATE_BRICKS);
        box(-9,84,-24,9,85,-12,Material.WAXED_CUT_COPPER);
        box(-8,86,-24,8,108,-12,Material.ORANGE_STAINED_GLASS);
        box(-7,87,-23,7,107,-13,Material.AIR);
        for(int x:new int[]{-9,9}) for(int z:new int[]{-24,-12}) {
            box(x,85,z,x,109,z,Material.POLISHED_BLACKSTONE_BRICKS);
            for(int y=86;y<=108;y+=4) block(x,y,z,Material.GOLD_BLOCK);
        }
        box(-9,109,-24,9,110,-12,Material.WAXED_CUT_COPPER);
        for(int x=-7;x<=7;x+=3) { block(x,86,-18,Material.SHROOMLIGHT); block(x,108,-18,Material.SHROOMLIGHT); }
        // Pale, folded, finned aquatic Navigator sculpture; no client model required.
        ellipsoid(0,96,-19,3.2,6.5,2.3,Material.SMOOTH_SANDSTONE);
        ellipsoid(0,103,-18,4.2,3.0,2.8,Material.CUT_SANDSTONE);
        ellipsoid(0,101,-15.6,2.6,1.3,1.0,Material.SMOOTH_SANDSTONE);
        block(-2,103,-15,Material.BLACK_TERRACOTTA); block(2,103,-15,Material.BLACK_TERRACOTTA);
        for(int s:new int[]{-1,1}) {
            ellipsoid(s*3.3,95,-17,1.3,4.2,1,Material.SMOOTH_SANDSTONE);
            ellipsoid(s*3,90,-17.5,2.4,1.3,2.2,Material.CUT_SANDSTONE);
            for(int f=0;f<4;f++) box(s<0?-6:-1,92+f,-15-f,s<0?-1:6,92+f,-15-f,Material.SMOOTH_SANDSTONE);
        }
        for(int y=90;y<=102;y+=3) { block(-3,y,-17,Material.SANDSTONE); block(3,y,-17,Material.SANDSTONE); }
        for(int z=-10;z<=-6;z++) for(int x=-6;x<=6;x++) block(x,80,z,Material.GOLD_BLOCK);
        box(-2,81,-9,2,82,-8,Material.WAXED_CUT_COPPER);
        sign(0,82,-7,BlockFace.SOUTH,"SPACING GUILD","NAVIGATOR","Right-click liaison","for planet travel");
        for(int x=-18;x<=18;x++) for(int z=-5;z<=23;z++) {
            double r=Math.hypot(x,z-10);
            if((r>=9.5 && r<=10.5)||(r>=3.5&&r<=4.5)) block(x,80,z,Material.GOLD_BLOCK);
        }
    }
    private void pad(int cx,int cz,int radius,int y,boolean launch,int number) {
        box(cx-radius-1,80,cz-radius-1,cx+radius+1,y,cz+radius+1,Material.GRAY_CONCRETE);
        for(int x=-radius;x<=radius;x++) for(int z=-radius;z<=radius;z++) {
            Material m=Material.POLISHED_BASALT;
            if(Math.abs(x)==radius || Math.abs(z)==radius) m=Math.floorMod(x+z,4)<2?Material.YELLOW_CONCRETE:Material.BLACK_CONCRETE;
            if((Math.abs(x)<=1 && Math.abs(z)<=radius-2)||(Math.abs(z)<=1 && Math.abs(x)<=radius-2)) m=Material.SMOOTH_QUARTZ;
            block(cx+x,y,cz+z,m);
        }
        for(int s:new int[]{-1,1}) for(int t:new int[]{-1,1}) { block(cx+s*radius,y+1,cz+t*radius,Material.LANTERN); }
        if(launch) {
            box(cx-2,81,cz+radius+1,cx+2,81,cz+radius+2,Material.SMOOTH_STONE);
            box(cx-2,81,cz+radius+3,cx+2,81,cz+radius+3,Material.SMOOTH_STONE_SLAB);
            box(cx-2,83,cz-radius-2,cx+2,84,cz-radius-2,Material.WAXED_CUT_COPPER);
            block(cx,85,cz-radius-2,Material.LANTERN);
            sign(cx,84,cz-radius-1,BlockFace.SOUTH,"DEPARTURE 0"+number,PLANETS.get(number-1).replace('_',' '),"Right-click console","Confirm destination");
        }
    }
    private void starport() {
        pad(-78,-53,10,80,false,0);
        for(int i=0;i<PADS.length;i++) pad(PADS[i][0],PADS[i][1],6,82,true,i+1);
        box(-84,81,-86,-72,90,-82,Material.SMOOTH_SANDSTONE);
        box(-83,87,-81,-73,89,-81,Material.CYAN_STAINED_GLASS);
        box(-85,91,-87,-71,91,-81,Material.WAXED_CUT_COPPER);
        box(-79,92,-85,-77,96,-83,Material.IRON_BLOCK); block(-78,97,-84,Material.END_ROD);
        sign(-78,85,-80,BlockFace.SOUTH,"DUNE II STARPORT","Land / Launch","Arrival apron ahead","Five departures");
        // Compact grounded frigate on the central apron, with access around all sides.
        box(-81,83,-58,-75,85,-48,Material.SMOOTH_QUARTZ);
        box(-80,84,-47,-76,85,-46,Material.SMOOTH_QUARTZ);
        box(-80,86,-57,-76,87,-51,Material.IRON_BLOCK);
        box(-79,85,-45,-77,86,-45,Material.CYAN_STAINED_GLASS);
        box(-85,83,-56,-82,83,-50,Material.GRAY_CONCRETE);
        box(-74,83,-56,-71,83,-50,Material.GRAY_CONCRETE);
        for(int x:new int[]{-82,-74}) { block(x,82,-55,Material.POLISHED_BLACKSTONE_WALL); block(x,82,-49,Material.POLISHED_BLACKSTONE_WALL); }
        for(int x:new int[]{-80,-76}) block(x,84,-59,Material.SEA_LANTERN);
        for(int z=-27;z<=-5;z+=4) { block(-67,80,z,Material.YELLOW_CONCRETE); block(-65,80,z,Material.YELLOW_CONCRETE); }
    }
    private void exchange() {
        for(int x=-82;x<=-50;x+=16) {
            box(x-5,81,24,x+5,82,26,Material.WAXED_CUT_COPPER);
            box(x-5,81,25,x-5,86,25,Material.DARK_OAK_LOG); box(x+5,81,25,x+5,86,25,Material.DARK_OAK_LOG);
            box(x-5,87,24,x+5,87,26,Material.BLACK_WOOL);
            block(x,83,25,Material.LANTERN);
            sign(x,82,27,BlockFace.SOUTH,"CHOAM EXCHANGE","Official market","Right-click broker","to open exchange");
        }
        for(int x=-84;x<=-48;x+=6) { box(x,81,54,x+2,82,56,Material.BARREL); block(x+1,83,55,Material.LANTERN); }
        box(-76,81,40,-70,81,44,Material.DARK_OAK_PLANKS);
        box(-62,81,40,-56,81,44,Material.DARK_OAK_PLANKS);
        for(int x:new int[]{-74,-58}) { block(x,82,42,Material.AMETHYST_CLUSTER); block(x+2,82,42,Material.SEA_LANTERN); }
    }
    private void news() {
        for(int x=48;x<=76;x+=7) {
            box(x-2,81,-50,x+2,81,-48,Material.DARK_OAK_PLANKS);
            block(x,82,-49,Material.LECTERN); lamp(x,-0+81,-53,false);
        }
        box(50,83,-61,74,91,-61,Material.POLISHED_BLACKSTONE);
        sign(62,82,-60,BlockFace.SOUTH,"GUILD NEWS","Right-click lecterns","for current notices","Staff: config.yml");
        for(int z=-39;z<=-31;z+=4) box(51,81,z,73,81,z,Material.DARK_OAK_SLAB);
    }
    private void rules() {
        for(int x=48;x<=76;x+=7) {
            box(x-2,81,20,x+2,81,21,Material.DARK_OAK_PLANKS); block(x,82,21,Material.LECTERN);
            box(x-2,83,16,x+2,88,16,Material.SMOOTH_SANDSTONE);
        }
        sign(62,82,22,BlockFace.SOUTH,"GUILD CHARTER","Right-click lecterns","to read server rules","Respect travellers");
        for(int z=32;z<=48;z+=5) { box(48,81,z,57,81,z,Material.DARK_OAK_SLAB); box(67,81,z,76,81,z,Material.DARK_OAK_SLAB); }
    }
    private void smugglers() {
        // Staggered screens leave a two-block dogleg passage, not a hidden trap.
        box(-87,81,82,-72,86,82,Material.DARK_OAK_PLANKS);
        box(-60,81,87,-45,86,87,Material.DARK_OAK_PLANKS);
        for(int x:new int[]{-78,-54}) {
            box(x-5,81,106,x+5,82,108,Material.DARK_OAK_PLANKS);
            for(int dx:new int[]{-5,5}) box(x+dx,81,106,x+dx,87,106,Material.DARK_OAK_LOG);
            box(x-5,88,104,x+5,88,109,Material.BROWN_WOOL);
            box(x-4,88,105,x-2,88,108,Material.BLACK_WOOL);
            block(x-4,86,105,Material.LANTERN);
        }
        box(-83,83,106,-81,84,107,Material.BLUE_STAINED_GLASS);
        block(-82,83,104,Material.WATER_CAULDRON);
        box(-57,83,106,-55,83,107,Material.ORANGE_CONCRETE_POWDER);
        sign(-78,82,105,BlockFace.NORTH,"WATER SELLER","Quiet supplies","Right-click trader","Emerald barter");
        sign(-54,82,105,BlockFace.NORTH,"SPICE SELLER","Off the books","Right-click trader","Lobby samples");
        for(int x:new int[]{-84,-48}) for(int z=91;z<=114;z+=6) {
            box(x,81,z,x+2,82,z+2,Material.BARREL); block(x+1,83,z+1,Material.BROWN_SHULKER_BOX);
        }
        for(int z=85;z<=115;z+=5) { block(-86,85,z,Material.WAXED_WEATHERED_COPPER); block(-46,85,z,Material.WAXED_WEATHERED_COPPER); }
    }
    long build() {
        room(0,0,26,29,34,false);
        room(-78,-58,38,32,27,false);
        room(-66,40,26,24,23,false);
        room(62,-42,25,23,23,false);
        room(62,37,25,23,23,false);
        room(-66,101,24,23,16,true);
        corridor(-26,0,-66,0,false); corridor(-66,-29,-66,19,false);
        corridor(26,0,62,0,false); corridor(62,-22,62,17,false);
        corridor(-66,61,-66,81,true);
        navigator(); starport(); exchange(); news(); rules(); smugglers();
        // Arrival marker is unobstructed at (0.5,81,10.5), facing north to the tank.
        block(0,80,10,Material.LODESTONE);
        return count;
    }
}
