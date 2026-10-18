package com.example.dunelobby;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display;
import org.bukkit.inventory.ItemStack;
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
    /** Heavy carved room walls; the entire lobby shares a flat opaque ceiling. */
    private void room(int cx,int cz,int rx,int rz,int height,boolean shady) {
        for(int x=-rx;x<=rx;x++) for(int z=-rz;z<=rz;z++) {
            if(!inside(x,z,rx,rz)) continue;
            boolean edge = !inside(x+1,z,rx,rz)||!inside(x-1,z,rx,rz)||!inside(x,z+1,rx,rz)||!inside(x,z-1,rx,rz);
            block(cx+x,76,cz+z,Material.DEEPSLATE_TILES);
            block(cx+x,77,cz+z,Material.POLISHED_BLACKSTONE_BRICKS);
            block(cx+x,78,cz+z,Material.POLISHED_BLACKSTONE_BRICKS);
            block(cx+x,79,cz+z,Material.CHISELED_POLISHED_BLACKSTONE);
            Material floor = Math.floorMod(Math.floorDiv(x,4)+Math.floorDiv(z,4),2)==0?Material.DEEPSLATE_TILES:Material.POLISHED_BLACKSTONE_BRICKS;
            if(Math.abs(x)==rx-3 || Math.abs(z)==rz-3) floor=shady?Material.WAXED_WEATHERED_COPPER:Material.GILDED_BLACKSTONE;
            if(Math.abs(x)==rx-5 || Math.abs(z)==rz-5) floor=shady?Material.CRACKED_DEEPSLATE_BRICKS:Material.WAXED_CUT_COPPER;
            block(cx+x,80,cz+z,floor);
            int wallTop=FLOOR+height;
            if(edge) {
                for(int y=65;y<=wallTop;y++) {
                    Material m=Material.POLISHED_BLACKSTONE_BRICKS;
                    if(y>=85 && y<wallTop-3) m=Material.CHISELED_POLISHED_BLACKSTONE;
                    if(y==76 || y==79 || y==84 || y==wallTop-3) m=shady?Material.WAXED_WEATHERED_COPPER:Material.WAXED_CUT_COPPER;
                    if(y==wallTop-1) m=Material.CHISELED_POLISHED_BLACKSTONE;
                    block(cx+x,y,cz+z,m);
                }
                if(Math.floorMod(x+z,3)==0) block(cx+x,wallTop+1,cz+z,Material.POLISHED_BLACKSTONE_BRICK_SLAB);
            }
            block(cx+x,wallTop+4,cz+z,Material.BLACK_CONCRETE);
            if(Math.floorMod(x,12)==0&&Math.floorMod(z,12)==0) block(cx+x,wallTop+3,cz+z,Material.SHROOMLIGHT);
        }
        for(int z=-rz+8;z<=rz-8;z+=8) for(int s:new int[]{-1,1}) pillar(cx+s*(rx-1),cz+z,80+height,shady);
        for(int x=-rx+8;x<=rx-8;x+=8) for(int s:new int[]{-1,1}) pillar(cx+x,cz+s*(rz-1),80+height,shady);
        for(int x=-rx+8;x<=rx-16;x+=8) for(int s:new int[]{-1,1}) wallArch(cx+x,cz+s*(rz-1),true,80+height,shady);
        for(int z=-rz+8;z<=rz-16;z+=8) for(int s:new int[]{-1,1}) wallArch(cx+s*(rx-1),cz+z,false,80+height,shady);
        for(int x=-rx+7;x<=rx-7;x+=8) for(int s:new int[]{-1,1}) {
            if(!shady) lamp(cx+x,81,cz+s*(rz-5),false);
        }
        for(int z=-rz+4;z<=rz-4;z++) {
            block(cx,80,cz+z,shady?Material.WAXED_WEATHERED_COPPER:Material.GOLD_BLOCK);
            if(!shady && Math.floorMod(z,6)==0) { block(cx-1,80,cz+z,Material.WAXED_CUT_COPPER); block(cx+1,80,cz+z,Material.WAXED_CUT_COPPER); }
        }
    }
    private void pillar(int x,int z,int top,boolean shady) {
        box(x-2,65,z-2,x+2,78,z+2,Material.DEEPSLATE_BRICKS);
        box(x-2,79,z-2,x+2,82,z+2,Material.CHISELED_POLISHED_BLACKSTONE);
        box(x-1,83,z-1,x+1,top,z+1,Material.POLISHED_BLACKSTONE_BRICKS);
        for(int y:new int[]{83,89,top-2}) {
            box(x-1,y,z-1,x+1,y,z+1,shady?Material.WAXED_WEATHERED_COPPER:Material.WAXED_CUT_COPPER);
            block(x,y,z,Material.GOLD_BLOCK);
        }
        box(x-2,top,z-2,x+2,top+1,z+2,Material.CHISELED_POLISHED_BLACKSTONE);
        block(x,top+2,z,Material.LANTERN);
        for(int s:new int[]{-1,1}) { block(x+s,top+2,z,Material.LANTERN); block(x,top+2,z+s,Material.LANTERN); }
        for(int y=85;y<=top-4;y++) for(int s:new int[]{-1,1}) {
            block(x+s*2,y,z,Material.AMETHYST_BLOCK);
            if(y%3==0) block(x+s*2,y+1,z,Material.END_ROD);
        }
    }
    private void wallArch(int x,int z,boolean acrossX,int top,boolean shady) {
        for(int u=0;u<=8;u++) {
            int y=top-7+(int)Math.round(5*Math.sqrt(Math.max(0,1-Math.pow((u-4)/4.0,2))));
            int px=x+(acrossX?u:0),pz=z+(acrossX?0:u);
            box(px,y,pz,px,y+2,pz,Material.DEEPSLATE_BRICKS);
            block(px,y-1,pz,shady?Material.WAXED_WEATHERED_COPPER:Material.GILDED_BLACKSTONE);
        }
    }
    private void enclosure() {
        // Simple flat ceiling and opaque walls: the End sky cannot be seen from the lobby.
        box(-126,59,-98,96,60,130,Material.BLACK_CONCRETE);
        box(-126,128,-98,96,129,130,Material.BLACK_CONCRETE);
        box(-126,61,-98,-126,127,130,Material.BLACK_CONCRETE);
        box(96,61,-98,96,127,130,Material.BLACK_CONCRETE);
        box(-125,61,-98,95,127,-98,Material.BLACK_CONCRETE);
        box(-125,61,130,95,127,130,Material.BLACK_CONCRETE);
        for(int x=-120;x<=88;x+=16) for(int z=-92;z<=124;z+=16) {
            block(x,127,z,Material.SHROOMLIGHT); // Flush, flat ceiling lighting.
        }
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
        box(-17,81,-28,17,83,-10,Material.POLISHED_BLACKSTONE_BRICKS);
        box(-16,84,-28,16,85,-10,Material.CHISELED_POLISHED_BLACKSTONE);
        box(-15,86,-28,15,86,-10,Material.WAXED_CUT_COPPER);
        for(int z=-1;z>=-12;z--) {
            int y=80+(int)Math.ceil(-z/2.0);
            box(-6,81,z,6,y,z,Material.POLISHED_BLACKSTONE_BRICKS);
            for(int x:new int[]{-6,6}) block(x,y,z,Material.GOLD_BLOCK);
            for(int x:new int[]{-8,8}) { block(x,y+1,z,Material.POLISHED_BLACKSTONE_BRICK_WALL); if(z%3==0) block(x,y+2,z,Material.LANTERN); }
        }
        box(-10,87,-27,10,89,-15,Material.WAXED_CUT_COPPER);
        box(-9,90,-26,9,121,-14,Material.ORANGE_STAINED_GLASS);
        box(-8,91,-25,8,120,-15,Material.AIR);
        box(-10,122,-27,10,123,-14,Material.WAXED_CUT_COPPER);
        box(-9,124,-26,9,124,-15,Material.CHISELED_POLISHED_BLACKSTONE);
        for(int x:new int[]{-11,11}) {
            box(x-1,87,-27,x+1,124,-14,Material.POLISHED_BLACKSTONE_BRICKS);
            for(int y=89;y<=123;y+=4) {
                box(x-1,y,-15,x+1,y,-13,Material.WAXED_OXIDIZED_COPPER);
                block(x,y,-12,Material.GOLD_BLOCK);
            }
            for(int y=91;y<=119;y+=7) { block(x, y,-12,Material.LANTERN); }
        }
        for(int x=-8;x<=8;x+=4) for(int z:new int[]{-24,-16}) { block(x,90,z,Material.SHROOMLIGHT); block(x,121,z,Material.SHROOMLIGHT); }
        // Oversized folded aquatic body, bulbous head, ribbed trunk and fanned hands.
        ellipsoid(0,105,-21,4.0,8.3,3.0,Material.SMOOTH_SANDSTONE);
        ellipsoid(0,115,-20,5.6,4.3,3.5,Material.CUT_SANDSTONE);
        ellipsoid(0,112,-17,3.5,2.1,1.5,Material.SMOOTH_SANDSTONE);
        ellipsoid(0,95,-22,5.7,2.7,2.6,Material.SANDSTONE);
        block(-3,115,-17,Material.BROWN_TERRACOTTA); block(3,115,-17,Material.BROWN_TERRACOTTA);
        block(0,111,-16,Material.BLACK_TERRACOTTA);
        for(int s:new int[]{-1,1}) {
            ellipsoid(s*4.6,104,-19,1.5,5.2,1.6,Material.CUT_SANDSTONE);
            ellipsoid(s*5.4,99,-19,2.7,1.4,2.3,Material.SMOOTH_SANDSTONE);
            for(int f=0;f<5;f++) {
                int end=s*(4+f);
                box(Math.min(s*3,end),100+f/2,-17-f/2,Math.max(s*3,end),100+f/2,-17-f/2,Material.SMOOTH_SANDSTONE);
            }
        }
        for(int y=99;y<=111;y+=2) for(int s:new int[]{-1,1}) box(s<0?-4:2,y,-18,s<0?-2:4,y,-17,Material.SANDSTONE);
        for(int x:new int[]{-14,14}) for(int z=-25;z<=-11;z+=5) lamp(x,87,z,false);
        box(11,87,-12,15,88,-12,Material.WAXED_CUT_COPPER);
        sign(13,88,-11,BlockFace.SOUTH,"SPACING GUILD","NAVIGATOR","Right-click liaison","for planet travel");
        for(int x=-18;x<=18;x++) for(int z=-5;z<=23;z++) {
            double r=Math.hypot(x,z-10);
            if((r>=11.5 && r<=12.5)||(r>=4.5&&r<=5.5)) block(x,80,z,Material.GOLD_BLOCK);
            if(r>12.5 && r<13.5) block(x,80,z,Material.WAXED_CUT_COPPER);
        }
        galleries();
    }
    private void galleries() {
        for(int x=-24;x<=24;x++) for(int z=-26;z<=26;z++) {
            if(!inside(x,z,24,26) || !(Math.abs(x)>=21 || z>=24)) continue;
            block(x,90,z,Material.POLISHED_BLACKSTONE_BRICKS);
            if(Math.abs(x)==21 || z==24) { block(x,91,z,Material.POLISHED_BLACKSTONE_BRICK_WALL); if(Math.floorMod(x+z,6)==0) block(x,92,z,Material.LANTERN); }
        }
        for(int s:new int[]{-1,1}) for(int i=0;i<10;i++) {
            int z=18-i,y=81+i;
            box(s<0?-24:22,81,z,s<0?-22:24,y,z,Material.POLISHED_BLACKSTONE_BRICKS);
            block(s*21,y+1,z,Material.WAXED_CUT_COPPER);
        }
        for(int s:new int[]{-1,1}) for(int z:new int[]{-19,-7,5,23}) {
            box(s<0?-24:22,81,z-1,s<0?-22:24,89,z+1,Material.DEEPSLATE_BRICKS);
            for(int y:new int[]{84,89}) block(s*22,y,z,Material.GOLD_BLOCK);
            block(s*20,85,z,Material.LANTERN);
        }
    }
    private void bridge(int x1,int z1,int x2,int z2,boolean shady) {
        int minX=Math.min(x1,x2)-4,maxX=Math.max(x1,x2)+4,minZ=Math.min(z1,z2)-4,maxZ=Math.max(z1,z2)+4;
        double vx=x2-x1,vz=z2-z1,length2=vx*vx+vz*vz;
        for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++) {
            double t=Math.max(0,Math.min(1,((x-x1)*vx+(z-z1)*vz)/length2));
            double d=Math.hypot(x-x1-t*vx,z-z1-t*vz);
            if(d>3.6) continue;
            box(x,76,z,x,79,z,Material.POLISHED_BLACKSTONE_BRICKS);
            block(x,80,z,d<.8?Material.GOLD_BLOCK:d>2.5?Material.WAXED_CUT_COPPER:Material.DEEPSLATE_TILES);
            box(x,81,z,x,88,z,Material.AIR);
            block(x,93,z,Material.POLISHED_BLACKSTONE_BRICKS);
            if(d>2.5) box(x,89,z,x,92,z,Material.CHISELED_POLISHED_BLACKSTONE);
        }
        for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++) {
            double t=Math.max(0,Math.min(1,((x-x1)*vx+(z-z1)*vz)/length2));
            double d=Math.hypot(x-x1-t*vx,z-z1-t*vz);
            if(d<2.5 || d>3.6 || !w.getBlockAt(x,80,z).getType().isSolid()) continue;
            boolean outer=false;
            for(int[] v:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) if(!w.getBlockAt(x+v[0],80,z+v[1]).getType().isSolid()) outer=true;
            if(!outer) continue;
            box(x,81,z,x,92,z,Material.POLISHED_BLACKSTONE_BRICKS);
            block(x,84,z,Material.WAXED_CUT_COPPER); block(x,90,z,Material.WAXED_CUT_COPPER);
            if(Math.floorMod(x+z,6)==0) {
                block(x,88,z,Material.SHROOMLIGHT);
                box(x,68,z,x,75,z,Material.DEEPSLATE_BRICKS); block(x,72,z,Material.WAXED_CUT_COPPER);
            }
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
    private void title(double x,double y,double z,String text) {
        w.spawn(new Location(w,x,y,z),TextDisplay.class,d->{
            d.setText(ChatColor.GOLD+text); d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(210,20,16,15)); d.setShadowed(true);
            d.setLineWidth(400); d.setPersistent(true);
        });
    }
    private void displayItem(int x,int y,int z,Material item) {
        block(x,y,z-1,Material.DARK_OAK_PLANKS);
        w.spawn(new Location(w,x+.5,y+.5,z+.5),ItemFrame.class,f->{
            f.setFacingDirection(BlockFace.SOUTH,true); f.setItem(new ItemStack(item),false);
            f.setFixed(true); f.setInvulnerable(true); f.setPersistent(true);
        });
    }
    private void furnishing() {
        // Dense layered stalls, bottles, catalogues and market displays.
        for(int x:new int[]{-82,-66,-50}) {
            box(x-5,83,21,x+5,88,21,Material.DARK_OAK_PLANKS);
            for(int y:new int[]{83,86,89}) box(x-5,y,21,x+5,y,22,Material.WAXED_CUT_COPPER);
            for(int i=0;i<5;i++) displayItem(x-4+i*2,84,23,new Material[]{Material.DIAMOND,Material.GOLD_INGOT,Material.ORANGE_DYE,Material.POTION,Material.EMERALD}[i]);
            for(int dx:new int[]{-3,3}) { block(x+dx,83,26,Material.BREWING_STAND); block(x+dx,87,26,Material.LANTERN); }
            title(x+.5,89,26.5,x==-66?"CHOAM":x==-82?"TRADE":"CONTRACTS");
        }
        for(int x:new int[]{-85,-47}) for(int z=34;z<=46;z+=4) {
            box(x-1,81,z-1,x+1,82,z+1,Material.DARK_OAK_PLANKS);
            block(x,83,z,Material.BREWING_STAND); block(x,83,z+1,Material.LANTERN);
        }
        for(int x:new int[]{-74,-58}) {
            block(x,82,41,Material.BREWING_STAND); block(x,82,43,Material.CANDLE);
            block(x-2,82,42,Material.DECORATED_POT); block(x+2,82,44,Material.LANTERN);
        }
        // Rules hall: proper seats with backs, aisle runner and illuminated charter panels.
        for(int z:new int[]{34,40,46,52}) for(int x=48;x<=76;x++) {
            if(x>=58&&x<=66) continue;
            block(x,81,z,Material.DARK_OAK_STAIRS);
            var data=(org.bukkit.block.data.type.Stairs)w.getBlockAt(x,81,z).getBlockData();
            data.setFacing(BlockFace.SOUTH); w.getBlockAt(x,81,z).setBlockData(data,false);
            block(x,82,z+1,Material.DARK_OAK_TRAPDOOR);
        }
        for(int z=27;z<=54;z++) for(int x=60;x<=64;x++) block(x,81,z,Material.BLACK_CARPET);
        for(int x:new int[]{48,55,69,76}) {
            box(x-2,83,16,x+2,91,16,Material.WAXED_CUT_COPPER);
            box(x-1,84,17,x+1,90,17,Material.SMOOTH_SANDSTONE);
            block(x-2,88,18,Material.LANTERN); block(x+2,88,18,Material.LANTERN);
        }
        // News room desks: ledgers, lamps, charts and side shelving.
        for(int x=48;x<=76;x+=7) {
            block(x+2,82,-49,Material.LANTERN); block(x-1,82,-50,Material.DECORATED_POT);
            box(x-2,81,-32,x+2,81,-32,Material.DARK_OAK_STAIRS);
        }
        for(int x:new int[]{42,82}) {
            box(x,81,-53,x,88,-33,Material.CHISELED_BOOKSHELF);
            for(int z=-52;z<=-34;z+=6) { block(x,89,z,Material.LANTERN); block(x,82,z,Material.WAXED_CUT_COPPER); }
        }
        // Smuggler den: pipework, patchwork canvas, mismatched stock and a recessed entrance.
        for(int x:new int[]{-78,-54}) {
            box(x-6,89,103,x+6,89,110,Material.BROWN_WOOL);
            for(int dx=-6;dx<=6;dx+=3) box(x+dx,89,103,x+dx,89,110,Material.ORANGE_TERRACOTTA);
            box(x-6,88,103,x+6,88,103,Material.DARK_OAK_SLAB);
            title(x+.5,90,104,x==-78?"WATER":"SPICE");
            block(x+3,83,107,Material.BREWING_STAND); block(x-3,83,107,Material.DECORATED_POT);
            box(x-4,81,113,x+4,83,114,Material.BARREL);
        }
        box(-87,90,84,-45,90,84,Material.WAXED_WEATHERED_COPPER);
        box(-87,90,84,-87,90,118,Material.WAXED_WEATHERED_COPPER);
        box(-45,90,84,-45,90,118,Material.WAXED_WEATHERED_COPPER);
        for(int z=86;z<=116;z+=6) {
            block(-87,89,z,Material.GRINDSTONE); block(-45,89,z,Material.GRINDSTONE);
            box(-85,81,z,-83,83,z+1,Material.BARREL); block(-84,84,z,Material.DARK_OAK_TRAPDOOR);
            box(-49,81,z,-47,82,z+1,Material.BARREL);
        }
        box(-71,81,83,-70,86,84,Material.DARK_OAK_LOG); block(-70,87,84,Material.SOUL_LANTERN);
        // Control tower and starport fittings echo Dune II's square industrial sprite.
        box(-84,91,-86,-72,100,-82,Material.SMOOTH_SANDSTONE);
        box(-82,95,-81,-74,98,-81,Material.CYAN_STAINED_GLASS);
        box(-85,101,-87,-71,102,-81,Material.WAXED_CUT_COPPER);
        for(int x:new int[]{-83,-73}) { box(x,103,-86,x,106,-86,Material.IRON_BARS); block(x,107,-86,Material.LANTERN); }
        title(-78,103,-80,"GUILD STARPORT");
        for(int[] p:PADS) {
            for(int s:new int[]{-1,1}) { block(p[0]+s*7,81,p[1]+5,Material.WAXED_CUT_COPPER); block(p[0]+s*7,82,p[1]+5,Material.LANTERN); }
            block(p[0]+5,83,p[1]-8,Material.LEVER);
        }
        frigate();
    }
    private void frigate() {
        box(-87,81,-61,-69,91,-44,Material.AIR);
        for(int z=-59;z<=-44;z++) {
            int width=z>=-47?Math.max(1,(-z-43)/2):3;
            box(-78-width,84,z,-78+width,86,z,Material.SMOOTH_QUARTZ);
            if(z<-50) box(-80,87,z,-76,87,z,Material.LIGHT_GRAY_CONCRETE);
            if(z%3==0) { block(-78-width,85,z,Material.POLISHED_ANDESITE); block(-78+width,85,z,Material.POLISHED_ANDESITE); }
        }
        box(-80,86,-49,-76,87,-48,Material.CYAN_STAINED_GLASS);
        for(int s:new int[]{-1,1}) {
            int x=-78+s*5;
            box(x-1,84,-58,x+1,86,-52,Material.SMOOTH_QUARTZ);
            block(x,85,-59,Material.SEA_LANTERN);
            box(Math.min(x,-78),83,-54,Math.max(x,-78),83,-51,Material.GRAY_CONCRETE);
            box(x,81,-56,x,83,-56,Material.IRON_BLOCK); box(x,81,-49,x,83,-49,Material.IRON_BLOCK);
        }
        box(-79,88,-58,-77,88,-55,Material.DEEPSLATE_TILES);
    }
    long build() {
        enclosure();
        room(0,0,26,29,40,false);
        room(-78,-58,38,32,16,false);
        room(-66,40,26,24,16,false);
        room(62,-42,25,23,16,false);
        room(62,37,25,23,16,false);
        room(-66,101,24,23,12,true);
        bridge(-22,-15,-43,-31,false); bridge(-22,17,-42,25,false);
        bridge(22,-15,39,-27,false); bridge(22,17,39,25,false);
        bridge(-66,61,-66,83,true);
        navigator(); starport(); exchange(); news(); rules(); smugglers();
        furnishing();
        // Arrival marker is unobstructed at (0.5,81,10.5), facing north to the tank.
        block(0,80,10,Material.LODESTONE);
        return count;
    }
}
