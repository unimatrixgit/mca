package com.example.duneess;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.math.BigDecimal;
import org.bukkit.Material;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExchangeMenuTest {
    @Test void artworkTargetsNavigationAndNeverPlayerSalePreviews() {
        assertEquals("gui_market",ExchangeMenu.navigationModel("§6Add Items / Sell"));
        assertEquals("gui_market",ExchangeMenu.navigationModel("Choose Another Item"));
        assertEquals("gui_confirm",ExchangeMenu.navigationModel("Review Listing"));
        assertEquals("gui_confirm",ExchangeMenu.navigationModel("Confirm / Add Listing"));
        assertEquals("gui_confirm",ExchangeMenu.navigationModel("Confirm Purchase"));
        assertEquals("gui_confirm",ExchangeMenu.navigationModel("Confirm Reclaim"));
        assertEquals("gui_compass",ExchangeMenu.navigationModel("Planet: all"));
        assertEquals("gui_compass",ExchangeMenu.navigationModel("arrakis"));
        assertEquals("gui_contract",ExchangeMenu.navigationModel("Trading Guide"));
        assertNull(ExchangeMenu.navigationModel("#17 Compass"));
        assertNull(ExchangeMenu.navigationModel("#18 Confirm Purchase sword"));
        assertNull(ExchangeMenu.navigationModel("Sell 16 at 25.00 Dunemarks EACH"));
    }
    @Test void listingControlsRespectStackBoundsAndExactMoneyLimits() {
        assertEquals(1,ExchangeMenu.adjustedQuantity(1,-8,16));
        assertEquals(16,ExchangeMenu.adjustedQuantity(15,8,16));
        assertEquals(64,ExchangeMenu.adjustedQuantity(60,8,128));
        assertThrows(IllegalArgumentException.class,()->ExchangeMenu.adjustedQuantity(1,1,0));
        assertEquals(new BigDecimal("0.01"),ExchangeMenu.adjustedPrice(new BigDecimal("1.00"),-1000));
        assertEquals(new BigDecimal("1.01"),ExchangeMenu.adjustedPrice(new BigDecimal("1.00"),1));
        assertEquals(new BigDecimal("1000000.00"),ExchangeMenu.adjustedPrice(new BigDecimal("999999.99"),1000));
    }
    @Test void staleInventorySelectionCannotListDifferentOrMissingItems() {
        Player player=mock(Player.class);PlayerInventory inventory=mock(PlayerInventory.class);
        ItemStack expected=mock(ItemStack.class),current=mock(ItemStack.class);
        Material material=mock(Material.class);when(material.isAir()).thenReturn(false);
        when(player.getInventory()).thenReturn(inventory);when(expected.getType()).thenReturn(material);
        when(current.getType()).thenReturn(material);when(current.getAmount()).thenReturn(16);
        when(inventory.getItem(5)).thenReturn(current);when(current.isSimilar(expected)).thenReturn(true);
        assertSame(current,ExchangeMenu.listingStack(player,5,expected,16));
        assertThrows(IllegalArgumentException.class,()->ExchangeMenu.listingStack(player,5,expected,17));
        when(current.isSimilar(expected)).thenReturn(false);
        assertThrows(IllegalArgumentException.class,()->ExchangeMenu.listingStack(player,5,expected,1));
        when(inventory.getItem(5)).thenReturn(null);
        assertThrows(IllegalArgumentException.class,()->ExchangeMenu.listingStack(player,5,expected,1));
        assertThrows(IllegalArgumentException.class,()->ExchangeMenu.listingStack(player,36,expected,1));
        verify(inventory,never()).setItem(anyInt(),any());
    }
    @Test void ordinaryInventoriesAreNeverHijackedByTitle() {
        ExchangeMenu menu=new ExchangeMenu(mock(DuneEss.class));
        InventoryClickEvent click=mock(InventoryClickEvent.class);
        InventoryView view=mock(InventoryView.class);Inventory inventory=mock(Inventory.class);
        when(click.getView()).thenReturn(view);when(view.getTopInventory()).thenReturn(inventory);
        menu.click(click);verify(click,never()).setCancelled(anyBoolean());
    }
    @Test void bottomInventoryTransfersAndDragsAreCancelledWithoutRunningAnAction() {
        ExchangeMenu menu=new ExchangeMenu(mock(DuneEss.class));UUID owner=UUID.randomUUID();
        ExchangeMenu.Screen screen=new ExchangeMenu.Screen(owner);
        Inventory top=mock(Inventory.class),bottom=mock(Inventory.class);InventoryView view=mock(InventoryView.class);
        screen.inventory=top;when(top.getHolder()).thenReturn(screen);when(view.getTopInventory()).thenReturn(top);
        Player player=mock(Player.class);when(player.getUniqueId()).thenReturn(owner);
        InventoryClickEvent click=mock(InventoryClickEvent.class);when(click.getView()).thenReturn(view);
        when(click.getWhoClicked()).thenReturn(player);when(click.getClickedInventory()).thenReturn(bottom);
        AtomicInteger actions=new AtomicInteger();screen.actions.put(10,actions::incrementAndGet);
        menu.click(click);verify(click).setCancelled(true);assertEquals(0,actions.get());
        InventoryDragEvent drag=mock(InventoryDragEvent.class);when(drag.getView()).thenReturn(view);
        menu.drag(drag);verify(drag).setCancelled(true);
    }
    @Test void queuedSecondConfirmationCannotExecuteAfterFirstClosesScreen() {
        DuneEss plugin=mock(DuneEss.class);ExchangeMenu menu=new ExchangeMenu(plugin);
        UUID owner=UUID.randomUUID();ExchangeMenu.Screen screen=new ExchangeMenu.Screen(owner);
        Inventory top=mock(Inventory.class),closed=mock(Inventory.class);screen.inventory=top;
        InventoryView view=mock(InventoryView.class);when(view.getTopInventory()).thenReturn(top);
        when(top.getHolder()).thenReturn(screen);
        Player player=mock(Player.class);when(player.getUniqueId()).thenReturn(owner);when(player.isOnline()).thenReturn(true);
        when(player.getOpenInventory()).thenReturn(view);
        InventoryClickEvent click=mock(InventoryClickEvent.class);when(click.getView()).thenReturn(view);
        when(click.getWhoClicked()).thenReturn(player);when(click.getClickedInventory()).thenReturn(top);
        when(click.getClick()).thenReturn(ClickType.LEFT);when(click.getRawSlot()).thenReturn(30);
        AtomicInteger actions=new AtomicInteger();screen.actions.put(30,()->{actions.incrementAndGet();when(view.getTopInventory()).thenReturn(closed);});
        BukkitScheduler scheduler=mock(BukkitScheduler.class);List<Runnable> queued=new ArrayList<>();
        try(MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTask(eq(plugin),any(Runnable.class))).thenAnswer(invocation->{queued.add(invocation.getArgument(1));return null;});
            menu.click(click);menu.click(click);queued.forEach(Runnable::run);
        }
        assertEquals(1,actions.get());
    }
    @Test void administratorCommandRechecksPermission() {
        ExchangeMenu menu=new ExchangeMenu(mock(DuneEss.class));Player player=mock(Player.class);
        assertTrue(menu.onCommand(player,null,"exchange",new String[]{"admin"}));
        verify(player).sendMessage(contains("Operators only"));
    }
}
