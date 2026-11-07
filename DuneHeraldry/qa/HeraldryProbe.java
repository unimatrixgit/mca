import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Banner;
import org.bukkit.block.banner.Pattern;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.plugin.java.JavaPlugin;

/** Runs only in an isolated loopback test world, never installed on a live backend. */
public class HeraldryProbe extends JavaPlugin {
 public void onEnable() {getServer().getScheduler().runTaskLater(this,this::check,40);}
 void check() {
  try {
   if (!getServer().getIp().equals("127.0.0.1")) throw new IllegalStateException("Loopback stage required");
   World world=Objects.requireNonNull(getServer().getWorld("heraldry_test"));
   boolean persisted=getDataFolder().toPath().resolve("placed").toFile().exists();
   int index=0;
   for (String name:List.of("atreides","corrino","harkonnen","fremen","ordos")) {
    var key=new NamespacedKey("dunerealms",name);
    var pattern=Objects.requireNonNull(Registry.BANNER_PATTERN.get(key));
    var block=world.getBlockAt(index++*3,80,0);
    if (!persisted) {
     block.getRelative(0,0,1).setType(Material.STONE);
     block.setType(Material.BLACK_WALL_BANNER,false);
     var data=(org.bukkit.block.data.Directional)block.getBlockData();data.setFacing(org.bukkit.block.BlockFace.NORTH);block.setBlockData(data,false);
     Banner banner=(Banner)block.getState();banner.setPatterns(List.of(new Pattern(DyeColor.WHITE,pattern)));banner.update(true,false);
    }
    Banner read=(Banner)block.getState();
    if (read.numberOfPatterns()!=1 || !key.equals(Registry.BANNER_PATTERN.getKey(read.getPattern(0).getPattern()))) throw new IllegalStateException("Placed banner: "+name);
    var drops=block.getDrops(new ItemStack(Material.DIAMOND_AXE));
    if (drops.size()!=1) throw new IllegalStateException("Drop count: "+name);
    var dropped=drops.iterator().next();var meta=(BannerMeta)dropped.getItemMeta();
    if(meta.numberOfPatterns()!=1 || !key.equals(Registry.BANNER_PATTERN.getKey(meta.getPattern(0).getPattern()))) throw new IllegalStateException("Dropped pattern: "+name);
    var restored=ItemStack.deserializeBytes(dropped.serializeAsBytes());if(!restored.isSimilar(dropped))throw new IllegalStateException("Dropped item roundtrip");
   }
   world.save();getDataFolder().mkdirs();java.nio.file.Files.writeString(getDataFolder().toPath().resolve("placed"),"5");
   getLogger().info("HERALDRY_BLOCK_PROOF placed=5 drops=5 itemRoundtrips=5 persistedRestart="+persisted);
  } catch(Exception failure){getLogger().severe("HERALDRY_PROBE_FAILED "+failure);}
 }
}
