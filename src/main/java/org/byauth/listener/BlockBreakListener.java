package org.byauth.listener;

import org.byauth.ByCircleGame;
import org.byauth.game.Arena;
import org.byauth.game.ArenaState;
import org.byauth.manager.GameManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class BlockBreakListener implements Listener {

    private final ByCircleGame plugin;

    public BlockBreakListener(ByCircleGame plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Arena arena = plugin.getArenaController().getArenaByPlayer(player);

        if (arena == null) {
            if (plugin.getSettingsManager().getMainSpawnLocation() != null && player.getWorld().equals(plugin.getSettingsManager().getMainSpawnLocation().getWorld())) {
                if (!player.hasPermission("bycirclegame.admin")) {
                    event.setCancelled(true);
                }
            } else if (plugin.getArenaController().getPrimaryLobbyLocation() != null && player.getWorld().equals(plugin.getArenaController().getPrimaryLobbyLocation().getWorld())) {
                if (!player.hasPermission("bycirclegame.admin")) {
                    event.setCancelled(true);
                }
            }
            return;
        }

        ArenaState state = arena.getState();
        if (state != ArenaState.ACTIVE && state != ArenaState.STARTING) {
            event.setCancelled(true);
            return;
        }

        if (arena.getSpectators().contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (event.getBlock().getType() == Material.SHULKER_BOX) {
            event.setCancelled(true);
            return;
        }

        GameManager gameManager = arena.getGameManager();
        Location blockLocation = event.getBlock().getLocation();

        if (gameManager.getActiveAoETraps().containsKey(blockLocation) || gameManager.getActiveGravityBombs().containsKey(blockLocation)) {
            event.setCancelled(true);
            return;
        }

        if (!gameManager.getPlayerPlacedBlocks().containsKey(blockLocation)) {
            event.setCancelled(true);
            return;
        }

        gameManager.getPlayerPlacedBlocks().remove(blockLocation);
    }
}