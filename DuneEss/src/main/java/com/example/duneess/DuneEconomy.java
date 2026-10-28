package com.example.duneess;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DuneEss's Solaris ledger and Vault economy service. */
@SuppressWarnings("deprecation")
final class DuneEconomy implements Economy {
    private final DuneEss plugin;
    private final Map<UUID, Double> balances = new HashMap<>();
    private final File file;
    private final YamlConfiguration data;
    private final boolean allowNegative;
    private final String singular, plural;
    private String jdbcUrl = "", dbUser = "", dbPassword = "";
    private boolean databaseEnabled;
    private boolean sharedDatabaseRequested;

    DuneEconomy(DuneEss plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "balances.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        this.allowNegative = plugin.getConfig().getBoolean("currency.allow-negative-balances", true);
        String configuredSingular = plugin.getConfig().getString("currency.singular", "Dunemark");
        this.singular = configuredSingular.equalsIgnoreCase("Solaris") ? "Dunemark" : configuredSingular;
        String configuredPlural = plugin.getConfig().getString("currency.plural", "Dunemarks");
        this.plural = configuredPlural.equalsIgnoreCase("Solaris") ? "Dunemarks" : configuredPlural;
        loadDatabaseSettings();
        loadBalances();
    }

    private void loadDatabaseSettings() {
        if (!plugin.getConfig().getBoolean("database.use-core-database", true)) return;
        File coreConfig = new File(plugin.getDataFolder().getParentFile(), "DuneCore/config.yml");
        if (!coreConfig.isFile()) return;
        YamlConfiguration core = YamlConfiguration.loadConfiguration(coreConfig);
        if (!core.getBoolean("database.enabled", false)) return;
        sharedDatabaseRequested = true;
        jdbcUrl = core.getString("database.jdbc-url", "");
        dbUser = core.getString("database.username", "");
        dbPassword = core.getString("database.password", "");
        databaseEnabled = !jdbcUrl.isBlank();
    }

    boolean sharedDatabaseReady() { return databaseEnabled; }

    private Connection connect() throws SQLException {
        try { Class.forName("org.postgresql.Driver"); }
        catch (ClassNotFoundException ex) { throw new SQLException("PostgreSQL driver unavailable", ex); }
        java.util.Properties settings=new java.util.Properties();
        settings.setProperty("user",dbUser);settings.setProperty("password",dbPassword);
        settings.setProperty("connectTimeout","5");settings.setProperty("socketTimeout","10");
        return DriverManager.getConnection(jdbcUrl,settings);
    }

    private void loadBalances() {
        try { file.getParentFile().mkdirs(); } catch (Exception ignored) { }
        for (String key : data.getKeys(false)) {
            try { balances.put(UUID.fromString(key), money(data.getDouble(key))); }
            catch (IllegalArgumentException ignored) { }
        }
        importLegacyFile();
        if (databaseEnabled) {
            try (Connection c = connect(); Statement s = c.createStatement()) {
                s.executeUpdate("CREATE TABLE IF NOT EXISTS dune_economy_balances (uuid UUID PRIMARY KEY, balance NUMERIC(20,2) NOT NULL DEFAULT 0)");
                boolean empty;
                try (ResultSet rows = s.executeQuery("SELECT NOT EXISTS (SELECT 1 FROM dune_economy_balances)")) {
                    empty = rows.next() && rows.getBoolean(1);
                }
                if (empty) {
                    importLegacyDatabase(c);
                    importLocalBalances(c);
                }
                try (ResultSet rows = s.executeQuery("SELECT uuid,balance FROM dune_economy_balances")) {
                    while (rows.next()) balances.put(rows.getObject(1, UUID.class), money(rows.getDouble(2)));
                }
            } catch (SQLException ex) {
                plugin.getLogger().severe("Shared Solaris database unavailable; network transactions remain disabled until it recovers: " + ex.getMessage());
            }
        }
        saveLocal();
    }

