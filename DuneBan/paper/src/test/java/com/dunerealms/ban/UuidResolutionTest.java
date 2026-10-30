package com.dunerealms.ban;
import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
import org.bukkit.*;import org.bukkit.command.Command;import org.bukkit.entity.Player;import org.bukkit.plugin.*;
import java.lang.reflect.*;import java.util.*;import java.util.concurrent.*;
class UuidResolutionTest {
 DuneBan plugin;Server server;UUID bedrock=UUID.fromString("00000000-0000-0000-0009-01f09764757f");
 @BeforeEach void setup(){plugin=mock(DuneBan.class,CALLS_REAL_METHODS);server=mock(Server.class);when(plugin.getServer()).thenReturn(server);}
 CompletableFuture<?> resolve(String value)throws Exception{Method m=DuneBan.class.getDeclaredMethod("resolve",String.class);m.setAccessible(true);return(CompletableFuture<?>)m.invoke(plugin,value);}
 @Test void floodgateUuidIsNotConvertedIntoJavaOfflineIdentity()throws Exception{assertEquals(bedrock,resolve(bedrock.toString()).join());verify(server,never()).getOfflinePlayerIfCached(anyString());}
 @Test void zeroUuidIsNotAPlayer()throws Exception{assertThrows(CompletionException.class,()->resolve("00000000-0000-0000-0000-000000000000").join());}
 @Test void unknownBedrockNameDoesNotGuessAJavaUuid()throws Exception{assertThrows(CompletionException.class,()->resolve(".UnknownBedrock").join());verify(server,never()).getOfflinePlayerIfCached(anyString());}
 @Test void verifiedNamesRetainBedrockPrefix()throws Exception{OfflinePlayer known=mock(OfflinePlayer.class);when(known.getName()).thenReturn(".unimatrix999780");when(server.getOfflinePlayer(bedrock)).thenReturn(known);Method m=DuneBan.class.getDeclaredMethod("verifiedName",UUID.class);m.setAccessible(true);assertEquals(".unimatrix999780",m.invoke(plugin,bedrock));}
 @Test void localBanNameIsNeverGuessedFromUuidText()throws Exception{OfflinePlayer unknown=mock(OfflinePlayer.class);when(server.getOfflinePlayer(bedrock)).thenReturn(unknown);Method m=DuneBan.class.getDeclaredMethod("verifiedName",UUID.class);m.setAccessible(true);assertNull(m.invoke(plugin,bedrock));}
 @Test void emptyCommandUsesExistingNamespacedStaffNavigatorOnly(){Player actor=mock(Player.class);Command command=mock(Command.class);PluginManager pm=mock(PluginManager.class);Plugin core=mock(Plugin.class);when(command.getName()).thenReturn("duneban");when(actor.getUniqueId()).thenReturn(bedrock);when(actor.isOnline()).thenReturn(true);when(actor.hasPermission("dune.ban.local")).thenReturn(true);when(server.getPlayer(bedrock)).thenReturn(actor);when(server.getPluginManager()).thenReturn(pm);when(pm.getPlugin("DuneCore")).thenReturn(core);when(core.isEnabled()).thenReturn(true);when(actor.performCommand("dunecore:dune admin")).thenReturn(true);plugin.onCommand(actor,command,"duneban",new String[0]);verify(actor).performCommand("dunecore:dune admin");}
 @Test void emptyCommandCannotBypassScopePermission(){Player actor=mock(Player.class);Command command=mock(Command.class);when(command.getName()).thenReturn("duneban");when(actor.isOp()).thenReturn(true);plugin.onCommand(actor,command,"duneban",new String[0]);verify(actor,never()).performCommand(anyString());}
}
