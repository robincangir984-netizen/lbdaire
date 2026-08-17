package org.byauth.manager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.byauth.ByCircleGame;
import org.byauth.data.PlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerDataManager {

    private final ByCircleGame plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, PlayerStats> statsCache = new HashMap<>();
    private final Gson gson = new Gson();
    private final Type stringListType = new TypeToken<ArrayList<String>>() {}.getType();

    public PlayerDataManager(ByCircleGame plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void loadPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        if (statsCache.containsKey(uuid)) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            databaseManager.lock();
            try {
                String sql = "SELECT * FROM player_stats WHERE uuid = ?;";
                try (Connection conn = databaseManager.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(sql)) {

                    pstmt.setString(1, uuid.toString());
                    ResultSet rs = pstmt.executeQuery();

                    if (rs.next()) {
                        String ownedCosmeticsJson = rs.getString("owned_cosmetics");
                        List<String> ownedCosmetics = gson.fromJson(ownedCosmeticsJson, stringListType);

                        statsCache.put(uuid, new PlayerStats(
                                rs.getInt("points"),
                                rs.getInt("bcoin"),
                                rs.getInt("kills"),
                                rs.getInt("deaths"),
                                rs.getInt("wins"),
                                rs.getInt("losses"),
                                ownedCosmetics,
                                rs.getString("selected_cosmetic"),
                                rs.getString("selected_kill_effect"),
                                rs.getString("selected_arrow_effect")
                        ));
                    } else {
                        statsCache.put(uuid, new PlayerStats(0, 0, 0, 0, 0, 0, new ArrayList<>(), null, null, null));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Oyuncu verisi yüklenirken SQL hatası: " + uuid.toString());
                e.printStackTrace();
                statsCache.put(uuid, new PlayerStats(0, 0, 0, 0, 0, 0, new ArrayList<>(), null, null, null));
            } finally {
                databaseManager.unlock();
            }
        });
    }

    public void savePlayerData(Player player, boolean removeFromCache) {
        UUID uuid = player.getUniqueId();
        PlayerStats stats = statsCache.get(uuid);
        if (stats == null) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            databaseManager.lock();
            try {
                String sql = "INSERT OR REPLACE INTO player_stats(uuid, name, points, bcoin, kills, deaths, wins, losses, owned_cosmetics, selected_cosmetic, selected_kill_effect, selected_arrow_effect) VALUES(?,?,?,?,?,?,?,?,?,?,?,?);";
                try (Connection conn = databaseManager.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(sql)) {

                    pstmt.setString(1, uuid.toString());
                    pstmt.setString(2, player.getName());
                    pstmt.setInt(3, stats.getPoints());
                    pstmt.setInt(4, stats.getBCoin());
                    pstmt.setInt(5, stats.getKills());
                    pstmt.setInt(6, stats.getDeaths());
                    pstmt.setInt(7, stats.getWins());
                    pstmt.setInt(8, stats.getLosses());
                    pstmt.setString(9, gson.toJson(stats.getOwnedCosmetics()));
                    pstmt.setString(10, stats.getSelectedCosmetic());
                    pstmt.setString(11, stats.getSelectedKillEffect());
                    pstmt.setString(12, stats.getSelectedArrowEffect());
                    pstmt.executeUpdate();

                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Oyuncu verisi kaydedilirken SQL hatası: " + uuid.toString());
                e.printStackTrace();
            } finally {
                databaseManager.unlock();
            }

            if (removeFromCache) {
                statsCache.remove(uuid);
            }
        });
    }

    public PlayerStats getStats(UUID uuid) {
        return statsCache.get(uuid);
    }

    private void saveOnChange(Player player) {
        if (player != null && player.isOnline()) {
            savePlayerData(player, false);
        }
    }

    public void addPoints(Player player, int amount) {
        PlayerStats stats = getStats(player.getUniqueId());
        if (stats != null) {
            stats.addPoints(amount);
            saveOnChange(player);
        }
    }

    public void setPoints(Player player, int amount) {
        PlayerStats stats = getStats(player.getUniqueId());
        if (stats != null) {
            stats.setPoints(amount);
            saveOnChange(player);
        }
    }

    public void addBCoin(Player player, int amount) {
        PlayerStats stats = getStats(player.getUniqueId());
        if (stats != null) {
            stats.addBCoin(amount);
            saveOnChange(player);
        }
    }

    public void setBCoin(Player player, int amount) {
        PlayerStats stats = getStats(player.getUniqueId());
        if (stats != null) {
            stats.setBCoin(amount);
            saveOnChange(player);
        }
    }

    public void processKillStats(Player player, int points, int coins) {
        PlayerStats stats = getStats(player.getUniqueId());
        if (stats != null) {
            stats.incrementKills();
            stats.addPoints(points);
            stats.addBCoin(coins);
            saveOnChange(player);
        }
    }

    public void incrementDeaths(Player player) {
        PlayerStats stats = getStats(player.getUniqueId());
        if (stats != null) {
            stats.incrementDeaths();
            saveOnChange(player);
        }
    }

    public void processWinStats(Player player, int points, int coins) {
        PlayerStats stats = getStats(player.getUniqueId());
        if (stats != null) {
            stats.incrementWins();
            stats.addPoints(points);
            stats.addBCoin(coins);
            saveOnChange(player);
        }
    }

    public void processRoundSurvivedStats(Player player, int points, int coins) {
        PlayerStats stats = getStats(player.getUniqueId());
        if (stats != null) {
            stats.addPoints(points);
            stats.addBCoin(coins);
            saveOnChange(player);
        }
    }

    public void incrementLosses(Player player) {
        PlayerStats stats = getStats(player.getUniqueId());
        if (stats != null) {
            stats.incrementLosses();
            saveOnChange(player);
        }
    }
}