    private void importLegacyFile() {
        if (!balances.isEmpty()) return;
        File old = new File(plugin.getDataFolder().getParentFile(), "DuneCore/players.yml");
        if (!old.isFile()) old = new File(plugin.getDataFolder().getParentFile(), "DuneRealms/players.yml");
        if (!old.isFile()) return;
        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(old);
        for (String key : legacy.getKeys(false)) try {
            UUID id = UUID.fromString(key);
            if (legacy.contains(key + ".solaris")) balances.put(id, money(legacy.getDouble(key + ".solaris")));
        } catch (IllegalArgumentException ignored) { }
        if (!balances.isEmpty()) plugin.getLogger().info("Imported " + balances.size() + " Solaris balances from the previous Dune data file.");
    }

    private void importLegacyDatabase(Connection c) {
        try (Statement s = c.createStatement(); ResultSet rows = s.executeQuery("SELECT uuid,solaris FROM dune_players WHERE solaris<>0")) {
            try (PreparedStatement insert = c.prepareStatement("INSERT INTO dune_economy_balances(uuid,balance) VALUES (?,?) ON CONFLICT(uuid) DO NOTHING")) {
                while (rows.next()) {
                    insert.setObject(1, rows.getObject(1, UUID.class));
                    insert.setBigDecimal(2, BigDecimal.valueOf(rows.getDouble(2)));
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            plugin.getLogger().info("Imported legacy Solaris balances from the shared Dune player table.");
        } catch (SQLException ignored) {
            // Older installations may not have enabled the shared player database.
        }
    }

    private void importLocalBalances(Connection c) {
        if (balances.isEmpty()) return;
        try (PreparedStatement insert = c.prepareStatement(
                "INSERT INTO dune_economy_balances(uuid,balance) VALUES (?,?) ON CONFLICT(uuid) DO NOTHING")) {
            for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
                insert.setObject(1, entry.getKey());
                insert.setBigDecimal(2, BigDecimal.valueOf(entry.getValue()));
                insert.addBatch();
            }
            insert.executeBatch();
            plugin.getLogger().info("Migrated local Solaris balances into the shared economy database.");
        } catch (SQLException ex) {
            plugin.getLogger().warning("Could not migrate local Solaris balances to PostgreSQL: " + ex.getMessage());
        }
    }

    private static double money(double amount) {
        if (!Double.isFinite(amount)) return 0;
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private synchronized void saveLocal() {
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) data.set(entry.getKey().toString(), entry.getValue());
        try { writeCommunitySnapshot(data); }
        catch (Exception ex) { plugin.getLogger().severe("Could not save Solaris balances: " + ex.getMessage()); }
    }

    private synchronized double balance(UUID id) {
        if (databaseEnabled) {
            try (Connection c = connect(); PreparedStatement q = c.prepareStatement("SELECT balance FROM dune_economy_balances WHERE uuid=?")) {
                q.setObject(1, id);
                try (ResultSet row = q.executeQuery()) {
                    if (row.next()) {
                        double value = money(row.getDouble(1));
                        balances.put(id, value);
                        return value;
                    }
                }
            } catch (SQLException ex) { plugin.getLogger().warning("Solaris balance read failed: " + ex.getMessage()); }
            return 0; // Never authorize a network payment against stale local cache.
        }
        return balances.getOrDefault(id, 0.0);
    }

    private synchronized void set(UUID id, double value) {
        value = money(value);
        balances.put(id, value);
        if (databaseEnabled) {
            try (Connection c = connect(); PreparedStatement q = c.prepareStatement(
                    "INSERT INTO dune_economy_balances(uuid,balance) VALUES (?,?) ON CONFLICT(uuid) DO UPDATE SET balance=EXCLUDED.balance")) {
                q.setObject(1, id); q.setBigDecimal(2, BigDecimal.valueOf(value)); q.executeUpdate();
            } catch (SQLException ex) { plugin.getLogger().severe("Solaris database save failed: " + ex.getMessage()); }
        }
        saveLocal();
    }

    synchronized void add(UUID id, double amount) { change(id, amount); }
    synchronized void subtract(UUID id, double amount) { change(id, -amount); }

    private synchronized EconomyResponse change(UUID id, double delta) {
        if (!Double.isFinite(delta)) return response(0, 0, EconomyResponse.ResponseType.FAILURE, "Invalid amount.");
        if (sharedDatabaseRequested && !databaseEnabled) return response(Math.abs(delta),0,EconomyResponse.ResponseType.FAILURE,"Shared economy is unavailable.");
        if (!databaseEnabled) {
            double next=money(balance(id)+delta);
            if (!allowNegative && next<0) return response(Math.abs(delta),balance(id),EconomyResponse.ResponseType.FAILURE,"Insufficient funds.");
            set(id,next); return response(Math.abs(delta),next,EconomyResponse.ResponseType.SUCCESS,null);
        }
        try (Connection c=connect()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement create=c.prepareStatement("INSERT INTO dune_economy_balances(uuid,balance) VALUES (?,0) ON CONFLICT(uuid) DO NOTHING")) {
                    create.setObject(1,id);create.executeUpdate();
                }
                double updated;
                try (PreparedStatement q=c.prepareStatement("UPDATE dune_economy_balances SET balance=balance+? WHERE uuid=? AND (? OR balance+?>=0) RETURNING balance")) {
                    BigDecimal amount=BigDecimal.valueOf(money(delta));
                    q.setBigDecimal(1,amount);q.setObject(2,id);q.setBoolean(3,allowNegative);q.setBigDecimal(4,amount);
                    try (ResultSet row=q.executeQuery()) {
                        if (!row.next()) {c.rollback();return response(Math.abs(delta),0,EconomyResponse.ResponseType.FAILURE,"Insufficient funds.");}
                        updated=row.getDouble(1);
                    }
                }
                c.commit();balances.put(id,updated);saveLocal();
                return response(Math.abs(delta),updated,EconomyResponse.ResponseType.SUCCESS,null);
            } catch(SQLException ex) {c.rollback();throw ex;}
        } catch(SQLException ex) {
            plugin.getLogger().warning("Shared Solaris transaction failed: "+ex.getSQLState());
            return response(Math.abs(delta),0,EconomyResponse.ResponseType.FAILURE,"Shared economy is unavailable.");
        }
    }

    /** One durable credit per UUID/month. A failed shared ledger never falls back to a second ledger. */
    synchronized boolean grantCommunity(UUID id,String month,int amount,String receipt) throws Exception {
        if(!month.matches("20\\d\\d-(0[1-9]|1[0-2])")||amount<1||amount>10000||!receipt.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid community grant");
        if(sharedDatabaseRequested&&!databaseEnabled)throw new IOException("Shared economy is unavailable");
        String key="community-claims."+id+"."+month;
        if(databaseEnabled){
            try(Connection c=connect()){
                try(Statement s=c.createStatement()){s.executeUpdate("CREATE TABLE IF NOT EXISTS dune_community_claims (uuid UUID NOT NULL, month VARCHAR(7) NOT NULL, receipt VARCHAR(64) NOT NULL, amount INTEGER NOT NULL, acked BOOLEAN NOT NULL DEFAULT FALSE, PRIMARY KEY(uuid,month))");}
                c.setAutoCommit(false);
                try{
                    int inserted;
                    try(PreparedStatement s=c.prepareStatement("INSERT INTO dune_community_claims(uuid,month,receipt,amount) VALUES(?,?,?,?) ON CONFLICT(uuid,month) DO NOTHING")){s.setObject(1,id);s.setString(2,month);s.setString(3,receipt);s.setInt(4,amount);inserted=s.executeUpdate();}
                    if(inserted==0){try(PreparedStatement s=c.prepareStatement("SELECT receipt,amount FROM dune_community_claims WHERE uuid=? AND month=?")){s.setObject(1,id);s.setString(2,month);try(ResultSet r=s.executeQuery()){if(!r.next()||!receipt.equals(r.getString(1))||amount!=r.getInt(2))throw new IOException("Community receipt mismatch");}}c.commit();return false;}
                    double updated;
                    try(PreparedStatement s=c.prepareStatement("INSERT INTO dune_economy_balances(uuid,balance) VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET balance=dune_economy_balances.balance+EXCLUDED.balance RETURNING balance")){s.setObject(1,id);s.setBigDecimal(2,BigDecimal.valueOf(amount));try(ResultSet r=s.executeQuery()){r.next();updated=r.getDouble(1);}}
                    c.commit();balances.put(id,money(updated));saveLocal();return true;
                }catch(Exception error){c.rollback();throw error;}
            }
        }
        if(data.contains(key+".receipt")){if(!receipt.equals(data.getString(key+".receipt"))||amount!=data.getInt(key+".amount"))throw new IOException("Community receipt mismatch");return false;}
        YamlConfiguration next=new YamlConfiguration();next.loadFromString(data.saveToString());
        double updated=money(balances.getOrDefault(id,0.0)+amount);
        next.set(id.toString(),updated);next.set(key+".receipt",receipt);next.set(key+".amount",amount);next.set(key+".acked",false);
        writeCommunitySnapshot(next);balances.put(id,updated);return true;
    }
    private void writeCommunitySnapshot(YamlConfiguration next) throws Exception {
        java.nio.file.Path target=file.toPath(),temporary=target.resolveSibling("balances.yml.community-tmp");
        java.nio.file.Files.createDirectories(target.getParent());
        byte[] bytes=next.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try(java.nio.channels.FileChannel channel=java.nio.channels.FileChannel.open(temporary,java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,java.nio.file.StandardOpenOption.WRITE)){java.nio.ByteBuffer buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}
        java.nio.file.Files.move(temporary,target,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        data.loadFromString(next.saveToString());
    }
    synchronized Map<UUID,List<String>> pendingCommunityReceipts() throws Exception {
        Map<UUID,List<String>> pending=new HashMap<>();
        if(sharedDatabaseRequested&&!databaseEnabled)return pending;
        if(databaseEnabled){try(Connection c=connect();Statement s=c.createStatement()){
            s.executeUpdate("CREATE TABLE IF NOT EXISTS dune_community_claims (uuid UUID NOT NULL, month VARCHAR(7) NOT NULL, receipt VARCHAR(64) NOT NULL, amount INTEGER NOT NULL, acked BOOLEAN NOT NULL DEFAULT FALSE, PRIMARY KEY(uuid,month))");
            try(ResultSet r=s.executeQuery("SELECT uuid,receipt FROM dune_community_claims WHERE acked=FALSE LIMIT 100")){while(r.next())pending.computeIfAbsent(r.getObject(1,UUID.class),ignored->new ArrayList<>()).add(r.getString(2));}
        }return pending;}
        var section=data.getConfigurationSection("community-claims");if(section==null)return pending;
        for(String uuid:section.getKeys(false)){var months=section.getConfigurationSection(uuid);if(months==null)continue;for(String month:months.getKeys(false)){String key=uuid+"."+month;if(!section.getBoolean(key+".acked"))pending.computeIfAbsent(UUID.fromString(uuid),ignored->new ArrayList<>()).add(section.getString(key+".receipt"));}}
        return pending;
    }
    synchronized void acknowledgeCommunity(UUID id,String receipt) throws Exception {
        if(databaseEnabled){try(Connection c=connect();PreparedStatement s=c.prepareStatement("UPDATE dune_community_claims SET acked=TRUE WHERE uuid=? AND receipt=?")){s.setObject(1,id);s.setString(2,receipt);s.executeUpdate();}return;}
        YamlConfiguration next=new YamlConfiguration();next.loadFromString(data.saveToString());var months=next.getConfigurationSection("community-claims."+id);if(months==null)return;
        for(String month:months.getKeys(false))if(receipt.equals(months.getString(month+".receipt")))months.set(month+".acked",true);
        writeCommunitySnapshot(next);
    }

    private EconomyResponse response(double amount, double balance, EconomyResponse.ResponseType type, String message) {
        return new EconomyResponse(amount, balance, type, message);
    }
    private EconomyResponse deposit(OfflinePlayer player, double amount) {
        if (amount < 0 || !Double.isFinite(amount)) return response(amount, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Amount must be non-negative.");
        return change(player.getUniqueId(), amount);
    }
    private EconomyResponse withdraw(OfflinePlayer player, double amount) {
        double current = getBalance(player);
        if (amount < 0 || !Double.isFinite(amount)) return response(amount, current, EconomyResponse.ResponseType.FAILURE, "Amount must be non-negative.");
        if (!allowNegative && current < amount) return response(amount, current, EconomyResponse.ResponseType.FAILURE, "Insufficient funds.");
        return change(player.getUniqueId(), -amount);
    }

    @Override public boolean isEnabled() { return plugin.isEnabled(); }
    @Override public String getName() { return "DuneEss"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return 2; }
    @Override public String format(double amount) { return String.format(java.util.Locale.US, "%,.2f %s", amount, Math.abs(amount) == 1 ? singular : plural); }
    @Override public String currencyNamePlural() { return plural; }
    @Override public String currencyNameSingular() { return singular; }
    @Override public boolean hasAccount(String name) { return hasAccount(Bukkit.getOfflinePlayer(name)); }
    @Override public boolean hasAccount(OfflinePlayer player) { return balances.containsKey(player.getUniqueId()) || player.hasPlayedBefore() || player.isOnline(); }
    @Override public boolean hasAccount(String name, String world) { return hasAccount(name); }
    @Override public boolean hasAccount(OfflinePlayer player, String world) { return hasAccount(player); }
    @Override public double getBalance(String name) { return getBalance(Bukkit.getOfflinePlayer(name)); }
    @Override public double getBalance(OfflinePlayer player) { return balance(player.getUniqueId()); }
    @Override public double getBalance(String name, String world) { return getBalance(name); }
    @Override public double getBalance(OfflinePlayer player, String world) { return getBalance(player); }
    @Override public boolean has(String name, double amount) { return has(Bukkit.getOfflinePlayer(name), amount); }
    @Override public boolean has(OfflinePlayer player, double amount) { return amount >= 0 && getBalance(player) >= amount; }
    @Override public boolean has(String name, String world, double amount) { return has(name, amount); }
    @Override public boolean has(OfflinePlayer player, String world, double amount) { return has(player, amount); }
    @Override public EconomyResponse withdrawPlayer(String name, double amount) { return withdraw(Bukkit.getOfflinePlayer(name), amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) { return withdraw(player, amount); }
    @Override public EconomyResponse withdrawPlayer(String name, String world, double amount) { return withdrawPlayer(name, amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) { return withdraw(player, amount); }
    @Override public EconomyResponse depositPlayer(String name, double amount) { return deposit(Bukkit.getOfflinePlayer(name), amount); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, double amount) { return deposit(player, amount); }
    @Override public EconomyResponse depositPlayer(String name, String world, double amount) { return depositPlayer(name, amount); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) { return deposit(player, amount); }
    @Override public EconomyResponse createBank(String name, String player) { return unsupported(); }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return unsupported(); }
    @Override public EconomyResponse deleteBank(String name) { return unsupported(); }
    @Override public EconomyResponse bankBalance(String name) { return unsupported(); }
    @Override public EconomyResponse bankHas(String name, double amount) { return unsupported(); }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return unsupported(); }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return unsupported(); }
    @Override public EconomyResponse isBankOwner(String name, String player) { return unsupported(); }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return unsupported(); }
    @Override public EconomyResponse isBankMember(String name, String player) { return unsupported(); }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return unsupported(); }
    @Override public List<String> getBanks() { return new ArrayList<>(); }
    @Override public boolean createPlayerAccount(String name) { return createPlayerAccount(Bukkit.getOfflinePlayer(name)); }
    @Override public boolean createPlayerAccount(OfflinePlayer player) {
        UUID id = player.getUniqueId();
        if (databaseEnabled) {
            try (Connection c=connect();PreparedStatement q=c.prepareStatement("INSERT INTO dune_economy_balances(uuid,balance) VALUES (?,?) ON CONFLICT(uuid) DO NOTHING")) {
                q.setObject(1,id);q.setBigDecimal(2,BigDecimal.valueOf(money(plugin.getConfig().getDouble("currency.starting-balance",0))));q.executeUpdate();return true;
            } catch(SQLException ex) {return false;}
        }
        if (!balances.containsKey(id)) set(id, money(plugin.getConfig().getDouble("currency.starting-balance", 0)));
        return true;
    }
    @Override public boolean createPlayerAccount(String name, String world) { return createPlayerAccount(name); }
    @Override public boolean createPlayerAccount(OfflinePlayer player, String world) { return createPlayerAccount(player); }
    private EconomyResponse unsupported() { return response(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "DuneEss does not provide bank accounts."); }
}
