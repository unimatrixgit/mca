
package com.example.dunerealms;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.Base64;

/** SQL-backed network player state and shared politics state (PostgreSQL or MySQL 8). */
public final class SharedDatabase implements AutoCloseable {
    private final JavaPlugin plugin;
    private final String url, user, password;
    private final boolean mysql;
    // Main-thread only. A dedicated connection owns the advisory lock until quit.
    private final Map<UUID, Connection> sessions = new HashMap<>();

    public SharedDatabase(JavaPlugin plugin) throws SQLException {
        this.plugin = plugin;
        url = plugin.getConfig().getString("database.jdbc-url", "");
        user = plugin.getConfig().getString("database.username", "");
        password = plugin.getConfig().getString("database.password", "");
        mysql = url.startsWith("jdbc:mysql:");

        if (url.isBlank()) {
            throw new SQLException("database.jdbc-url is required");
        }

        try (Connection c = connect(); Statement s = c.createStatement()) {
            if (mysql) {
                initializeMysql(c, s);
                initializeWaterCareers();
                return;
            }
            c.setAutoCommit(false);
            s.execute("SELECT pg_advisory_xact_lock(1146441285)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS dune_players (uuid UUID PRIMARY KEY, faction TEXT NOT NULL DEFAULT '', solaris INTEGER NOT NULL DEFAULT 0, inventory TEXT)");
            s.executeUpdate("ALTER TABLE dune_players ADD COLUMN IF NOT EXISTS inventory_view JSONB");
            s.executeUpdate("ALTER TABLE dune_players ADD COLUMN IF NOT EXISTS inventory_updated_at TIMESTAMPTZ");
            s.executeUpdate("ALTER TABLE dune_players ADD COLUMN IF NOT EXISTS experience_level INTEGER");
            s.executeUpdate("ALTER TABLE dune_players ADD COLUMN IF NOT EXISTS experience_progress REAL");
            s.executeUpdate("ALTER TABLE dune_players ADD COLUMN IF NOT EXISTS total_experience INTEGER");
            s.executeUpdate("ALTER TABLE dune_players ADD COLUMN IF NOT EXISTS held_slot INTEGER");
            s.executeUpdate("ALTER TABLE dune_players ADD COLUMN IF NOT EXISTS session_open BOOLEAN NOT NULL DEFAULT FALSE");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS dune_politics (id INTEGER PRIMARY KEY, yaml TEXT NOT NULL)");
            s.executeUpdate("INSERT INTO dune_politics (id,yaml) VALUES (1,'') ON CONFLICT (id) DO NOTHING");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS dune_arrakis_permits (player UUID PRIMARY KEY, expires_at BIGINT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS dune_bounties (target UUID PRIMARY KEY, world TEXT NOT NULL, reward INTEGER NOT NULL, claimed BOOLEAN NOT NULL DEFAULT FALSE, hunter UUID)");
            c.commit();
        }
        initializeWaterCareers();
    }

    public void initializeWaterCareers() throws SQLException {
        try (Connection c=connect(); Statement s=c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS dune_water_careers (uuid VARCHAR(36) PRIMARY KEY, career VARCHAR(16) NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        }
    }
    public String loadWaterCareer(UUID id) throws SQLException {
        Objects.requireNonNull(id,"player UUID");
        try(Connection c=connect(); PreparedStatement q=c.prepareStatement("SELECT career FROM dune_water_careers WHERE uuid=?")) {
            q.setString(1,id.toString());try(ResultSet rows=q.executeQuery()) {
                if(!rows.next())return "none";
                String career=rows.getString(1);return "smuggler".equals(career)||"guild".equals(career)?career:"none";
            }
        }
    }
    public void saveWaterCareer(UUID id,String career) throws SQLException {
        Objects.requireNonNull(id,"player UUID");
        if(!"guild".equals(career)&&!"smuggler".equals(career))throw new IllegalArgumentException("Explicit guild or smuggler career required");
        String sql=mysql?"INSERT INTO dune_water_careers(uuid,career) VALUES(?,?) ON DUPLICATE KEY UPDATE career=VALUES(career),updated_at=CURRENT_TIMESTAMP":"INSERT INTO dune_water_careers(uuid,career) VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET career=EXCLUDED.career,updated_at=CURRENT_TIMESTAMP";
        try(Connection c=connect();PreparedStatement q=c.prepareStatement(sql)) {q.setString(1,id.toString());q.setString(2,career);q.executeUpdate();}
    }

    public Connection connect() throws SQLException {
        try { Class.forName(mysql ? "com.mysql.cj.jdbc.Driver" : "org.postgresql.Driver"); }
        catch (ClassNotFoundException ex) { throw new SQLException((mysql ? "MySQL" : "PostgreSQL") + " driver unavailable", ex); }
        Properties properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        properties.setProperty("connectTimeout", mysql ? "5000" : "5");
        properties.setProperty("socketTimeout", mysql ? "10000" : "10");
        properties.setProperty("tcpKeepAlive", "true");
        if (mysql) {
            // Reconnecting transparently would discard the named session lock.
            properties.setProperty("autoReconnect", "false");
            properties.setProperty("useAffectedRows", "false");
            properties.setProperty("connectionTimeZone", "UTC");
            properties.setProperty("forceConnectionTimeZoneToSession", "true");
            properties.setProperty("allowMultiQueries", "false");
        } else properties.setProperty("ApplicationName", "DuneRealms-Core");
        return DriverManager.getConnection(url, properties);
    }

    private void initializeMysql(Connection c, Statement s) throws SQLException {
        // MySQL DDL implicitly commits. A connection-scoped lock, not a pretend
        // DDL transaction, serializes idempotent upgrades across the backends.
        String schemaLock = "dune:core:schema:v1";
        if (!acquireMysqlLock(c, schemaLock, 10)) throw new SQLException("Network schema initialization is busy");
        try {
            for (String ddl : mysqlSchema()) s.executeUpdate(ddl);
            Map<String,String> upgrades = new LinkedHashMap<>();
            upgrades.put("inventory_view", "JSON");
            upgrades.put("inventory_updated_at", "DATETIME(6) NULL");
            upgrades.put("experience_level", "INTEGER NULL");
            upgrades.put("experience_progress", "FLOAT NULL");
            upgrades.put("total_experience", "INTEGER NULL");
            upgrades.put("held_slot", "INTEGER NULL");
            upgrades.put("session_open", "BOOLEAN NOT NULL DEFAULT FALSE");
            for (Map.Entry<String,String> column : upgrades.entrySet()) {
                try (ResultSet found = c.getMetaData().getColumns(c.getCatalog(), null, "dune_players", column.getKey())) {
                    if (!found.next()) s.executeUpdate("ALTER TABLE dune_players ADD COLUMN " + column.getKey() + " " + column.getValue());
                }
            }
            // A trigger shares the inventory UPDATE's own transaction, including
            // ordinary autocommit saves. Never emit on unchanged autosave rows.
            try (PreparedStatement q = c.prepareStatement("SELECT 1 FROM INFORMATION_SCHEMA.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME=?")) {
                q.setString(1, "dune_players_faction_event");
                try (ResultSet found = q.executeQuery()) {
                    if (!found.next()) s.executeUpdate(mysqlFactionTrigger());
                }
            }
            s.executeUpdate("INSERT INTO dune_politics (id,yaml) VALUES (1,'') ON DUPLICATE KEY UPDATE id=id");
        } finally { releaseMysqlLock(c, schemaLock); }
    }

    static String mysqlFactionTrigger() {
        return "CREATE TRIGGER dune_players_faction_event AFTER UPDATE ON dune_players FOR EACH ROW BEGIN IF NOT (OLD.faction <=> NEW.faction) THEN INSERT INTO dune_game_events(topic) VALUES('politics'); END IF; END";
    }

    static List<String> mysqlSchema() {
        String uuid = "VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin";
        String engine = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        return List.of(
            "CREATE TABLE IF NOT EXISTS dune_players (uuid " + uuid + " PRIMARY KEY, faction VARCHAR(32) NOT NULL DEFAULT '', solaris INTEGER NOT NULL DEFAULT 0, inventory LONGTEXT, inventory_view JSON, inventory_updated_at DATETIME(6) NULL, experience_level INTEGER NULL, experience_progress FLOAT NULL, total_experience INTEGER NULL, held_slot INTEGER NULL, session_open BOOLEAN NOT NULL DEFAULT FALSE)" + engine,
            "CREATE TABLE IF NOT EXISTS dune_politics (id INTEGER PRIMARY KEY, yaml LONGTEXT NOT NULL)" + engine,
            "CREATE TABLE IF NOT EXISTS dune_arrakis_permits (player " + uuid + " PRIMARY KEY, expires_at BIGINT NOT NULL)" + engine,
            "CREATE TABLE IF NOT EXISTS dune_bounties (target " + uuid + " PRIMARY KEY, world VARCHAR(64) NOT NULL, reward INTEGER NOT NULL, claimed BOOLEAN NOT NULL DEFAULT FALSE, hunter " + uuid + ")" + engine,
            "CREATE TABLE IF NOT EXISTS dune_game_events (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, topic VARCHAR(64) NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6))" + engine
        );
    }

    private static boolean acquireMysqlLock(Connection c, String name, int seconds) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT GET_LOCK(?,?)")) {
            q.setString(1, name); q.setInt(2, seconds);
            try (ResultSet r = q.executeQuery()) { return r.next() && r.getInt(1) == 1; }
        }
    }

