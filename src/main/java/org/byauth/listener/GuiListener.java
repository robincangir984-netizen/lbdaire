package org.byauth.listener;

import org.byauth.ByCircleGame;
import org.byauth.controller.ArenaController;
import org.byauth.game.Arena;
import org.byauth.game.ArenaState;
import org.byauth.game.Team;
import org.byauth.utils.SettingsManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class GuiListener implements Listener {

    private final ArenaController arenaController;
    private final SettingsManager settings;

    public GuiListener(ByCircleGame plugin) {
        this.arenaController = plugin.getArenaController();
        this.settings = plugin.getSettingsManager();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(settings.getMessage("gui.team-select-title"))) {
            return;
        }

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        Arena arena = arenaController.getArenaByPlayer(player);

        if (arena == null || arena.getState() == ArenaState.ACTIVE || arena.getState() == ArenaState.STARTING) {
            player.closeInventory();
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        if (clickedItem.getType() == Material.BARRIER) {
            if (arena.getGameManager().getTeamOfPlayer(player) == null) {
                settings.sendMessage(player, settings.getMessage("errors.not-in-a-team"));
                player.closeInventory();
            } else {
                arenaController.unselectTeam(player);
                player.playSound(player.getLocation(), settings.PLAYER_LEAVE_SOUND, settings.PLAYER_LEAVE_VOLUME, settings.PLAYER_LEAVE_PITCH);
            }
            return;
        }

        for (Team team : Team.values()) {
            if (clickedItem.getType() == team.getConcreteMaterial()) {
                Team currentTeam = arena.getGameManager().getTeamOfPlayer(player);
                if (team.equals(currentTeam)) {
                    settings.sendMessage(player, settings.getMessage("errors.already-in-team"));
                    player.playSound(player.getLocation(), settings.TEAM_FULL_SOUND, settings.TEAM_FULL_VOLUME, settings.TEAM_FULL_PITCH);
                    player.closeInventory();
                    return;
                }

                if (arena.getGameManager().getPlayersInTeam(team).size() >= arena.getTeamSize()) {
                    settings.sendMessage(player, settings.getMessage("errors.team-full"));
                    player.playSound(player.getLocation(), settings.TEAM_FULL_SOUND, settings.TEAM_FULL_VOLUME, settings.TEAM_FULL_PITCH);
                } else {
                    arenaController.selectTeam(player, team);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                }
                break;
            }
        }
    }
}