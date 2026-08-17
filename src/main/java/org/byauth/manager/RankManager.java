package org.byauth.manager;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.byauth.ByCircleGame;
import org.byauth.data.PlayerStats;
import org.byauth.utils.SettingsManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class RankManager {

    private final ByCircleGame plugin;
    private final SettingsManager settings;
    private final PlayerDataManager playerDataManager;
    private final LuckPerms luckPerms;

    private File ranksFile;
    private FileConfiguration ranksConfig;
    private boolean enabled;
    private final List<Rank> ranks = new ArrayList<>();

    private static class Rank {
        private final int points;
        private final String name;
        private final List<String> commands;
        private final String group;

        Rank(int points, String name, List<String> commands) {
            this.points = points;
            this.name = name;
            this.commands = commands;
            this.group = findGroup(commands);
        }

        private String findGroup(List<String> commands) {
            for (String cmd : commands) {
                String lowerCmd = cmd.toLowerCase();
                if (lowerCmd.contains("parent set ")) {
                    try {
                        return lowerCmd.split("parent set ")[1].split(" ")[0];
                    } catch (Exception ignored) {}
                }
            }
            return null;
        }
    }

    public RankManager(ByCircleGame plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.settings = plugin.getSettingsManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.luckPerms = luckPerms;
        reload();
    }

    public void reload() {
        ranksFile = new File(plugin.getDataFolder(), "ranks.yml");
        if (!ranksFile.exists()) {
            plugin.saveResource("ranks.yml", false);
        }
        ranksConfig = YamlConfiguration.loadConfiguration(ranksFile);
        this.enabled = ranksConfig.getBoolean("enabled", false);

        ranks.clear();
        List<Map<?, ?>> rankMaps = ranksConfig.getMapList("ranks");
        for (Map<?, ?> map : rankMaps) {
            if (map.containsKey("points") && map.containsKey("name") && map.containsKey("commands")) {
                int points = (int) map.get("points");
                String name = (String) map.get("name");
                List<String> commands = (List<String>) map.get("commands");
                ranks.add(new Rank(points, name, commands));
            }
        }
        ranks.sort(Comparator.comparingInt(r -> r.points));
    }

    public void checkAndPromote(Player player) {
        if (!enabled || plugin.getArenaController().getArenaByPlayer(player) != null) {
            return;
        }

        PlayerStats stats = playerDataManager.getStats(player.getUniqueId());
        if (stats == null) {
            return;
        }

        int playerPoints = stats.getPoints();
        Rank targetRank = null;
        for (Rank rank : ranks) {
            if (playerPoints >= rank.points) {
                targetRank = rank;
            } else {
                break;
            }
        }

        if (targetRank == null || targetRank.group == null) {
            return;
        }

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return;
        }

        if (user.getPrimaryGroup().equalsIgnoreCase(targetRank.group)) {
            return;
        }

        final Rank finalTargetRank = targetRank;

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String command : finalTargetRank.commands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
            }
        });

        String message = settings.getMessage("rank.rank-up-broadcast")
                .replace("%player%", player.getName())
                .replace("%rank%", settings.format(finalTargetRank.name));

        Bukkit.broadcastMessage(settings.PREFIX + message);
    }
}