package com.example.dunerealms;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class JobGuiSessionTest {
    static final class Fixture implements AutoCloseable {
        final ConfigurableGui gui=mock(ConfigurableGui.class,CALLS_REAL_METHODS);
        final DuneCore plugin=mock(DuneCore.class);final Player player=mock(Player.class);
        final Inventory top=mock(Inventory.class),bottom=mock(Inventory.class);
        final BukkitScheduler scheduler=mock(BukkitScheduler.class);final MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);
        final List<Runnable> queued=new ArrayList<>(),expiry=new ArrayList<>();final AtomicReference<Inventory> current=new AtomicReference<>();
        final AtomicInteger actions=new AtomicInteger();
        Fixture()throws Exception{
            for(String field:List.of("opened","actions","jobScreens"))set(field,new HashMap<>());set("jobInventories",new IdentityHashMap<>());set("plugin",plugin);
            when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-4000-8000-000000000001"));when(player.isOnline()).thenReturn(true);
            InventoryView view=mock(InventoryView.class);when(view.getTopInventory()).thenAnswer(i->current.get());when(player.getOpenInventory()).thenReturn(view);
            when(player.openInventory(any(Inventory.class))).thenAnswer(i->{current.set(i.getArgument(0));return view;});doAnswer(i->{current.set(bottom);return null;}).when(player).closeInventory();
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTask(eq(plugin),any(Runnable.class))).thenAnswer(i->{queued.add(i.getArgument(1));return mock(BukkitTask.class);});
            when(scheduler.runTaskLater(eq(plugin),any(Runnable.class),eq(2400L))).thenAnswer(i->{expiry.add(i.getArgument(1));return mock(BukkitTask.class);});
            show(top);
        }
        void set(String name,Object value)throws Exception{Field f=ConfigurableGui.class.getDeclaredField(name);f.setAccessible(true);f.set(gui,value);}
        void show(Inventory inventory)throws Exception{Method m=ConfigurableGui.class.getDeclaredMethod("jobScreen",Player.class,String.class,Inventory.class,Map.class);m.setAccessible(true);m.invoke(gui,player,"Jobs",inventory,Map.of(10,(Runnable)actions::incrementAndGet));}
        InventoryClickEvent click(Player who,Inventory clicked,ClickType type){InventoryClickEvent e=mock(InventoryClickEvent.class);InventoryView view=mock(InventoryView.class);when(view.getTopInventory()).thenReturn(top);when(e.getView()).thenReturn(view);when(e.getWhoClicked()).thenReturn(who);when(e.getClickedInventory()).thenReturn(clicked);when(e.getRawSlot()).thenReturn(10);when(e.getClick()).thenReturn(type);return e;}
        public void close(){bukkit.close();}
    }
    @Test void actualClickDispatchesOnceAfterQueueAndNeverMovesTheCard()throws Exception{
        try(Fixture f=new Fixture()){var first=f.click(f.player,f.top,ClickType.LEFT);f.gui.click(first);f.gui.click(f.click(f.player,f.top,ClickType.LEFT));verify(first).setCancelled(true);assertEquals(1,f.queued.size());assertEquals(0,f.actions.get());f.queued.getFirst().run();assertEquals(1,f.actions.get());verify(f.player).closeInventory();}
    }
    @Test void bottomShiftClicksDragsAndDoubleClicksAreCancelledWithoutActions()throws Exception{
        try(Fixture f=new Fixture()){for(var click:List.of(f.click(f.player,f.bottom,ClickType.SHIFT_LEFT),f.click(f.player,f.top,ClickType.DOUBLE_CLICK),f.click(f.player,f.top,ClickType.NUMBER_KEY))){f.gui.click(click);verify(click).setCancelled(true);}var drag=mock(InventoryDragEvent.class);var view=mock(InventoryView.class);when(view.getTopInventory()).thenReturn(f.top);when(drag.getView()).thenReturn(view);f.gui.drag(drag);verify(drag).setCancelled(true);assertTrue(f.queued.isEmpty());}
    }
    @Test void anotherPlayerCannotUseTheOwnersInventory()throws Exception{
        try(Fixture f=new Fixture()){Player other=mock(Player.class);when(other.getUniqueId()).thenReturn(UUID.randomUUID());var event=f.click(other,f.top,ClickType.LEFT);f.gui.click(event);verify(event).setCancelled(true);assertTrue(f.queued.isEmpty());}
    }
    @Test void closingOrReplacingTheScreenInvalidatesQueuedConfirmations()throws Exception{
        try(Fixture f=new Fixture()){f.gui.click(f.click(f.player,f.top,ClickType.LEFT));var close=mock(InventoryCloseEvent.class);when(close.getInventory()).thenReturn(f.top);f.gui.closeJobScreen(close);f.queued.getFirst().run();assertEquals(0,f.actions.get());}
        try(Fixture f=new Fixture()){f.gui.click(f.click(f.player,f.top,ClickType.LEFT));f.show(mock(Inventory.class));f.queued.getFirst().run();assertEquals(0,f.actions.get());}
    }
    @Test void expiredOrNoLongerCurrentViewsCannotRunAQueuedAction()throws Exception{
        try(Fixture f=new Fixture()){f.gui.click(f.click(f.player,f.top,ClickType.LEFT));f.current.set(f.bottom);f.queued.getFirst().run();assertEquals(0,f.actions.get());}
        try(Fixture f=new Fixture()){f.gui.click(f.click(f.player,f.top,ClickType.LEFT));f.expiry.getFirst().run();f.queued.getFirst().run();assertEquals(0,f.actions.get());verify(f.player).closeInventory();}
    }
}
