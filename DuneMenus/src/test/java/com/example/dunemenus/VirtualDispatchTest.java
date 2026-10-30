package com.example.dunemenus;
import org.junit.jupiter.api.Test;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class VirtualDispatchTest {
 static final class Harness {
  final DuneMenus plugin=mock(DuneMenus.class,CALLS_REAL_METHODS);final Player player=mock(Player.class);final UUID id=UUID.randomUUID();
  final Map<UUID,DuneMenus.View> views=new HashMap<>();final List<Runnable> queued=new ArrayList<>();final AtomicInteger calls=new AtomicInteger();final AtomicBoolean allowed=new AtomicBoolean(true);final BukkitScheduler scheduler=mock(BukkitScheduler.class);final DuneMenus.View view;
  Harness()throws Exception{
   when(player.getUniqueId()).thenReturn(id);when(player.isOnline()).thenReturn(true);
   Field f=DuneMenus.class.getDeclaredField("views");f.setAccessible(true);f.set(plugin,views);
   view=new DuneMenus.View(new ViewGate(id,new Object(),"a".repeat(64),System.currentTimeMillis()+20000,allowed::get),"Test",List.of(new DuneMenus.Card("Confirm","Exact reviewed action",calls::incrementAndGet,null,"")),new AtomicLong(1));views.put(id,view);
   doAnswer(c->{queued.add(c.getArgument(1));return null;}).when(scheduler).runTask(any(org.bukkit.plugin.Plugin.class),any(Runnable.class));
  }
  void click(String token,String index,String revision){plugin.onCommand(player,null,"dunemenus",new String[]{"action",token,index,revision});}
 }
 @Test void actionQueuesOnceAndRunsOnlyAfterTheGuardedMainThreadCheck()throws Exception{
  Harness h=new Harness();try(var api=mockStatic(Bukkit.class)){api.when(Bukkit::getScheduler).thenReturn(h.scheduler);h.click(h.view.gate().token,"0","1");h.click(h.view.gate().token,"0","1");assertEquals(1,h.queued.size());assertEquals(0,h.calls.get());h.queued.getFirst().run();assertEquals(1,h.calls.get());assertTrue(h.views.isEmpty());}
 }
 @Test void wrongTokenNegativeOrOutOfRangeAndObsoleteRenderCannotDispatch()throws Exception{
  Harness h=new Harness();try(var api=mockStatic(Bukkit.class)){api.when(Bukkit::getScheduler).thenReturn(h.scheduler);h.click("b".repeat(64),"0","1");h.click(h.view.gate().token,"-1","1");h.click(h.view.gate().token,"1","1");h.click(h.view.gate().token,"0","0");assertTrue(h.queued.isEmpty());assertEquals(0,h.calls.get());}
 }
 @Test void queuedActionCannotRunAfterDisconnectRevocationCancellationOrReplacement()throws Exception{
  for(int change=0;change<4;change++){Harness h=new Harness();try(var api=mockStatic(Bukkit.class)){api.when(Bukkit::getScheduler).thenReturn(h.scheduler);h.click(h.view.gate().token,"0","1");assertEquals(1,h.queued.size());switch(change){case 0->when(h.player.isOnline()).thenReturn(false);case 1->h.allowed.set(false);case 2->h.plugin.cancel(h.player);case 3->h.views.put(h.id,new DuneMenus.View(new ViewGate(h.id,new Object(),"new",System.currentTimeMillis()+20000,()->true),"New",List.of(),new AtomicLong()));}h.queued.getFirst().run();assertEquals(0,h.calls.get());}}
 }
 @Test void anotherPlayerCannotUseTheOwnersOpaqueButton()throws Exception{
  Harness h=new Harness();Player other=mock(Player.class);when(other.getUniqueId()).thenReturn(UUID.randomUUID());when(other.isOnline()).thenReturn(true);h.plugin.onCommand(other,null,"dunemenus",new String[]{"action",h.view.gate().token,"0","1"});assertEquals(0,h.calls.get());assertTrue(h.queued.isEmpty());
 }
 @Test void exactVirtualModelIdentityReplacesPhysicalContainerIdentity()throws Exception{
  Harness h=new Harness();org.bukkit.inventory.Inventory model=mock(org.bukkit.inventory.Inventory.class);DuneMenus.View view=new DuneMenus.View(new ViewGate(h.id,model,"opaque",System.currentTimeMillis()+20000,h.allowed::get),"View",List.of(),new AtomicLong());h.views.put(h.id,view);assertTrue(h.plugin.isCurrent(h.player,model));assertFalse(h.plugin.isCurrent(h.player,mock(org.bukkit.inventory.Inventory.class)));h.allowed.set(false);assertFalse(h.plugin.isCurrent(h.player,model));
 }
}
