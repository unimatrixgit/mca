package com.example.dunerealms;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.lang.reflect.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class PlayerTicketMenuTest {
    @Test void playerMenuUsesOnlyFixedTicketEntryForActualPlayer()throws Exception{
        ConfigurableGui gui=mock(ConfigurableGui.class,CALLS_REAL_METHODS);DuneCore plugin=mock(DuneCore.class);Player player=mock(Player.class);
        Field field=ConfigurableGui.class.getDeclaredField("plugin");field.setAccessible(true);field.set(gui,plugin);Method execute=ConfigurableGui.class.getDeclaredMethod("execute",Player.class,String.class);execute.setAccessible(true);
        try(MockedStatic<ModerationGui> tickets=mockStatic(ModerationGui.class)){
            execute.invoke(gui,player,"tickets");tickets.verify(()->ModerationGui.ticketsFor(plugin,player));tickets.verifyNoMoreInteractions();verify(player,never()).performCommand(anyString());
        }
    }
    @Test void ticketEntryDoesNotAcceptArbitraryUuidOrCommandText()throws Exception{
        ConfigurableGui gui=mock(ConfigurableGui.class,CALLS_REAL_METHODS);Player player=mock(Player.class);Method execute=ConfigurableGui.class.getDeclaredMethod("execute",Player.class,String.class);execute.setAccessible(true);
        try(MockedStatic<ModerationGui> tickets=mockStatic(ModerationGui.class)){
            execute.invoke(gui,player,"tickets:other-player");execute.invoke(gui,player,"tickets op attacker");tickets.verifyNoInteractions();verify(player,never()).performCommand(anyString());
        }
    }
    @Test void staffDeskUsesOnlyFixedAdminCommandAndRechecksPermissionAfterRankChange()throws Exception{
        ConfigurableGui gui=mock(ConfigurableGui.class,CALLS_REAL_METHODS);Player player=mock(Player.class);Method execute=ConfigurableGui.class.getDeclaredMethod("execute",Player.class,String.class);execute.setAccessible(true);
        assertFalse(AdminGui.canUse(player));execute.invoke(gui,player,"staffdesk");verify(player,never()).performCommand(anyString());
        when(player.hasPermission("dune.moderation.ban")).thenReturn(true);assertTrue(AdminGui.canUse(player));execute.invoke(gui,player,"staffdesk");verify(player).performCommand("dune admin");
        when(player.hasPermission("dune.moderation.ban")).thenReturn(false);execute.invoke(gui,player,"staffdesk");execute.invoke(gui,player,"staffdesk op attacker");verify(player,times(1)).performCommand(anyString());
        assertEquals("gui_contract",ConfigurableGui.guiModel("&6Support Tickets","WRITABLE_BOOK",List.of()));assertEquals("gui_compass",ConfigurableGui.guiModel("&6Staff Desk","SHIELD",List.of()));
    }
}
