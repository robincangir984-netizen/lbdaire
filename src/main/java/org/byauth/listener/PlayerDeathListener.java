package org.byauth.listener;

import org.byauth.ByCircleGame;
import org.byauth.controller.ArenaController;
import org.byauth.data.PlayerStats;
import org.byauth.game.Arena;
import org.byauth.game.EffectPlayer;
import org.byauth.manager.GameManager;
import org.byauth.manager.PlayerDataManager;
import org.byauth.utils.SettingsManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerDeathListener implements Listener {

    private final ByCircleGame plugin;
    private final ArenaController arenaController;
    private final PlayerDataManager playerDataManager;
    private final SettingsManager settings;

    public PlayerDeathListener(ByCircleGame plugin) {
        this.plugin = plugin;
        this.arenaController = plugin.getArenaController();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.settings = plugin.getSettingsManager();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Arena arena = arenaController.getArenaByPlayer(player);

        if (arena == null) {
            return;
        }

        GameManager gameManager = arena.getGameManager();
        event.setDeathMessage(null);

        Player killer = player.getKiller();
        if (killer != null && killer != player) {
            PlayerStats killerStats = playerDataManager.getStats(killer.getUniqueId());
            if (killerStats != null && killerStats.getSelectedKillEffect() != null) {
                EffectPlayer.playKillEffect(player.getLocation(), killerStats.getSelectedKillEffect());
            }

            playerDataManager.processKillStats(killer, settings.POINTS_ON_KILL, settings.COINS_ON_KILL);
            String killMessage = settings.getMessage("game.elimination-by-player-broadcast")
                    .replace("%player%", player.getName())
                    .replace("%killer%", killer.getName());
            arenaController.broadcastArenaMessage(arena, killMessage);

            if (settings.POINTS_ENABLED && settings.POINTS_ON_KILL > 0) {
                String message = settings.getMessage("points.on-kill").replace("%points%", String.valueOf(settings.POINTS_ON_KILL));
                killer.sendMessage(settings.PREFIX + message);
            }
            if (settings.COIN_SYSTEM_ENABLED && settings.COINS_ON_KILL > 0) {
                String message = settings.getMessage("coin.on-kill")
                        .replace("%coins%", String.valueOf(settings.COINS_ON_KILL))
                        .replace("%coin_name%", settings.COIN_SYSTEM_NAME);
                killer.sendMessage(settings.PREFIX + message);
            }

        } else {
            String eliminationMessage = settings.getMessage("game.elimination-broadcast").replace("%player%", player.getName());
            arenaController.broadcastArenaMessage(arena, eliminationMessage);
        }

        playerDataManager.incrementDeaths(player);
        arenaController.addSpectator(player, arena);

        new BukkitRunnable() {
            @Override
            public void run() {
                player.spigot().respawn();
                player.setGameMode(GameMode.SPECTATOR);
                Location center = arena.getArenaManager().getCenter();
                if (center != null) {
                    player.teleport(center.clone().add(0, 15, 0));
                }
            }
        }.runTaskLater(plugin, 1L);

        gameManager.removePlayerFromGame(player);
    }
}