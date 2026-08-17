package org.byauth.listener;

import org.byauth.ByCircleGame;
import org.byauth.controller.ArenaController;
import org.byauth.utils.SettingsManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class LobbyListener implements Listener {

    private final ByCircleGame plugin;
    private final ArenaController arenaController;
    private final SettingsManager settings;

    public LobbyListener(ByCircleGame plugin) {
        this.plugin = plugin;
        this.arenaController = plugin.getArenaController();
        this.settings = plugin.getSettingsManager();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (arenaController.getArenaByPlayer(player) == null) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return;
        }

        if (item.getType() == settings.TEAM_SELECTOR_MATERIAL && item.getItemMeta().getDisplayName().equals(settings.format(settings.TEAM_SELECTOR_NAME))) {
            arenaController.openTeamSelectionGUI(player);
            event.setCancelled(true);
        } else if (item.getType() == settings.LEAVE_LOBBY_MATERIAL && item.getItemMeta().getDisplayName().equals(settings.format(settings.LEAVE_LOBBY_NAME))) {
            arenaController.removePlayer(player);
            event.setCancelled(true);
        }
    }
}