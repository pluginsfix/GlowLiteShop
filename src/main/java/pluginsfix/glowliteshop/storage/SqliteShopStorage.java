package pluginsfix.glowliteshop.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.inventory.ItemStack;
import pluginsfix.glowliteshop.domain.StoredItem;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SqliteShopStorage implements ShopStorage {
    private final File databaseFile;
    private final Logger logger;
    private HikariDataSource dataSource;
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2);

    public SqliteShopStorage(File dataFolder, Logger logger) {
        this.databaseFile = new File(dataFolder, "shop_data.db");
        this.logger = logger;
    }

    @Override
    public void initialize() {
        if (!databaseFile.getParentFile().exists()) {
            databaseFile.getParentFile().mkdirs();
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("GlowLiteShop-Pool");

        this.dataSource = new HikariDataSource(config);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS shop_chest (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid VARCHAR(36) NOT NULL,
                        item_data TEXT NOT NULL,
                        acquired_at BIGINT NOT NULL,
                        source_note VARCHAR(128) NOT NULL
                    );
                    """);

            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_shop_chest_player
                    ON shop_chest(player_uuid);
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_sapphires (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        amount DOUBLE NOT NULL DEFAULT 0.0
                    );
                    """);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize database tables", e);
        }
    }

    @Override
    public void close() {
        ioExecutor.shutdown();
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public CompletableFuture<Double> getInternalSapphires(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT amount FROM player_sapphires WHERE player_uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getDouble("amount");
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to get sapphires for " + playerUuid, e);
            }
            return 0.0;
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Void> setInternalSapphires(UUID playerUuid, double amount) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO player_sapphires (player_uuid, amount)
                    VALUES (?, ?)
                    ON CONFLICT(player_uuid) DO UPDATE SET amount = excluded.amount;
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setDouble(2, Math.max(0.0, amount));
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to set sapphires for " + playerUuid, e);
            }
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Void> addInternalSapphires(UUID playerUuid, double amount) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO player_sapphires (player_uuid, amount)
                    VALUES (?, ?)
                    ON CONFLICT(player_uuid) DO UPDATE SET amount = player_sapphires.amount + excluded.amount;
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setDouble(2, amount);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to add sapphires for " + playerUuid, e);
            }
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Boolean> removeInternalSapphires(UUID playerUuid, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    String selectSql = "SELECT amount FROM player_sapphires WHERE player_uuid = ?";
                    double current = 0.0;
                    try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                        ps.setString(1, playerUuid.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                current = rs.getDouble("amount");
                            }
                        }
                    }

                    if (current < amount) {
                        conn.rollback();
                        return false;
                    }

                    String updateSql = "UPDATE player_sapphires SET amount = amount - ? WHERE player_uuid = ?";
                    try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        ps.setDouble(1, amount);
                        ps.setString(2, playerUuid.toString());
                        ps.executeUpdate();
                    }

                    conn.commit();
                    return true;
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to remove sapphires for " + playerUuid, e);
                return false;
            }
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Long> insertStoredItem(UUID playerUuid, ItemStack item, String sourceNote) {
        return CompletableFuture.supplyAsync(() -> {
            String base64 = ItemSerializer.toBase64(item);
            long acquiredAt = System.currentTimeMillis();
            String sql = "INSERT INTO shop_chest (player_uuid, item_data, acquired_at, source_note) VALUES (?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, base64);
                ps.setLong(3, acquiredAt);
                ps.setString(4, sourceNote);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to insert stored item for " + playerUuid, e);
            }
            return -1L;
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<List<StoredItem>> getStoredItems(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<StoredItem> list = new ArrayList<>();
            String sql = "SELECT id, player_uuid, item_data, acquired_at, source_note FROM shop_chest WHERE player_uuid = ? ORDER BY id ASC";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                        String itemData = rs.getString("item_data");
                        long acquiredAt = rs.getLong("acquired_at");
                        String sourceNote = rs.getString("source_note");
                        ItemStack item = ItemSerializer.fromBase64(itemData);
                        if (item != null) {
                            list.add(new StoredItem(id, uuid, item, acquiredAt, sourceNote));
                        }
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to fetch stored items for " + playerUuid, e);
            }
            return list;
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Boolean> deleteStoredItem(long id, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM shop_chest WHERE id = ? AND player_uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                ps.setString(2, playerUuid.toString());
                int affected = ps.executeUpdate();
                return affected > 0;
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to delete stored item " + id, e);
                return false;
            }
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Integer> clearAllStoredItems(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM shop_chest WHERE player_uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                return ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to clear stored items for " + playerUuid, e);
                return 0;
            }
        }, ioExecutor);
    }
}
