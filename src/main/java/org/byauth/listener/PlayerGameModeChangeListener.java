package org.byauth.listener;

import org.byauth.ByCircleGame;
import org.byauth.controller.ArenaController;
import org.byauth.game.Arena;
import org.byauth.game.ArenaState;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

public class PlayerGameModeChangeListener implements Listener {

    private final ByCircleGame plugin;
    private final ArenaController arenaController;

    public PlayerGameModeChangeListener(ByCircleGame plugin) {
        this.plugin = plugin;
        this.arenaController = plugin.getArenaController();
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        Arena arena = arenaController.getArenaByPlayer(player);

        boolean isSpectatingAny = false;
        if (arena == null) {
            for (Arena a : arenaController.getArenas().values()) {
                if (a.getSpectators().contains(player.getUniqueId())) {
                    isSpectatingAny = true;
                    arena = a;
                    break;
                }
            }
            if (!isSpectatingAny) {
                return;
            }
        }

        ArenaState state = arena.getState();

        if (state == ArenaState.STARTING || state == ArenaState.ACTIVE || state == ArenaState.ENDING) {
            boolean isPlayerSpectator = arena.getSpectators().contains(player.getUniqueId());

            if (isPlayerSpectator) {
                if (event.getNewGameMode() != GameMode.SPECTATOR) {
                    player.sendMessage(plugin.getSettingsManager().PREFIX + plugin.getSettingsManager().getMessage("errors.cannot-change-gamemode-spectator"));
                    event.setCancelled(true);
                }
            } else if (event.getNewGameMode() != GameMode.SURVIVAL) {
                if (player.getGameMode() == GameMode.SURVIVAL) {
                    player.sendMessage(plugin.getSettingsManager().PREFIX + plugin.getSettingsManager().getMessage("errors.cannot-change-gamemode-player"));
                    event.setCancelled(true);
                }
            }
        }
    }
}