    private static void releaseMysqlLock(Connection c, String name) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            q.setString(1, name); q.execute();
        }
    }

    private void bindUuid(PreparedStatement q, int index, UUID id) throws SQLException {
        if (mysql) q.setString(index, id.toString()); else q.setObject(index, id);
    }

    private void closeOwnedConnection(UUID id, Connection c) throws SQLException {
        SQLException failure = null;
        if (mysql) try { releaseMysqlLock(c, "dune:profile:" + id); } catch (SQLException ex) { failure = ex; }
        try { c.close(); } catch (SQLException ex) { if (failure == null) failure = ex; else failure.addSuppressed(ex); }
        if (failure != null) throw failure;
    }

    private static Integer integer(Object value) { return value == null ? null : ((Number)value).intValue(); }
    private static Float decimal(Object value) { return value == null ? null : ((Number)value).floatValue(); }

    public boolean isLoaded(UUID id) { return sessions.containsKey(id); }

    public void loadPlayer(Player p, Map<UUID, String> factions) throws Exception {
        UUID id = p.getUniqueId();
        if (sessions.containsKey(id)) throw new SQLException("Profile already loaded on this backend");
        Connection c = connect();
        boolean retained = false;
        try {
            if (mysql) {
                if (!acquireMysqlLock(c, "dune:profile:" + id, 0))
                    throw new SQLException("Profile is still active on another backend; reconnect shortly");
            } else try (PreparedStatement lock = c.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                lock.setLong(1, id.getMostSignificantBits() ^ id.getLeastSignificantBits());
                try (ResultSet r = lock.executeQuery()) {
                    if (!r.next() || !r.getBoolean(1))
                        throw new SQLException("Profile is still active on another backend; reconnect shortly");
                }
            }
            try (PreparedStatement ins = c.prepareStatement(
                    mysql ? "INSERT INTO dune_players(uuid,faction) VALUES (?,?) ON DUPLICATE KEY UPDATE uuid=uuid" : "INSERT INTO dune_players(uuid,faction) VALUES (?,?) ON CONFLICT DO NOTHING")) {
                bindUuid(ins, 1, id);
                ins.setString(2, factions.getOrDefault(id, ""));
                ins.executeUpdate();
            }
            ItemStack[] inventory = null;
            String faction;
            Integer level, total, slot;
            Float progress;
            try (PreparedStatement q = c.prepareStatement(
                    "SELECT faction,inventory,experience_level,experience_progress,total_experience,held_slot,session_open FROM dune_players WHERE uuid=?")) {
                bindUuid(q, 1, id);
                try (ResultSet r = q.executeQuery()) {
                    if (!r.next()) throw new SQLException("Missing network profile");
                    if (r.getBoolean("session_open"))
                        throw new SQLException("Unclean previous session; administrator recovery required");
                    faction = r.getString("faction");
                    String blob = r.getString("inventory");
                    if (blob != null && !blob.isBlank()) inventory = decode(blob);
                    level = integer(r.getObject("experience_level"));
                    progress = decimal(r.getObject("experience_progress"));
                    total = integer(r.getObject("total_experience"));
                    slot = integer(r.getObject("held_slot"));
                }
            }
            // Decode and validate everything before changing the player or marking a session open.
            if (inventory != null && inventory.length != p.getInventory().getContents().length)
                throw new IOException("Network inventory has an unexpected slot count");
            if ((level != null && level < 0) || (total != null && total < 0)
                    || (progress != null && (!Float.isFinite(progress) || progress < 0 || progress >= 1))
                    || (slot != null && (slot < 0 || slot > 8)))
                throw new IOException("Network experience or selected slot is invalid");
            try (PreparedStatement q = c.prepareStatement("UPDATE dune_players SET session_open=TRUE WHERE uuid=?")) {
                bindUuid(q, 1, id);
                if (q.executeUpdate() != 1) throw new SQLException("Network profile disappeared before load");
            }
            // Keep the durable marker if any application step fails: do not overwrite the profile.
            if (inventory != null) p.getInventory().setContents(inventory);
            if (level != null) p.setLevel(level);
            if (progress != null) p.setExp(progress);
            if (total != null) p.setTotalExperience(total);
            if (slot != null) p.getInventory().setHeldItemSlot(slot);
            factions.put(id, faction);
            sessions.put(id, c);
            retained = true;
        } finally {
            if (!retained) closeOwnedConnection(id, c);
        }
    }

    public void savePlayer(Player p, Map<UUID, String> factions) throws Exception {
        writePlayer(p, factions, false);
    }

    /** Write and clear the durable marker before releasing the lock for the next backend. */
    public void releasePlayer(Player p, Map<UUID, String> factions) throws Exception {
        if (!isLoaded(p.getUniqueId())) return; // A failed load must never save stale local items.
        try {
            p.closeInventory(); // Return cursor and crafting items before the final snapshot.
            writePlayer(p, factions, true);
        } finally {
            Connection c = sessions.remove(p.getUniqueId());
            if (c != null) closeOwnedConnection(p.getUniqueId(), c);
        }
    }

    private void writePlayer(Player p, Map<UUID, String> factions, boolean release) throws Exception {
        Connection c = sessions.get(p.getUniqueId());
        if (c == null) return;
        // Never reconnect here: a replacement connection would not own the session lock.
        String inventory = encode(p.getInventory().getContents());
        try (PreparedStatement q = c.prepareStatement(
                "UPDATE dune_players SET faction=?,inventory=?,experience_level=?,experience_progress=?,total_experience=?,held_slot=?,session_open=?,inventory_view=" + (mysql ? "?" : "?::jsonb") + ",inventory_updated_at=CURRENT_TIMESTAMP WHERE uuid=?")) {
            q.setString(1, factions.getOrDefault(p.getUniqueId(), ""));
            q.setString(2, inventory);
            q.setInt(3, p.getLevel());
            q.setFloat(4, p.getExp());
            q.setInt(5, p.getTotalExperience());
            q.setInt(6, p.getInventory().getHeldItemSlot());
            q.setBoolean(7, !release);
            q.setString(8, InventoryWebSnapshot.capture(p));
            bindUuid(q, 9, p.getUniqueId());
            if (q.executeUpdate() != 1) throw new SQLException("Network profile disappeared during save");
        } catch (SQLException ex) {
            try {
                org.bukkit.configuration.file.YamlConfiguration recovery = new org.bukkit.configuration.file.YamlConfiguration();
                recovery.set("uuid", p.getUniqueId().toString());
                recovery.set("captured-at", java.time.Instant.now().toString());
                recovery.set("faction", factions.getOrDefault(p.getUniqueId(), ""));
                recovery.set("inventory", inventory);
                recovery.set("experience-level", p.getLevel());
                recovery.set("experience-progress", p.getExp());
                recovery.set("total-experience", p.getTotalExperience());
                recovery.set("held-slot", p.getInventory().getHeldItemSlot());
                File folder = new File(plugin.getDataFolder(), "inventory-recovery");
                if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Cannot create recovery directory");
                recovery.save(new File(folder, p.getUniqueId() + "-" + System.nanoTime() + ".yml"));
            } catch (IOException recoveryError) { ex.addSuppressed(recoveryError); }
            throw ex;
        }
    }

    /** Exchange SQL and the resulting authoritative inventory checkpoint commit together. */
    public void transaction(Player p, Map<UUID, String> factions, java.util.function.Consumer<Connection> operation) throws Exception {
        Connection c = sessions.get(p.getUniqueId());
        if (c == null) throw new SQLException("A loaded shared network profile is required to trade");
        ItemStack[] before = Arrays.stream(p.getInventory().getContents())
                .map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
        boolean committing = false;
        c.setAutoCommit(false);
        try {
            operation.accept(c);
            writePlayer(p, factions, false);
            committing = true;
            c.commit();
        } catch (Exception ex) {
            boolean recovered = false;
            if (!committing) try {
                c.rollback();
                p.getInventory().setContents(before);
                recovered = true;
            } catch (Exception rollback) { ex.addSuppressed(rollback); }
            if (!recovered) {
                // Unknown commit result: never perform an ordinary quit save over the evidence.
                sessions.remove(p.getUniqueId());
                try { closeOwnedConnection(p.getUniqueId(), c); } catch (SQLException close) { ex.addSuppressed(close); }
                p.kickPlayer("Exchange storage interrupted. An administrator must review your network profile.");
            }
            throw ex;
        } finally {
            if (sessions.containsKey(p.getUniqueId())) c.setAutoCommit(true);
        }
    }

    @Override public void close() {
        for (Map.Entry<UUID,Connection> session : sessions.entrySet()) try { closeOwnedConnection(session.getKey(), session.getValue()); }
        catch (SQLException ex) { plugin.getLogger().warning("Profile connection close failed: " + ex.getMessage()); }
        sessions.clear();
    }

    public String loadPolitics() throws SQLException {
        try (Connection c = connect();
             PreparedStatement q = c.prepareStatement(
                     "SELECT yaml FROM dune_politics WHERE id=1");
             ResultSet r = q.executeQuery()) {
            return r.next() ? r.getString(1) : "";
        }
    }

    public void savePolitics(String yaml) throws SQLException {
        if (mysql) {
            saveMysqlPolitics(yaml);
            return;
        }
        try (Connection c = connect();
             PreparedStatement q = c.prepareStatement(
                     "UPDATE dune_politics SET yaml=? WHERE id=1")) {
            q.setString(1, yaml);
            q.executeUpdate();
        }
    }

    private void saveMysqlPolitics(String yaml) throws SQLException {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement q = c.prepareStatement("UPDATE dune_politics SET yaml=? WHERE id=1 AND BINARY yaml<>BINARY ?")) {
                    q.setString(1, yaml); q.setString(2, yaml); changed = q.executeUpdate();
                }
                if (changed > 0) try (PreparedStatement q = c.prepareStatement("INSERT INTO dune_game_events(topic) VALUES('politics')")) {
                    q.executeUpdate();
                }
                c.commit();
            } catch (SQLException ex) { try { c.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); } throw ex; }
        }
    }

    /** Short-lived, single-use authorization for an Emperor-approved arrival. */
    public void issueArrakisPermit(UUID player, long expiresAt)
            throws SQLException {
        try (Connection c = connect();
             PreparedStatement q = c.prepareStatement(
                     mysql ? "INSERT INTO dune_arrakis_permits(player,expires_at) VALUES (?,?) ON DUPLICATE KEY UPDATE expires_at=VALUES(expires_at)" : "INSERT INTO dune_arrakis_permits(player,expires_at) "
                     + "VALUES (?,?) ON CONFLICT(player) "
                     + "DO UPDATE SET expires_at=EXCLUDED.expires_at")) {
            bindUuid(q, 1, player);
            q.setLong(2, expiresAt);
            q.executeUpdate();
        }
    }

    public boolean consumeArrakisPermit(UUID player) throws SQLException {
        try (Connection c = connect();
             PreparedStatement q = c.prepareStatement(
                     "DELETE FROM dune_arrakis_permits "
                     + "WHERE player=? AND expires_at>?" + (mysql ? "" : " RETURNING player"))) {
            bindUuid(q, 1, player);
            q.setLong(2, System.currentTimeMillis());

            if (mysql) return q.executeUpdate() == 1;
            try (ResultSet r = q.executeQuery()) {
                return r.next();
            }
        }
    }

    public void issueBounty(UUID target, String world, int reward)
            throws SQLException {
        try (Connection c = connect();
             PreparedStatement q = c.prepareStatement(
                     mysql ? "INSERT INTO dune_bounties(target,world,reward,claimed,hunter) VALUES(?,?,?,FALSE,NULL) ON DUPLICATE KEY UPDATE world=VALUES(world),reward=VALUES(reward),claimed=FALSE,hunter=NULL" : "INSERT INTO dune_bounties(target,world,reward,claimed,hunter) "
                     + "VALUES(?,?,?,FALSE,NULL) ON CONFLICT(target) "
                     + "DO UPDATE SET world=EXCLUDED.world,"
                     + "reward=EXCLUDED.reward,claimed=FALSE,hunter=NULL")) {
            bindUuid(q, 1, target);
            q.setString(2, world);
            q.setInt(3, reward);
            q.executeUpdate();
        }
    }

    public int claimBounty(UUID target, UUID hunter) throws SQLException {
        if (mysql) return claimMysqlBounty(target, hunter);
        try (Connection c = connect();
             PreparedStatement q = c.prepareStatement(
                     "UPDATE dune_bounties SET claimed=TRUE,hunter=? "
                     + "WHERE target=? AND claimed=FALSE RETURNING reward")) {
            q.setObject(1, hunter);
            q.setObject(2, target);

            try (ResultSet r = q.executeQuery()) {
                return r.next() ? r.getInt(1) : 0;
            }
        }
    }

    private int claimMysqlBounty(UUID target, UUID hunter) throws SQLException {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                int reward;
                try (PreparedStatement q = c.prepareStatement("SELECT reward FROM dune_bounties WHERE target=? AND claimed=FALSE FOR UPDATE")) {
                    bindUuid(q, 1, target);
                    try (ResultSet r = q.executeQuery()) {
                        if (!r.next()) { c.rollback(); return 0; }
                        reward = r.getInt(1);
                    }
                }
                try (PreparedStatement q = c.prepareStatement("UPDATE dune_bounties SET claimed=TRUE,hunter=? WHERE target=? AND claimed=FALSE")) {
                    bindUuid(q, 1, hunter); bindUuid(q, 2, target);
                    if (q.executeUpdate() != 1) throw new SQLException("Bounty changed before claim");
                }
                c.commit(); return reward;
            } catch (SQLException ex) { try { c.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); } throw ex; }
        }
    }

    private String encode(ItemStack[] items) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (BukkitObjectOutputStream out =
                     new BukkitObjectOutputStream(bytes)) {
            out.writeInt(items.length);

            for (ItemStack item : items) {
                out.writeObject(item);
            }
        }

        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private ItemStack[] decode(String str) throws Exception {
        byte[] data = Base64.getDecoder().decode(str);

        try (BukkitObjectInputStream in =
                     new BukkitObjectInputStream(
                             new ByteArrayInputStream(data))) {
            int n = in.readInt();

            if (n < 0 || n > 54) {
                throw new IOException("Invalid inventory length");
            }

            ItemStack[] items = new ItemStack[n];

            for (int i = 0; i < n; i++) {
                items[i] = (ItemStack) in.readObject();
            }

            return items;
        }
    }
}
