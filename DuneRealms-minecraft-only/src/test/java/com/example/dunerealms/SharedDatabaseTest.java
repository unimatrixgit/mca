package com.example.dunerealms;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SharedDatabaseTest {
    @TempDir Path temp;
    private Driver driver;
    private Connection owner;
    private PreparedStatement lock, row, update;
    private ResultSet lockResult, profile;
    private SharedDatabase db;
    private Player player;
    private PlayerInventory inventory;
    private Inventory ender;
    private final UUID id = UUID.randomUUID();
    private final Map<UUID, String> factions = new HashMap<>();

    @BeforeEach void prepare() throws Exception {
        Connection schema = mock(Connection.class);
        when(schema.createStatement()).thenReturn(mock(Statement.class));
        owner = mock(Connection.class);
        lock = mock(PreparedStatement.class);
        row = mock(PreparedStatement.class);
        update = mock(PreparedStatement.class);
        lockResult = mock(ResultSet.class);
        profile = mock(ResultSet.class);
        when(owner.prepareStatement(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("pg_try_advisory_lock")) return lock;
            if (sql.startsWith("SELECT faction")) return row;
            return update;
        });
        when(lock.executeQuery()).thenReturn(lockResult);
        when(lockResult.next()).thenReturn(true);
        when(lockResult.getBoolean(1)).thenReturn(true);
        when(row.executeQuery()).thenReturn(profile);
        when(profile.next()).thenReturn(true);
        when(profile.getString("faction")).thenReturn("fremen");
        when(update.executeUpdate()).thenReturn(1);
        driver = mock(Driver.class);
        when(driver.acceptsURL("jdbc:dune-test:profiles")).thenReturn(true);
        when(driver.connect(eq("jdbc:dune-test:profiles"), any())).thenReturn(schema, owner);
        DriverManager.registerDriver(driver);
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("database.jdbc-url", "jdbc:dune-test:profiles");
        config.set("database.username", "dune");
        config.set("database.password", "test-only");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        db = new SharedDatabase(plugin);
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        ender = mock(Inventory.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getEnderChest()).thenReturn(ender);
        when(inventory.getContents()).thenReturn(new ItemStack[41]);
        when(ender.getContents()).thenReturn(new ItemStack[27]);
        when(ender.getSize()).thenReturn(27);
    }

    @AfterEach void cleanup() throws Exception {
        db.close();
        DriverManager.deregisterDriver(driver);
    }

    @Test void failedLockNeverLoadsOrSavesLocalItems() throws Exception {
        when(lockResult.getBoolean(1)).thenReturn(false);
        assertThrows(SQLException.class, () -> db.loadPlayer(player, factions));
        db.releasePlayer(player, factions);
        assertFalse(db.isLoaded(id));
        verify(owner).close();
        verify(row, never()).executeQuery();
        verify(update, never()).executeUpdate();
        verifyNoInteractions(inventory, ender);
    }

    @Test void dirtySessionRequiresRecoveryAndDoesNotOverwriteDatabase() throws Exception {
        when(profile.getBoolean("session_open")).thenReturn(true);
        assertThrows(SQLException.class, () -> db.loadPlayer(player, factions));
        clearInvocations(update);
        db.savePlayer(player, factions);
        db.releasePlayer(player, factions);
        verifyNoInteractions(update);
        verify(inventory, never()).setContents(any());
    }

    @Test void malformedInventoryFailsBeforeOpeningSessionOrApplyingItems() throws Exception {
        when(profile.getString("inventory")).thenReturn("not base64!");
        assertThrows(Exception.class, () -> db.loadPlayer(player, factions));
        verify(owner, never()).prepareStatement("UPDATE dune_players SET session_open=TRUE WHERE uuid=?");
        verify(inventory, never()).setContents(any());
        assertFalse(db.isLoaded(id));
    }

    @Test void autosaveUsesOwnedConnectionAndKeepsSessionOpenThenQuitReleases() throws Exception {
        db.loadPlayer(player, factions);
        assertEquals("fremen", factions.get(id));
        assertTrue(db.isLoaded(id));
        db.savePlayer(player, factions);
        verify(update).setBoolean(7, true);
        verify(owner, never()).close();
        db.releasePlayer(player, factions);
        verify(update).setBoolean(7, false);
        verify(owner).close();
        assertFalse(db.isLoaded(id));
        verify(driver, times(2)).connect(anyString(), any()); // Schema + one owned connection only.
    }

    @Test void duplicateLoadOnSameBackendCannotOverwriteSession() throws Exception {
        db.loadPlayer(player, factions);
        assertThrows(SQLException.class, () -> db.loadPlayer(player, factions));
        verify(driver, times(2)).connect(anyString(), any());
        verify(owner, never()).close();
    }

    @Test void savedInventoryAndExperienceReloadTogether() throws Exception {
        db.loadPlayer(player, factions);
        clearInvocations(update);
        when(player.getLevel()).thenReturn(23);
        when(player.getExp()).thenReturn(0.5f);
        when(player.getTotalExperience()).thenReturn(900);
        when(inventory.getHeldItemSlot()).thenReturn(7);
        db.savePlayer(player, factions);
        var inv = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(update).setString(eq(2), inv.capture());
        db.releasePlayer(player, factions);
        when(profile.getString("inventory")).thenReturn(inv.getValue());
        when(profile.getObject("experience_level")).thenReturn(23);
        when(profile.getObject("experience_progress")).thenReturn(0.5f);
        when(profile.getObject("total_experience")).thenReturn(900);
        when(profile.getObject("held_slot")).thenReturn(7);
        db.loadPlayer(player, factions);
        verify(inventory).setContents(new ItemStack[41]);
        verifyNoInteractions(ender);
        verify(player).setLevel(23);
        verify(player).setExp(0.5f);
        verify(player).setTotalExperience(900);
        verify(inventory).setHeldItemSlot(7);
    }

    @Test void failedQuitSaveWritesRecoverySnapshotAndClosesOwnership() throws Exception {
        db.loadPlayer(player, factions);
        when(update.executeUpdate()).thenThrow(new SQLException("simulated outage"));
        assertThrows(SQLException.class, () -> db.releasePlayer(player, factions));
        assertFalse(db.isLoaded(id));
        verify(owner).close();
        try (var files = java.nio.file.Files.list(temp.resolve("inventory-recovery"))) {
            Path recovery = files.findFirst().orElseThrow();
            YamlConfiguration saved = YamlConfiguration.loadConfiguration(recovery.toFile());
            assertEquals(id.toString(), saved.getString("uuid"));
            assertNotNull(saved.getString("inventory"));
            assertNull(saved.getString("ender-chest"));
        }
    }
    @Test void tradeAndInventoryCheckpointCommitOnTheOwnedConnection() throws Exception {
        db.loadPlayer(player,factions);clearInvocations(update);
        db.transaction(player,factions,c->assertSame(owner,c));
        verify(update).executeUpdate();verify(owner).commit();verify(owner).setAutoCommit(true);
        assertTrue(db.isLoaded(id));verify(owner,never()).close();
    }
    @Test void failedTradeRollsBackAndRestoresInventoryWithoutReleasingOwnership() throws Exception {
        db.loadPlayer(player,factions);
        assertThrows(IllegalArgumentException.class,()->db.transaction(player,factions,c->{throw new IllegalArgumentException("sold out");}));
        verify(owner).rollback();verify(owner,never()).commit();
        verify(inventory).setContents(new ItemStack[41]);assertTrue(db.isLoaded(id));
    }
    @Test void uncertainCommitQuarantinesPlayerAndCannotPerformAQuitSave() throws Exception {
        db.loadPlayer(player,factions);doThrow(new SQLException("connection lost at commit")).when(owner).commit();
        assertThrows(SQLException.class,()->db.transaction(player,factions,c->{}));
        assertFalse(db.isLoaded(id));verify(owner).close();verify(player).kickPlayer(anyString());
        clearInvocations(update);db.releasePlayer(player,factions);verifyNoInteractions(update);
    }
}
