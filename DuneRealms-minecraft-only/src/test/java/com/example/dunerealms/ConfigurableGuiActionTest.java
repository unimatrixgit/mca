package com.example.dunerealms;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.mockito.Mockito.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurableGuiActionTest {
    @Test void themedModelsSelectOnlyTheirGuiRoles(){
        assertEquals("gui_market",ConfigurableGui.guiModel("&6Item Exchange","CHEST",List.of()));
        assertEquals("gui_confirm",ConfigurableGui.guiModel("§aConfirm","LIME_DYE",List.of()));
        assertEquals("gui_contract",ConfigurableGui.guiModel("A sponsor","PAPER",List.of("&7Spice remaining: 100")));
        assertEquals("gui_contract",ConfigurableGui.guiModel("&6Koopkring Contracts","PAPER",List.of()));
        assertEquals("gui_compass",ConfigurableGui.guiModel("Player menu","ARROW",List.of()));
        assertNull(ConfigurableGui.guiModel("Wallet: 50 Dunemarks","DUNEMARK",List.of()));
        assertNull(ConfigurableGui.guiModel("Arrakduin","PAPER",List.of()));
        assertNull(ConfigurableGui.guiModel("By appointment of the Emperor","PAPER",List.of()));
    }
    private void execute(ConfigurableGui gui,Player player,String action)throws Exception{Method method=ConfigurableGui.class.getDeclaredMethod("execute",Player.class,String.class);method.setAccessible(true);method.invoke(gui,player,action);}
    @Test void itemExchangeUsesOnlyTheFixedPlayerCommand()throws Exception{
        ConfigurableGui gui=mock(ConfigurableGui.class,CALLS_REAL_METHODS);Player player=mock(Player.class);when(player.performCommand("exchange")).thenReturn(true);
        execute(gui,player,"itemexchange");verify(player).closeInventory();verify(player).performCommand("exchange");verify(player,never()).isOp();
    }
    @Test void arbitraryCommandTextCannotUseTheNewAction()throws Exception{
        ConfigurableGui gui=mock(ConfigurableGui.class,CALLS_REAL_METHODS);Player player=mock(Player.class);execute(gui,player,"itemexchange op attacker");verify(player,never()).performCommand(anyString());
    }
}
