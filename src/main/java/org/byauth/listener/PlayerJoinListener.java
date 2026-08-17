package org.byauth.listener;

import org.byauth.ByCircleGame;
import org.byauth.controller.ArenaController;
import org.byauth.game.Arena;
import org.byauth.utils.SettingsManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerJoinListener implements Listener {

    private final ByCircleGame plugin;
    private final ArenaController arenaController;
    private final SettingsManager settings;

    public PlayerJoinListener(ByCircleGame plugin) {
        this.plugin = plugin;
        this.arenaController = plugin.getArenaController();
        this.settings = plugin.getSettingsManager();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getPlayerDataManager().loadPlayerData(player);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getRankManager() != null) {
                plugin.getRankManager().checkAndPromote(player);
            }
        }, 20L);

        Arena arena = arenaController.getArenaByPlayer(player);
        if (arena == null) {
            for (Arena a : arenaController.getArenas().values()) {
                Location center = a.getCenterLocation();
                if (center != null && center.getWorld().equals(player.getWorld())) {
                    double distance = player.getLocation().distance(center);
                    if (distance < settings.ARENA_RADIUS + 10) {
                        player.getInventory().clear();
                        player.sendMessage(settings.PREFIX + settings.getMessage("errors.game-interrupted"));

                        Location mainSpawn = settings.getMainSpawnLocation();
                        if (mainSpawn != null) {
                            player.teleport(mainSpawn);
                        } else {
                            World defaultWorld = Bukkit.getServer().getWorlds().get(0);
                            if (defaultWorld != null) {
                                player.teleport(defaultWorld.getSpawnLocation());
                            }
                        }
                        return;
                    }
                }
            }

            ItemStack teamSelector = player.getInventory().getItem(settings.TEAM_SELECTOR_SLOT);
            if (teamSelector != null && teamSelector.getType() == settings.TEAM_SELECTOR_MATERIAL) {
                player.getInventory().clear(settings.TEAM_SELECTOR_SLOT);
            }

            ItemStack leaveItem = player.getInventory().getItem(settings.LEAVE_LOBBY_SLOT);
            if (leaveItem != null && leaveItem.getType() == settings.LEAVE_LOBBY_MATERIAL) {
                player.getInventory().clear(settings.LEAVE_LOBBY_SLOT);
            }
        }
        arenaController.updatePlayerVisibility(player);
    }
}