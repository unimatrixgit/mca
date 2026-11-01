package com.example.dunerealms;

import org.bukkit.*;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ModerationGuiTest {
    private Player player(UUID id){Player p=mock(Player.class);when(p.getUniqueId()).thenReturn(id);when(p.isOnline()).thenReturn(true);return p;}
    @Test void selfOperatorsAndProtectedTargetsAreNeverEligible(){
        Player actor=player(UUID.randomUUID()),target=player(UUID.randomUUID());assertTrue(ModerationGui.eligibleTarget(actor,target));
        assertFalse(ModerationGui.eligibleTarget(actor,actor));when(target.isOp()).thenReturn(true);assertFalse(ModerationGui.eligibleTarget(actor,target));
        when(target.isOp()).thenReturn(false);when(target.hasPermission("dune.moderation.protected")).thenReturn(true);assertFalse(ModerationGui.eligibleTarget(actor,target));
        assertFalse(ModerationGui.eligibleTarget(actor,null));
    }
    @Test void banRequiresCurrentPermissionFixedReasonDurationAndEligibleCurrentTarget(){
        Player actor=player(UUID.randomUUID());UUID target=UUID.randomUUID();
        try(MockedStatic<Bukkit> api=mockStatic(Bukkit.class)){
            assertFalse(ModerationGui.applyBan(actor,target,"Repeated rule violations",900000));api.verifyNoInteractions();
            when(actor.hasPermission("dune.moderation.ban")).thenReturn(true);
            assertFalse(ModerationGui.applyBan(actor,target,"Injected\nreason",900000));assertFalse(ModerationGui.applyBan(actor,target,"Repeated rule violations",-1));api.verifyNoInteractions();
            api.when(()->Bukkit.getPlayer(target)).thenReturn(null);assertFalse(ModerationGui.applyBan(actor,target,"Repeated rule violations",900000));api.verify(()->Bukkit.getBanList(BanList.Type.PROFILE),never());
        }
    }
    @Test void profileBanMustBeConfirmedBeforePlayerIsDisconnected(){
        Player actor=player(UUID.randomUUID()),target=player(UUID.randomUUID());when(actor.hasPermission("dune.moderation.ban")).thenReturn(true);when(actor.getName()).thenReturn("Moderator");
        ProfileBanList list=mock(ProfileBanList.class);com.destroystokyo.paper.profile.PlayerProfile profile=mock(com.destroystokyo.paper.profile.PlayerProfile.class);when(target.getPlayerProfile()).thenReturn(profile);
        try(MockedStatic<Bukkit> api=mockStatic(Bukkit.class)){
            api.when(()->Bukkit.getPlayer(target.getUniqueId())).thenReturn(target);api.when(()->Bukkit.getBanList(BanList.Type.PROFILE)).thenReturn(list);
            assertFalse(ModerationGui.applyBan(actor,target.getUniqueId(),"Repeated rule violations",900000));verify(target,never()).kickPlayer(anyString());
            when(list.isBanned((org.bukkit.profile.PlayerProfile)profile)).thenReturn(false,true);
            doReturn(mock(BanEntry.class)).when(list).addBan(eq((org.bukkit.profile.PlayerProfile)profile),eq("Repeated rule violations"),any(Date.class),eq("Moderator"));
            api.when(Bukkit::getLogger).thenReturn(Logger.getAnonymousLogger());
            assertTrue(ModerationGui.applyBan(actor,target.getUniqueId(),"Repeated rule violations",900000));verify(target).kickPlayer(contains("Appeal at"));
        }
    }
    private static final class Harness {
        final Player owner;final ModerationGui gui;final ModerationGui.Screen screen;final InventoryView view;final InventoryClickEvent click;final List<Runnable> queued=new ArrayList<>();final AtomicInteger calls=new AtomicInteger();final BukkitScheduler scheduler=mock(BukkitScheduler.class);final AtomicBoolean allowed=new AtomicBoolean(true);
        Harness()throws Exception{
            owner=mock(Player.class);UUID id=UUID.randomUUID();when(owner.getUniqueId()).thenReturn(id);when(owner.isOnline()).thenReturn(true);
            gui=mock(ModerationGui.class,CALLS_REAL_METHODS);Field f=ModerationGui.class.getDeclaredField("plugin");f.setAccessible(true);f.set(gui,mock(DuneCore.class));
            screen=new ModerationGui.Screen(id,allowed::get);screen.inventory=mock(Inventory.class);when(screen.inventory.getHolder()).thenReturn(screen);screen.actions.put(30,calls::incrementAndGet);
            view=mock(InventoryView.class);when(view.getTopInventory()).thenReturn(screen.inventory);when(owner.getOpenInventory()).thenReturn(view);
            click=mock(InventoryClickEvent.class);when(click.getView()).thenReturn(view);when(click.getWhoClicked()).thenReturn(owner);when(click.getClickedInventory()).thenReturn(screen.inventory);when(click.getRawSlot()).thenReturn(30);when(click.getClick()).thenReturn(ClickType.LEFT);
            doAnswer(call->{queued.add(call.getArgument(1));return null;}).when(scheduler).runTask(any(org.bukkit.plugin.Plugin.class),any(Runnable.class));
        }
    }
    @Test void exactOwnerTopClickDispatchesOnceAfterQueueAndCancelsTransfers()throws Exception{
        Harness h=new Harness();try(MockedStatic<Bukkit> api=mockStatic(Bukkit.class)){api.when(Bukkit::getScheduler).thenReturn(h.scheduler);h.gui.click(h.click);h.gui.click(h.click);assertEquals(1,h.queued.size());verify(h.click,times(2)).setCancelled(true);h.queued.get(0).run();assertEquals(1,h.calls.get());}
    }
    @Test void bottomShiftDoubleNumberOtherOwnerAndDragCannotTakeIconsOrTriggerActions()throws Exception{
        for(ClickType type:List.of(ClickType.SHIFT_LEFT,ClickType.NUMBER_KEY,ClickType.DOUBLE_CLICK,ClickType.RIGHT)){Harness h=new Harness();when(h.click.getClick()).thenReturn(type);h.gui.click(h.click);verify(h.click).setCancelled(true);assertEquals(0,h.queued.size());}
        Harness h=new Harness();when(h.click.getClickedInventory()).thenReturn(mock(Inventory.class));h.gui.click(h.click);assertFalse(h.screen.claimed);
        when(h.click.getClickedInventory()).thenReturn(h.screen.inventory);Player other=mock(Player.class);when(other.getUniqueId()).thenReturn(UUID.randomUUID());when(h.click.getWhoClicked()).thenReturn(other);h.gui.click(h.click);assertFalse(h.screen.claimed);
        InventoryDragEvent drag=mock(InventoryDragEvent.class);when(drag.getView()).thenReturn(h.view);h.gui.drag(drag);verify(drag).setCancelled(true);
    }
    @Test void queuedConfirmationRechecksPermissionConnectionAndExactCurrentInventory()throws Exception{
        for(int change=0;change<3;change++){Harness h=new Harness();try(MockedStatic<Bukkit> api=mockStatic(Bukkit.class)){api.when(Bukkit::getScheduler).thenReturn(h.scheduler);h.gui.click(h.click);if(change==0)h.allowed.set(false);if(change==1)when(h.owner.isOnline()).thenReturn(false);if(change==2)when(h.view.getTopInventory()).thenReturn(mock(Inventory.class));h.queued.get(0).run();assertEquals(0,h.calls.get());}}
    }
    @Test void changedBanSnapshotIsNeverPardonedAndUnbanRequiresCurrentPermission(){
        Player actor=player(UUID.randomUUID());ModerationGui.BanSnapshot snapshot=new ModerationGui.BanSnapshot(UUID.randomUUID(),"Target",1,"Old reason",null,"Moderator");
        ProfileBanList list=mock(ProfileBanList.class);when(list.getBanEntries()).thenReturn(Set.of());
        try(MockedStatic<Bukkit> api=mockStatic(Bukkit.class)){assertFalse(ModerationGui.removeBan(actor,snapshot));api.verifyNoInteractions();when(actor.hasPermission("dune.moderation.unban")).thenReturn(true);api.when(()->Bukkit.getBanList(BanList.Type.PROFILE)).thenReturn(list);assertFalse(ModerationGui.removeBan(actor,snapshot));verify(list,never()).pardon(any(org.bukkit.profile.PlayerProfile.class));}
    }
    @Test void paginationRemainsWithinAvailableRows(){assertEquals(0,ModerationGui.page(99,0));assertEquals(0,ModerationGui.page(-1,500));assertEquals(1,ModerationGui.page(99,29));}
    @Test void successfulUnbanUsesTheExactReviewedProfileAndConfirmsRemoval(){
        Player actor=player(UUID.randomUUID());when(actor.hasPermission("dune.moderation.unban")).thenReturn(true);when(actor.getName()).thenReturn("Moderator");
        UUID id=UUID.randomUUID();ModerationGui.BanSnapshot reviewed=new ModerationGui.BanSnapshot(id,"Target",1000,"Reviewed reason",null,"OtherStaff");
        ProfileBanList list=mock(ProfileBanList.class);BanEntry entry=mock(BanEntry.class);com.destroystokyo.paper.profile.PlayerProfile profile=mock(com.destroystokyo.paper.profile.PlayerProfile.class);
        when(profile.getUniqueId()).thenReturn(id);when(profile.getName()).thenReturn("Target");when(entry.getBanTarget()).thenReturn(profile);when(entry.getCreated()).thenReturn(new Date(1000));when(entry.getReason()).thenReturn("Reviewed reason");when(entry.getSource()).thenReturn("OtherStaff");when(list.getBanEntries()).thenReturn(Set.of(entry));
        try(MockedStatic<Bukkit> api=mockStatic(Bukkit.class)){api.when(()->Bukkit.getBanList(BanList.Type.PROFILE)).thenReturn(list);api.when(()->Bukkit.createPlayerProfile(id,"Target")).thenReturn(profile);api.when(Bukkit::getLogger).thenReturn(Logger.getAnonymousLogger());assertTrue(ModerationGui.removeBan(actor,reviewed));verify(list).pardon((org.bukkit.profile.PlayerProfile)profile);verify(list).isBanned((org.bukkit.profile.PlayerProfile)profile);}
    }
    @Test void aTargetProtectedAfterSelectionCannotBeBanned(){
        Player actor=player(UUID.randomUUID()),target=player(UUID.randomUUID());when(actor.hasPermission("dune.moderation.ban")).thenReturn(true);assertTrue(ModerationGui.eligibleTarget(actor,target));when(target.hasPermission("dune.moderation.protected")).thenReturn(true);
        try(MockedStatic<Bukkit> api=mockStatic(Bukkit.class)){api.when(()->Bukkit.getPlayer(target.getUniqueId())).thenReturn(target);assertFalse(ModerationGui.applyBan(actor,target.getUniqueId(),"Repeated rule violations",86400000));api.verify(()->Bukkit.getBanList(BanList.Type.PROFILE),never());verify(target,never()).kickPlayer(anyString());}
    }
    @Test void delegatedModeratorsCannotRunOperatorUtilityButtons()throws Exception{
        AdminGui gui=mock(AdminGui.class,CALLS_REAL_METHODS);Player staff=player(UUID.randomUUID());when(staff.hasPermission("dune.moderation.ban")).thenReturn(true);
        Class<?> menuClass=Class.forName("com.example.dunerealms.AdminGui$Menu");var constructor=menuClass.getDeclaredConstructor();constructor.setAccessible(true);InventoryHolder menu=(InventoryHolder)constructor.newInstance();var ownerField=menuClass.getDeclaredField("owner");ownerField.setAccessible(true);ownerField.set(menu,staff.getUniqueId());
        Inventory top=mock(Inventory.class);when(top.getHolder()).thenReturn(menu);InventoryView view=mock(InventoryView.class);when(view.getTopInventory()).thenReturn(top);InventoryClickEvent event=mock(InventoryClickEvent.class);when(event.getView()).thenReturn(view);when(event.getWhoClicked()).thenReturn(staff);when(event.getClickedInventory()).thenReturn(top);when(event.getClick()).thenReturn(ClickType.LEFT);when(event.getRawSlot()).thenReturn(10);
        try(MockedStatic<Bukkit> api=mockStatic(Bukkit.class)){gui.click(event);verify(event).setCancelled(true);api.verifyNoInteractions();}
    }
}
