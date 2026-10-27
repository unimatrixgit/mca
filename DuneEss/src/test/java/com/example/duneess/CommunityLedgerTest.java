package com.example.duneess;

import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CommunityLedgerTest {
 @TempDir Path directory;
 DuneEss plugin(){DuneEss plugin=mock(DuneEss.class);when(plugin.getDataFolder()).thenReturn(directory.resolve("DuneEss").toFile());when(plugin.getConfig()).thenReturn(new YamlConfiguration());when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());return plugin;}
 @Test void creditsOnceAcrossRestartAndRetainsReceiptUntilAcknowledged() throws Exception {
  DuneEss plugin=plugin();UUID uuid=UUID.randomUUID();OfflinePlayer player=mock(OfflinePlayer.class);when(player.getUniqueId()).thenReturn(uuid);String receipt="a".repeat(64);
  DuneEconomy first=new DuneEconomy(plugin);assertTrue(first.grantCommunity(uuid,"2026-10",50,receipt));assertEquals(50,first.getBalance(player));
  first.add(uuid,10);assertEquals(60,first.getBalance(player));
  DuneEconomy restarted=new DuneEconomy(plugin);assertFalse(restarted.grantCommunity(uuid,"2026-10",50,receipt));assertEquals(60,restarted.getBalance(player));assertEquals(List.of(receipt),restarted.pendingCommunityReceipts().get(uuid));
  assertThrows(Exception.class,()->restarted.grantCommunity(uuid,"2026-10",50,"b".repeat(64)));
  restarted.acknowledgeCommunity(uuid,receipt);assertTrue(new DuneEconomy(plugin).pendingCommunityReceipts().isEmpty());
  assertTrue(restarted.grantCommunity(uuid,"2026-11",50,"c".repeat(64)));assertEquals(110,restarted.getBalance(player));
 }
 @Test void failedDurableWriteDoesNotCreditOrConsumeTheReward() throws Exception {
  DuneEss plugin=plugin();DuneEconomy ledger=new DuneEconomy(plugin);UUID uuid=UUID.randomUUID();OfflinePlayer player=mock(OfflinePlayer.class);when(player.getUniqueId()).thenReturn(uuid);
  Path blocker=directory.resolve("DuneEss/balances.yml.community-tmp");Files.createDirectory(blocker);
  assertThrows(Exception.class,()->ledger.grantCommunity(uuid,"2026-10",50,"a".repeat(64)));assertEquals(0,ledger.getBalance(player));assertTrue(ledger.pendingCommunityReceipts().isEmpty());
  Files.delete(blocker);assertTrue(ledger.grantCommunity(uuid,"2026-10",50,"a".repeat(64)));assertEquals(50,ledger.getBalance(player));
 }
 @Test void incompleteSharedConfigurationCannotCreateASecondLocalWallet() throws Exception {
  Files.createDirectories(directory.resolve("DuneCore"));YamlConfiguration core=new YamlConfiguration();core.set("database.enabled",true);core.set("database.jdbc-url","");core.save(directory.resolve("DuneCore/config.yml").toFile());
  DuneEconomy ledger=new DuneEconomy(plugin());UUID uuid=UUID.randomUUID();OfflinePlayer player=mock(OfflinePlayer.class);when(player.getUniqueId()).thenReturn(uuid);
  assertFalse(ledger.depositPlayer(player,50).transactionSuccess());assertEquals(0,ledger.getBalance(player));
  assertThrows(Exception.class,()->ledger.grantCommunity(uuid,"2026-10",50,"a".repeat(64)));
 }
}
