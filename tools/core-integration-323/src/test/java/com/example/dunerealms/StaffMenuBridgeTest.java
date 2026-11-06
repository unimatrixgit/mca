package com.example.dunerealms;
import org.bukkit.Bukkit;import org.bukkit.entity.Player;import org.bukkit.plugin.Plugin;import org.bukkit.plugin.PluginManager;import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;

class StaffMenuBridgeTest {
 public static class Renderer extends JavaPlugin {public boolean hasStaffTools(Player player){return false;}}
 Player player;PluginManager manager;MockedStatic<Bukkit> bukkit;
 @BeforeEach void setup(){player=mock(Player.class);manager=mock(PluginManager.class);bukkit=mockStatic(Bukkit.class);bukkit.when(Bukkit::getPluginManager).thenReturn(manager);}
 @AfterEach void close(){bukkit.close();}
 @Test void missingOrDisabledRendererCannotShowAnOperationalStaffRoute(){assertFalse(StaffMenuBridge.available(player));Plugin disabled=mock(Plugin.class);when(manager.getPlugin("DuneMenus")).thenReturn(disabled);assertFalse(StaffMenuBridge.available(player));verifyNoInteractions(player);}
 @Test void currentRendererAuthorityIsConsultedOnEveryCall(){Renderer renderer=mock(Renderer.class);when(renderer.isEnabled()).thenReturn(true);when(manager.getPlugin("DuneMenus")).thenReturn(renderer);when(renderer.hasStaffTools(player)).thenReturn(true,false);assertTrue(StaffMenuBridge.available(player));assertFalse(StaffMenuBridge.available(player));verify(renderer,times(2)).hasStaffTools(player);verify(player,never()).performCommand(anyString());}
 @Test void oldRendererWithoutStaffCapabilityFailsClosed(){Plugin old=mock(Plugin.class);when(old.isEnabled()).thenReturn(true);when(manager.getPlugin("DuneMenus")).thenReturn(old);assertFalse(StaffMenuBridge.available(player));verifyNoInteractions(player);}
 @Test void failedRendererCapabilityNeverCreatesAnOpBypass(){Renderer renderer=mock(Renderer.class);when(renderer.isEnabled()).thenReturn(true);when(manager.getPlugin("DuneMenus")).thenReturn(renderer);when(renderer.hasStaffTools(player)).thenThrow(new IllegalStateException("Fixture renderer unavailable"));assertFalse(StaffMenuBridge.available(player));verifyNoInteractions(player);}
 @Test void installedRendererCapabilityObservesActualToolPermissionsAndPluginAvailability()throws Exception{
  String jar=System.getProperty("qaMenusJar");Assumptions.assumeTrue(jar!=null);
  Class<? extends Plugin> type=Class.forName("com.example.dunemenus.DuneMenus").asSubclass(Plugin.class);
  assertEquals(java.nio.file.Path.of(jar).toRealPath(),java.nio.file.Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath());
  Plugin renderer=mock(type,CALLS_REAL_METHODS);when(renderer.isEnabled()).thenReturn(true);when(manager.getPlugin("DuneMenus")).thenReturn(renderer);when(player.isOnline()).thenReturn(true);when(player.isOp()).thenReturn(true);assertFalse(StaffMenuBridge.available(player));
  Plugin quests=mock(Plugin.class);when(quests.isEnabled()).thenReturn(true);when(manager.getPlugin("DuneQuests")).thenReturn(quests);when(player.hasPermission("dune.quests.admin")).thenReturn(true);assertTrue(StaffMenuBridge.available(player));when(player.hasPermission("dune.quests.admin")).thenReturn(false);assertFalse(StaffMenuBridge.available(player));
  when(player.hasPermission("dune.quests.admin")).thenReturn(true);when(quests.isEnabled()).thenReturn(false);assertFalse(StaffMenuBridge.available(player));verify(player,never()).performCommand(anyString());
 }
}
