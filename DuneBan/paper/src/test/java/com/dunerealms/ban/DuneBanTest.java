package com.dunerealms.ban;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.bukkit.*;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import java.lang.reflect.Field;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
class DuneBanTest {
 DuneBan plugin;Server server;Player actor,target;OfflinePlayer offline;ProfileBanList local;org.bukkit.profile.PlayerProfile profile;BanStore store;ExecutorService worker;
 UUID actorId=UUID.fromString("00000000-0000-4000-8000-000000000001"),targetId=UUID.fromString("00000000-0000-4000-8000-000000000002");
 void field(String name,Object value)throws Exception{Field f=DuneBan.class.getDeclaredField(name);f.setAccessible(true);f.set(plugin,value);}
 @BeforeEach void setup()throws Exception {
  plugin=mock(DuneBan.class,CALLS_REAL_METHODS);server=mock(Server.class);actor=mock(Player.class);target=mock(Player.class);offline=mock(OfflinePlayer.class);local=mock(ProfileBanList.class);profile=mock(com.destroystokyo.paper.profile.PlayerProfile.class);store=mock(BanStore.class);worker=Executors.newSingleThreadExecutor();
  when(plugin.getServer()).thenReturn(server);when(plugin.isEnabled()).thenReturn(true);field("worker",worker);field("busy",ConcurrentHashMap.newKeySet());field("source","arrakis");field("store",store);field("ready",true);field("serverThread",Thread.currentThread());
  when(actor.getUniqueId()).thenReturn(actorId);when(actor.getName()).thenReturn("Moderator");when(actor.isOnline()).thenReturn(true);when(actor.hasPermission(anyString())).thenReturn(true);
  when(target.getUniqueId()).thenReturn(targetId);when(target.getName()).thenReturn("Target");when(target.isOnline()).thenReturn(true);
  when(server.getPlayer(actorId)).thenReturn(actor);when(server.getPlayer(targetId)).thenReturn(target);when(server.getOfflinePlayer(targetId)).thenReturn(offline);when(offline.getName()).thenReturn("Target");when(server.createPlayerProfile(targetId)).thenReturn(profile);when(target.getPlayerProfile()).thenReturn((com.destroystokyo.paper.profile.PlayerProfile)profile);when(server.getBanList(BanList.Type.PROFILE)).thenAnswer(i->local);
  BukkitScheduler scheduler=mock(BukkitScheduler.class);when(server.getScheduler()).thenReturn(scheduler);doAnswer(i->{((Runnable)i.getArgument(1)).run();return null;}).when(scheduler).runTask(eq(plugin),any(Runnable.class));
 }
 @AfterEach void close(){worker.shutdownNow();}
 @Test void localBanRequiresVerifiedNativeBanBeforeKickAndNeverTouchesGlobalStore(){when(local.isBanned(profile)).thenReturn(true);var result=plugin.banScoped(actor,targetId,"Recorded reason",Duration.ofHours(1),"local").join();assertTrue(result.ok(),result.message());verify(local).addBan(eq(profile),eq("Recorded reason"),any(Instant.class),eq("Moderator [DuneBan:arrakis]"));verify(target).kick(any(net.kyori.adventure.text.Component.class));verifyNoInteractions(store);}
 @Test void failedLocalConfirmationDoesNotKick(){when(local.isBanned(profile)).thenReturn(false);assertFalse(plugin.banScoped(actor,targetId,"Recorded reason",null,"local").join().ok());verify(target,never()).kick(any(net.kyori.adventure.text.Component.class));}
 @Test void selfOperatorProtectedAndRevokedPermissionCannotWrite()throws Exception {
  assertFalse(plugin.banScoped(actor,actorId,"Recorded reason",null,"global").join().ok());
  when(offline.isOp()).thenReturn(true);assertFalse(plugin.banScoped(actor,targetId,"Recorded reason",null,"global").join().ok());when(offline.isOp()).thenReturn(false);
  when(target.hasPermission("dune.ban.protected")).thenReturn(true);assertFalse(plugin.banScoped(actor,targetId,"Recorded reason",null,"global").join().ok());when(target.hasPermission("dune.ban.protected")).thenReturn(false);
  when(actor.hasPermission("dune.ban.global")).thenReturn(false);assertFalse(plugin.banScoped(actor,targetId,"Recorded reason",null,"global").join().ok());verifyNoInteractions(store);verify(local,never()).addBan(any(com.destroystokyo.paper.profile.PlayerProfile.class),anyString(),any(Instant.class),anyString());
 }
 @Test void globalBanPersistsOnlyExactUuidAndDoesNotCreateALocalBan()throws Exception {var record=new BanRecord(UUID.randomUUID(),targetId,"Target",actorId,"Moderator","Recorded reason",1793232000000L,null,"arrakis");when(store.add(eq(targetId),eq("Target"),eq(actorId),eq("Moderator"),eq("Recorded reason"),isNull(),eq("arrakis"))).thenReturn(record);assertTrue(plugin.banScoped(actor,targetId,"Recorded reason",null,"global").get(3,TimeUnit.SECONDS).ok());verifyNoInteractions(local);verify(target,never()).kick(any(net.kyori.adventure.text.Component.class));}
 @Test void staleLocalUnbanNeverPardons(){org.bukkit.BanEntry<com.destroystokyo.paper.profile.PlayerProfile> entry=mock(org.bukkit.BanEntry.class);when(entry.getCreated()).thenReturn(Date.from(Instant.parse("2026-10-29T00:00:00Z")));when(local.getBanEntry(profile)).thenAnswer(i->entry);assertFalse(plugin.unbanScoped(actor,targetId,"2026-10-28T00:00:00Z","Reviewed reason","local").join().ok());verify(local,never()).pardon(profile);}
 @Test void globalUnbanCarriesExactCreatedSnapshotAndReason()throws Exception {when(store.remove(eq(targetId),eq(1793232000000L),anyString(),eq("Reviewed reason"))).thenReturn(true);assertTrue(plugin.unbanScoped(actor,targetId,"2026-10-29T00:00:00Z","Reviewed reason","global").get(3,TimeUnit.SECONDS).ok());verify(store).remove(targetId,1793232000000L,actorId+":Moderator","Reviewed reason");verifyNoInteractions(local);}
 @Test void invalidScopeAndReasonAndUnavailableStorageFailWithoutBan()throws Exception {assertFalse(plugin.banScoped(actor,targetId,"Recorded reason",null,"network;stop").join().ok());assertFalse(plugin.banScoped(actor,targetId,"reason\nstop",null,"global").join().ok());field("ready",false);assertFalse(plugin.banScoped(actor,targetId,"Recorded reason",null,"global").join().ok());verifyNoInteractions(store);}
}
