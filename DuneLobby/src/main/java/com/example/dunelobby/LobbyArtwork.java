package com.example.dunelobby;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Future;

/** Uses ImageFrame's published loader API through reflection; no vendored third-party code. */
final class LobbyArtwork {
    private final DuneLobby plugin;
    private final World world;
    private final NamespacedKey key;
    private static final java.util.concurrent.atomic.AtomicBoolean INSTALLING=new java.util.concurrent.atomic.AtomicBoolean();
    private static final UUID OWNER=UUID.fromString("79a66e02-e519-4489-a153-89f0037d7d11");
    private record Panel(String name,String file,int x,int y,int z,int width,int height) {}
    private static final List<Panel> PANELS=List.of(
        new Panel("guild","guild-navigator.png",-3,85,26,6,4),
        new Panel("choam","exchange.png",-88,85,20,5,3),
        new Panel("expedition","spice-expedition.png",-54,85,20,5,3),
        new Panel("news","news.png",57,85,-60,10,5),
        new Panel("rules","rules.png",58,85,17,8,5),
        new Panel("smugglers","smuggler-water-camp.png",-70,84,122,8,4),
        new Panel("arrakis","world-0.png",-110,88,-87,5,4),
        new Panel("giedi","world-1.png",-100,88,-87,5,4),
        new Panel("kaitain","world-2.png",-90,88,-87,5,4),
        new Panel("caladan","world-3.png",-68,88,-87,5,4),
        new Panel("sigma","world-4.png",-58,88,-87,5,4),
        new Panel("navigator_left","guild-navigator.png",-23,103,-26,6,10),
        new Panel("navigator_right","arrakis-cover.png",18,103,-26,6,10),
        new Panel("choam_history_south","spice-expedition.png",-86,85,60,10,6),
        new Panel("choam_arrakis_south","arrakis-cover.png",-56,85,60,10,6),
        new Panel("news_caladan","world-3.png",44,85,-60,6,6),
        new Panel("news_kaitain","world-2.png",73,85,-60,6,6),
        new Panel("news_expedition_south","spice-expedition.png",47,85,-22,8,6),
        new Panel("news_sandworm_south","shai-hulud-encounter.png",69,85,-22,8,6),
        new Panel("charter_sietch_south","sietch-panorama.png",45,85,56,10,6),
        new Panel("charter_arrakis_south","arrakis-cover.png",70,85,56,10,6),
        new Panel("starport_guild_south","guild-navigator.png",-110,86,-29,10,7),
        new Panel("starport_arrakis_south","arrakis-cover.png",-59,86,-29,10,7),
        new Panel("smuggler_water","smuggler-water-camp.png",-85,84,84,8,5),
        new Panel("smuggler_spice","smuggler-spice-mission.png",-57,84,84,8,5));
    LobbyArtwork(DuneLobby plugin,World world) { this.plugin=plugin; this.world=world; key=new NamespacedKey(plugin,"art"); }
    static int tileCount() { return PANELS.stream().mapToInt(p->p.width*p.height).sum(); }
    static int panelCount() { return PANELS.size(); }
    void restore() { install(); }
    void install() {
        if(!Bukkit.getPluginManager().isPluginEnabled("ImageFrame")) { plugin.getLogger().warning("Install ImageFrame 2026.1.5.0 to restore image decorations; structural lobby remains usable."); return; }
        if(!INSTALLING.compareAndSet(false,true)) { plugin.getLogger().info("Artwork restoration is already running."); return; }
        // Creation waits for ImageFrame's sync map allocation: run OFF the server thread.
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()-> {
            try {
                ClassLoader cl=Bukkit.getPluginManager().getPlugin("ImageFrame").getClass().getClassLoader();
                Class<?> main=cl.loadClass("com.loohp.imageframe.ImageFrame"); Object manager=main.getField("imageMapManager").get(null);
                Class<?> managerType=cl.loadClass("com.loohp.imageframe.objectholders.ImageMapManager");
                // ImageFrame loads saved maps asynchronously after enable. Avoid creating duplicate
                // names/map IDs while its storage is still being restored.
                Object storage=managerType.getMethod("getStorage").invoke(manager);
                Class<?> storageType=cl.loadClass("com.loohp.imageframe.storage.ImageFrameStorage");
                for(int attempt=0;;attempt++) {
                    var saved=(Set<?>)storageType.getMethod("getAllImageIndexes").invoke(storage);
                    var loaded=(Collection<?>)managerType.getMethod("getMaps").invoke(manager);
                    if(loaded.size()>=saved.size()) break;
                    if(attempt>=60) throw new IllegalStateException("ImageFrame data did not finish loading; retry dunelobby artwork after checking its logs.");
                    Thread.sleep(1000);
                }
                Class<?> infoType=cl.loadClass("com.loohp.imageframe.objectholders.NonUpdatableImageMapCreateInfo");
                Class<?> mapType=cl.loadClass("com.loohp.imageframe.objectholders.NonUpdatableStaticImageMap");
                Class<?> dithering=cl.loadClass("com.loohp.imageframe.objectholders.DitheringType");
                Object dither=dithering.getMethod("fromName",String.class).invoke(null,"floyd-steinberg");
                Class<?> loaders=cl.loadClass("com.loohp.imageframe.objectholders.ImageMapLoaders");
                Object loader=loaders.getMethod("getLoader",Class.class,Class.class,String.class,org.bukkit.command.CommandSender.class).invoke(null,mapType,infoType,"image/png",Bukkit.getConsoleSender());
                for(Panel p:PANELS) {
                    String name="dune_lobby_"+p.name;
                    Object map=managerType.getMethod("getFromCreator",UUID.class,String.class).invoke(manager,OWNER,name);
                    if(map==null) {
                        BufferedImage original;
                        try(var input=plugin.getResource("artwork/"+p.file)) { if(input==null) throw new IllegalStateException("Missing artwork "+p.file); original=ImageIO.read(input); }
                        // Map tiles are a display-format conversion; ImageFrame stores and renders them.
                        BufferedImage canvas=new BufferedImage(p.width*128,p.height*128,BufferedImage.TYPE_INT_ARGB);
                        var g=canvas.createGraphics(); g.drawImage(original,0,0,canvas.getWidth(),canvas.getHeight(),null); g.dispose();
                        BufferedImage[] tiles=new BufferedImage[p.width*p.height];
                        for(int i=0;i<tiles.length;i++) tiles[i]=canvas.getSubimage(i%p.width*128,i/p.width*128,128,128);
                        Object info=infoType.getConstructor(managerType,String.class,BufferedImage[].class,int.class,int.class,dithering,UUID.class).newInstance(manager,name,tiles,p.width,p.height,dither,OWNER);
                        map=((Future<?>)loader.getClass().getMethod("create",infoType).invoke(loader,info)).get();
                        managerType.getMethod("addMap",cl.loadClass("com.loohp.imageframe.objectholders.ImageMap")).invoke(manager,map);
                    }
                    @SuppressWarnings("unchecked") List<MapView> views=(List<MapView>)map.getClass().getMethod("getMapViews").invoke(map);
                    Bukkit.getScheduler().callSyncMethod(plugin,()-> { mount(p,views); return null; }).get();
                }
                Bukkit.getScheduler().runTask(plugin,()-> { world.save(); plugin.getLogger().info("GUILD_ARTWORK_COMPLETE: "+PANELS.size()+" ImageFrame panels installed."); });
            } catch(Exception e) { plugin.getLogger().log(java.util.logging.Level.SEVERE,"ImageFrame artwork failed",e); }
            finally { INSTALLING.set(false); }
        });
    }
    private void mount(Panel p,List<MapView> views) {
        Map<String,ItemFrame> existing=new HashMap<>();
        for(ItemFrame frame:world.getEntitiesByClass(ItemFrame.class)) {
            String tag=frame.getPersistentDataContainer().get(key,PersistentDataType.STRING); if(tag!=null) existing.put(tag,frame);
        }
        boolean facesNorth=p.name.equals("guild")||p.name.equals("smugglers")||p.name.endsWith("_south");
        int backing=p.z+(facesNorth?1:-1);
        for(int x=p.x-1;x<=p.x+p.width;x++) for(int y=p.y-1;y<=p.y+p.height;y++) {
            if(x!=p.x-1&&x!=p.x+p.width&&y!=p.y-1&&y!=p.y+p.height) continue;
            world.getBlockAt(x,y,backing).setType(Material.WAXED_CUT_COPPER,false);
        }
        for(int x:new int[]{p.x-2,p.x+p.width+1}) {
            for(int y=p.y-1;y<=p.y+p.height;y++) world.getBlockAt(x,y,backing).setType(Material.CHISELED_POLISHED_BLACKSTONE,false);
            world.getBlockAt(x,p.y+p.height+1,backing).setType(Material.LANTERN,false);
        }
        for(int row=0;row<p.height;row++) for(int col=0;col<p.width;col++) {
            boolean north=facesNorth;
            int x=p.x+(north?p.width-1-col:col),y=p.y+p.height-1-row,z=p.z; String tag=p.name+":"+row+":"+col;
            BlockFace face=north?BlockFace.NORTH:BlockFace.SOUTH;
            // South-wall panels face into the room; reverse column order for that view.
            if(north) world.getBlockAt(x,y,z-1).setType(Material.AIR,false);
            world.getBlockAt(x,y,z+(north?1:-1)).setType(Material.POLISHED_BLACKSTONE,false);
            world.getBlockAt(x,y,z).setType(Material.AIR,false);
            ItemFrame frame=existing.get(tag);
            if(frame!=null && (frame.getFacing()!=face || frame.getLocation().getBlockX()!=x)) { frame.remove(); frame=null; }
            if(frame==null) frame=world.spawn(new Location(world,x+.5,y+.5,z+.5),GlowItemFrame.class,f->f.setFacingDirection(face,true));
            frame.setVisible(false); frame.setFixed(true); frame.setInvulnerable(true); frame.setPersistent(true);
            frame.getPersistentDataContainer().set(key,PersistentDataType.STRING,tag);
            ItemStack item=new ItemStack(Material.FILLED_MAP); MapMeta meta=(MapMeta)item.getItemMeta(); meta.setMapView(views.get(row*p.width+col)); item.setItemMeta(meta); frame.setItem(item,false);
        }
    }
}
