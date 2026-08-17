package org.byauth.manager;

import org.byauth.ByCircleGame;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.locks.ReentrantLock;

public class DatabaseManager {

    private final ByCircleGame plugin;
    private Connection connection;
    private final String dbUrl;
    private final ReentrantLock lock = new ReentrantLock();

    public DatabaseManager(ByCircleGame plugin) {
        this.plugin = plugin;
        File dbFile = new File(plugin.getDataFolder(), "playerdata.db");
        if (!dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }
        this.dbUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
    }

    public void connect() {
        try {
            if (connection != null && !connection.isClosed()) {
                return;
            }
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(dbUrl);
            plugin.getLogger().info("SQLite veritabanı bağlantısı başarılı.");
            createTable();
        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().severe("SQLite veritabanı bağlantısı kurulamadı!");
            e.printStackTrace();
        }
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS player_stats (" +
                "uuid TEXT PRIMARY KEY," +
                "name TEXT NOT NULL," +
                "points INTEGER DEFAULT 0," +
                "bcoin INTEGER DEFAULT 0," +
                "kills INTEGER DEFAULT 0," +
                "deaths INTEGER DEFAULT 0," +
                "wins INTEGER DEFAULT 0," +
                "losses INTEGER DEFAULT 0," +
                "owned_cosmetics TEXT," +
                "selected_cosmetic TEXT," +
                "selected_kill_effect TEXT," +
                "selected_arrow_effect TEXT" +
                ");";
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(sql);
            updateTable();
        } catch (SQLException e) {
            plugin.getLogger().severe("player_stats tablosu oluşturulamadı!");
            e.printStackTrace();
        }
    }

    private void updateTable() {
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute("ALTER TABLE player_stats ADD COLUMN bcoin INTEGER DEFAULT 0;");
        } catch (SQLException e) {}
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute("ALTER TABLE player_stats ADD COLUMN owned_cosmetics TEXT;");
        } catch (SQLException e) {}
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute("ALTER TABLE player_stats ADD COLUMN selected_cosmetic TEXT;");
        } catch (SQLException e) {}
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute("ALTER TABLE player_stats ADD COLUMN selected_kill_effect TEXT;");
        } catch (SQLException e) {}
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute("ALTER TABLE player_stats ADD COLUMN selected_arrow_effect TEXT;");
        } catch (SQLException e) {}
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Veritabanı bağlantısı kontrol edilirken bir hata oluştu.");
            e.printStackTrace();
        }
        return connection;
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    public void closeConnection() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    plugin.getLogger().info("SQLite veritabanı bağlantısı kapatıldı.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}