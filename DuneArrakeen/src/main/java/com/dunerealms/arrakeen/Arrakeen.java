package com.dunerealms.arrakeen;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Console-only, UUID-fenced installer of our original offline block plan. No automatic world edits. */
public final class Arrakeen extends JavaPlugin implements CommandExecutor {
    private static final int W=416,H=88,L=256,OX=-294,OY=68,OZ=-120;
    private static final Material[] PALETTE={Material.AIR,Material.SANDSTONE,Material.SMOOTH_SANDSTONE,
        Material.CUT_SANDSTONE,Material.CHISELED_SANDSTONE,Material.YELLOW_TERRACOTTA,
        Material.DARK_PRISMARINE,Material.GREEN_TERRACOTTA,Material.TINTED_GLASS,
        Material.SEA_LANTERN,Material.SAND,Material.POLISHED_DEEPSLATE,Material.RED_SANDSTONE,Material.BROWN_TERRACOTTA};
    private byte[] blocks;
    private String hash;
    private boolean running;
    private final List<int[]> targets=new ArrayList<>();

    @Override public void onEnable() {
        try {
            byte[] raw=Objects.requireNonNull(getResource("plan.bin.gz")).readAllBytes();
            hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
            try(var in=new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(raw)))) {
                if(in.readInt()!=0x44524131 || in.readInt()!=W || in.readInt()!=H || in.readInt()!=L)throw new IOException("Unexpected plan dimensions");
                blocks=in.readNBytes(W*H*L);
                if(blocks.length!=W*H*L)throw new IOException("Incomplete plan");
                for(byte b:blocks)if(b<0||b>=PALETTE.length)throw new IOException("Invalid palette");
                int n=in.readInt();if(n<23||n>100)throw new IOException("Missing arrival checks");
                for(int i=0;i<n;i++)targets.add(new int[]{in.readInt(),in.readInt(),in.readInt()});
                if(in.read()!=-1)throw new IOException("Trailing plan bytes");
            }
            getCommand("arrakeen").setExecutor(this);
            getLogger().info("Original build loaded; no automatic edits. Plan SHA256="+hash);
        } catch(Exception e) {getLogger().severe("Installer disabled: "+e);getServer().getPluginManager().disablePlugin(this);}
    }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String alias,String[] args) {
        if(!(sender instanceof ConsoleCommandSender)){sender.sendMessage("This world installer is console-only.");return true;}
        World w=Bukkit.getWorld("arrakis");if(w==null){sender.sendMessage("Arrakis not loaded");return true;}
        if(args.length==0||args[0].equals("status")) {
            sender.sendMessage("ARRAKEEN_STATUS world="+w.getUID()+" plan="+hash+" complete="+getConfig().getBoolean("complete")+" column="+getConfig().getInt("column")+" running="+running);return true;
        }
        if(args[0].equals("verify")){verify(w);return true;}
        if(args[0].equals("sietch")&&args.length==3) {
            try { inspectSietch(w,Integer.parseInt(args[1]),Integer.parseInt(args[2])); }
            catch(NumberFormatException ex){sender.sendMessage("Integer x/z required");}return true;
        }
        if(args.length!=3||!args[0].equals("install")||!args[1].equals(w.getUID().toString())||!args[2].equals(hash)) {
            sender.sendMessage("Use arrakeen install <exact Arrakis UUID> <exact plan SHA256>; status shows both.");return true;
        }
        if(running||getConfig().getBoolean("complete")){sender.sendMessage("Already running or complete; refusing replay.");return true;}
        String old=getConfig().getString("world","");
        if(!old.isEmpty()&&!old.equals(w.getUID().toString())){sender.sendMessage("World UUID mismatch; refusing reuse.");return true;}
        getConfig().set("world",w.getUID().toString());saveConfig();running=true;
        new BukkitRunnable(){
            int column=getConfig().getInt("column",0);
            public void run(){
                try {
                    long until=System.nanoTime()+8_000_000L;int batch=0;
                    do {
                        int x=column%W,z=column/W,wx=OX+x,wz=OZ+z;
                        w.getChunkAt(wx>>4,wz>>4);
                        int high=Math.max(w.getHighestBlockYAt(wx,wz),OY+H-1);
                        int original=w.getHighestBlockYAt(wx,wz);
                        if(original<OY-1)for(int y=original+1;y<OY;y++)w.getBlockAt(wx,y,wz).setType(Material.SANDSTONE,false);
                        for(int y=OY;y<=high;y++) {
                            Material material=y<OY+H?PALETTE[blocks[x+W*(z+L*(y-OY))]]:Material.AIR;
                            var b=w.getBlockAt(wx,y,wz);
                            if(b.getType()!=material)b.setType(material,false);
                        }
                        column++;batch++;
                        if(column%256==0){getConfig().set("column",column);saveConfig();}
                    } while(column<W*L&&batch<64&&System.nanoTime()<until);
                    if(column%8192<batch)getLogger().info("ARRAKEEN_PROGRESS "+column+"/"+(W*L));
                    if(column>=W*L){
                        w.setSpawnLocation(0,72,0,90);w.setGameRule(GameRule.SPAWN_RADIUS,0);
                        verify(w);w.save();getConfig().set("column",column);getConfig().set("complete",true);saveConfig();
                        running=false;cancel();getLogger().info("ARRAKEEN_COMPLETE world="+w.getUID()+" spawn=0,72,0");
                    }
                } catch(Exception e){running=false;cancel();getLogger().severe("ARRAKEEN_FAILED column="+column+" "+e);}
            }
        }.runTaskTimer(this,1,1);
        return true;
    }
    private void verify(World w) {
        int pass=0;
        for(int[] t:targets) {
            int x=OX+t[0],y=OY+t[1],z=OZ+t[2];
            if(!w.getBlockAt(x,y-1,z).getType().isSolid()||!w.getBlockAt(x,y,z).getType().isAir()
                ||!w.getBlockAt(x,y+1,z).getType().isAir())throw new IllegalStateException("Blocked destination "+x+","+y+","+z);
            pass++;
        }
        getLogger().info("ARRAKEEN_VERIFY safeDestinations="+pass+" spawn="+w.getSpawnLocation().getBlockX()+","+w.getSpawnLocation().getBlockY()+","+w.getSpawnLocation().getBlockZ());
    }
    private void inspectSietch(World w,int x,int z) {
        // Deliberate console audit: load the entire footprint before examining actual blocks.
        for(int cx=Math.floorDiv(x-40,16);cx<=Math.floorDiv(x+40,16);cx++)
            for(int cz=Math.floorDiv(z-40,16);cz<=Math.floorDiv(z+40,16);cz++)w.getChunkAt(cx,cz);
        int surface=70+(int)Math.round((Math.sin(x*.02)*Math.cos(z*.02*1.13)+Math.sin(x*.02*2.7+z*.02*1.45)*.35)*11);
        int hy=surface-11,water=0,leaks=0;
        for(int dx=-5;dx<=5;dx++)for(int dz=-5;dz<=5;dz++)if(dx*dx+dz*dz<=25) {
            if(w.getBlockAt(x+dx,hy-29,z+8+dz).getType()!=Material.STONE)leaks++;
            for(int yy=hy-28;yy<=hy-25;yy++) {
                if(w.getBlockAt(x+dx,yy,z+8+dz).getType()==Material.WATER)water++;else leaks++;
                for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
                    Material m=w.getBlockAt(x+dx+d[0],yy,z+8+dz+d[1]).getType();
                    if(m!=Material.WATER&&m!=Material.STONE)leaks++;
                }
            }
        }
        Material chest=w.getBlockAt(x,hy-14,z-10).getType();
        boolean rooms=w.getBlockAt(x-18,hy-7,z-26).getType().isAir()&&w.getBlockAt(x+18,hy-7,z-26).getType().isAir();
        getLogger().info("SIETCH_VERIFY x="+x+" z="+z+" water="+water+" leaks="+leaks+" chest="+chest+" extraRooms="+rooms);
        if(water!=324||leaks!=0||chest!=Material.CHEST||!rooms)throw new IllegalStateException("Sietch audit failed");
    }
}
