package com.example.duneterrain;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Villager;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Rich deposits are authenticated by a persistent chunk ledger, never by material alone. */
final class DenseSpice implements Listener {
    private final DuneTerrain plugin;
    private final NamespacedKey ledger = new NamespacedKey("dunerealms", "rich-spice-deposits");
    DenseSpice(DuneTerrain plugin) { this.plugin=plugin; }
    static long mix(long n) { n=(n^(n>>>30))*0xbf58476d1ce4e5b9L; n=(n^(n>>>27))*0x94d049bb133111ebL; return n^(n>>>31); }
    // One 30 x 30 patch per 32-chunk region: configurable village-like spacing, no vanilla structure dependency.
    static int[] anchor(long seed, int x, int z, int spacing) {
        int cx=Math.floorDiv(x,spacing), cz=Math.floorDiv(z,spacing);
        long hash=mix(seed^cx*0x9e3779b97f4a7c15L^cz*0xc2b2ae3d27d4eb4fL^0x44454e5345535049L);
        return new int[]{cx*spacing+16+(int)Math.floorMod(hash,spacing-62),cz*spacing+16+(int)Math.floorMod(mix(hash),spacing-62)};
    }
    static boolean contains(long seed,int x,int z,int spacing) {
        int[] a=anchor(seed,x,z,spacing); return x>=a[0]&&x<a[0]+30&&z>=a[1]&&z<a[1]+30;
    }
    static int encode(Block b) { return (b.getY()+2048)*256+(b.getX()&15)*16+(b.getZ()&15); }
    private int[] entries(Chunk c) { return c.getPersistentDataContainer().getOrDefault(ledger,PersistentDataType.INTEGER_ARRAY,new int[0]); }
    private boolean marked(Block b) { int key=encode(b); return Arrays.stream(entries(b.getChunk())).anyMatch(v->v==key); }
    private void consume(Block b) { int key=encode(b); b.getChunk().getPersistentDataContainer().set(ledger,PersistentDataType.INTEGER_ARRAY,Arrays.stream(entries(b.getChunk())).filter(v->v!=key).toArray()); }
    @EventHandler(priority=EventPriority.LOWEST) public void initialize(ChunkLoadEvent e) {
        if(!e.isNewChunk()||!e.getWorld().getName().equalsIgnoreCase("arrakis"))return;
        Chunk c=e.getChunk(); List<Integer> deposits=new ArrayList<>();
        for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
            Block b=e.getWorld().getHighestBlockAt(c.getX()*16+x,c.getZ()*16+z);
            if(b.getType()==Material.ORANGE_TERRACOTTA)deposits.add(encode(b));
        }
        c.getPersistentDataContainer().set(ledger,PersistentDataType.INTEGER_ARRAY,deposits.stream().mapToInt(Integer::intValue).toArray());
        if(deposits.isEmpty())return;
        int spacing=Math.max(128,plugin.getConfig().getInt("dense-spice.spacing",512));
        int[] a=anchor(e.getWorld().getSeed(),c.getX()*16+8,c.getZ()*16+8,spacing);
        if(Math.floorDiv(a[0]+15,16)!=c.getX()||Math.floorDiv(a[1]+15,16)!=c.getZ())return;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!c.isLoaded())return;
            Location loc=e.getWorld().getHighestBlockAt(a[0]+15,a[1]+15).getLocation().add(.5,1,.5);
            Villager seller=e.getWorld().spawn(loc,Villager.class,v->{
                v.setCustomName(ChatColor.GOLD+"Spice Seller · +35%");v.setCustomNameVisible(true);
                v.setAI(false);v.setInvulnerable(true);v.setPersistent(true);v.setRemoveWhenFarAway(false);
                v.getPersistentDataContainer().set(new NamespacedKey("dunerealms","spice-seller"),PersistentDataType.BYTE,(byte)1);
            });
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void harvest(BlockBreakEvent e) {
        Block b=e.getBlock(); if(!marked(b))return;
        e.setDropItems(false);e.setExpToDrop(0);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void reward(BlockBreakEvent e) {
        Block b=e.getBlock();if(!marked(b))return;consume(b);
        if(b.getType()==Material.ORANGE_TERRACOTTA && e.getPlayer().getGameMode()!=GameMode.CREATIVE)
            b.getWorld().dropItemNaturally(b.getLocation(),DuneTerrain.spiceItem(Math.max(1,Math.min(64,plugin.getConfig().getInt("dense-spice.yield",4)))));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void place(BlockPlaceEvent e) { if(marked(e.getBlock()))consume(e.getBlock()); }
    @EventHandler(ignoreCancelled=true) public void push(BlockPistonExtendEvent e) { if(e.getBlocks().stream().anyMatch(this::marked))e.setCancelled(true); }
    @EventHandler(ignoreCancelled=true) public void pull(BlockPistonRetractEvent e) { if(e.getBlocks().stream().anyMatch(this::marked))e.setCancelled(true); }
    @EventHandler(ignoreCancelled=true) public void explode(EntityExplodeEvent e) { e.blockList().removeIf(this::marked); }
    @EventHandler(ignoreCancelled=true) public void explode(BlockExplodeEvent e) { e.blockList().removeIf(this::marked); }
}
