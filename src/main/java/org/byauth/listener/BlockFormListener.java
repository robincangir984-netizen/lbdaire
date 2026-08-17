package org.byauth.listener;

import org.byauth.ByCircleGame;
import org.byauth.controller.ArenaController;
import org.byauth.game.Arena;
import org.byauth.utils.SettingsManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;

public class BlockFormListener implements Listener {

    private final ByCircleGame plugin;
    private final ArenaController arenaController;
    private final SettingsManager settings;

    public BlockFormListener(ByCircleGame plugin) {
        this.plugin = plugin;
        this.arenaController = plugin.getArenaController();
        this.settings = plugin.getSettingsManager();
    }

    @EventHandler
    public void onBlockForm(BlockFromToEvent event) {
        Block toBlock = event.getToBlock();
        boolean inAnyArenaWorld = arenaController.getArenas().values().stream()
                .anyMatch(arena -> arena.getCenterLocation() != null && arena.getCenterLocation().getWorld().equals(toBlock.getWorld()));

        if (!inAnyArenaWorld) return;

        if (toBlock.getType() == Material.COBWEB) {
            event.setCancelled(true);
            return;
        }

        int lavaY = settings.LAVA_Y_LEVEL;
        if (toBlock.getY() <= lavaY) {
            if (event.getBlock().isLiquid()) {
                event.setCancelled(true);
            }
        }
    }
}