package com.example.duneprotect;
import org.junit.jupiter.api.Test;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;
import static org.mockito.Mockito.*;

class DuneProtectTest {
 @Test void deniedWrapperPermissionNeverDispatches(){
  var wrapper=mock(DuneProtect.class,CALLS_REAL_METHODS);var sender=mock(CommandSender.class);
  try(var bukkit=mockStatic(Bukkit.class)){wrapper.onCommand(sender,null,"duneprotect",new String[]{"rollback","r:10"});bukkit.verifyNoInteractions();}
 }
 @Test void forwardsAsOriginalSenderWithoutConsoleElevation(){
  var wrapper=mock(DuneProtect.class,CALLS_REAL_METHODS);var sender=mock(CommandSender.class);var manager=mock(PluginManager.class);
  when(sender.hasPermission("duneprotect.lookup")).thenReturn(true);when(manager.isPluginEnabled("CoreProtect")).thenReturn(true);
  try(var bukkit=mockStatic(Bukkit.class)){bukkit.when(Bukkit::getPluginManager).thenReturn(manager);wrapper.onCommand(sender,null,"duneprotect",new String[]{"lookup","u:example","t:1h"});bukkit.verify(()->Bukkit.dispatchCommand(sender,"coreprotect:coreprotect lookup u:example t:1h"));bukkit.verify(Bukkit::getPluginManager);bukkit.verifyNoMoreInteractions();}
 }
 @Test void unavailableBackendNeverDispatches(){
  var wrapper=mock(DuneProtect.class,CALLS_REAL_METHODS);var sender=mock(CommandSender.class);var manager=mock(PluginManager.class);
  when(sender.hasPermission("duneprotect.worldedit")).thenReturn(true);
  try(var bukkit=mockStatic(Bukkit.class)){bukkit.when(Bukkit::getPluginManager).thenReturn(manager);wrapper.onCommand(sender,null,"duneprotect",new String[]{"worldedit","version"});bukkit.verify(Bukkit::getPluginManager);bukkit.verifyNoMoreInteractions();}
 }
}
