package com.example.dunemenus;
import org.junit.jupiter.api.Test;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffConsoleTest {
 @Test void operatorFlagAloneDoesNotInventStaffPermissions(){Player p=mock(Player.class);when(p.isOp()).thenReturn(true);for(var tool:StaffConsole.TOOLS)assertFalse(tool.allowed().test(p));}
 @Test void questAdministratorDoesNotGetBankOrWorldAdministration(){Player p=mock(Player.class);when(p.hasPermission("dune.quests.admin")).thenReturn(true);assertEquals(1,StaffConsole.TOOLS.stream().filter(t->t.allowed().test(p)).count());}
 @Test void bankRequiresBothPersonalAndStaffEntryNodes(){Player p=mock(Player.class);var bank=StaffConsole.TOOLS.stream().filter(t->t.plugin().equals("DuneBank")).findFirst().orElseThrow();when(p.hasPermission("dunebank.staff.view")).thenReturn(true);assertFalse(bank.allowed().test(p));when(p.hasPermission("dunebank.use")).thenReturn(true);assertTrue(bank.allowed().test(p));}
 @Test void revokedOrDisabledToolCannotRunItsPreviouslyRenderedButton(){
  Player p=mock(Player.class);when(p.isOnline()).thenReturn(true);var tool=StaffConsole.TOOLS.stream().filter(t->t.plugin().equals("DuneQuests")).findFirst().orElseThrow();
  StaffConsole.run(tool,p);verify(p,never()).performCommand(anyString());
  when(p.hasPermission("dune.quests.admin")).thenReturn(true);PluginManager pm=mock(PluginManager.class);Plugin dependency=mock(Plugin.class);when(pm.getPlugin("DuneQuests")).thenReturn(dependency);
  try(var api=mockStatic(Bukkit.class)){api.when(Bukkit::getPluginManager).thenReturn(pm);StaffConsole.run(tool,p);verify(p,never()).performCommand(anyString());when(dependency.isEnabled()).thenReturn(true);StaffConsole.run(tool,p);verify(p).performCommand("dunequests:dunequests admin");}
 }
}

