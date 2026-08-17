package org.byauth.listener;

import org.byauth.ByCircleGame;
import org.byauth.controller.ArenaController;
import org.byauth.game.Arena;
import org.byauth.game.ArenaState;
import org.byauth.game.Team;
import org.byauth.manager.GameManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerMoveListener implements Listener {

    private final ByCircleGame plugin;
    private final ArenaController arenaController;

    public PlayerMoveListener(ByCircleGame plugin) {
        this.plugin = plugin;
        this.arenaController = plugin.getArenaController();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Arena arena = arenaController.getArenaByPlayer(player);

        if (arena == null) {
            return;
        }

        GameManager gameManager = arena.getGameManager();
        Location to = event.getTo();

        if (event.getFrom().getBlockX() == to.getBlockX() && event.getFrom().getBlockY() == to.getBlockY() && event.getFrom().getBlockZ() == to.getBlockZ()) {
            return;
        }

        if (arena.getState() == ArenaState.ENDING && gameManager.getInvinciblePlayers().contains(player.getUniqueId())) {
            if (to.getY() < arena.getArenaManager().getLavaY()) {
                player.teleport(arena.getArenaManager().getCenter().clone().add(0.5, 2, 0.5));
                return;
            }
        }

        if (arena.getState() != ArenaState.ACTIVE) {
            return;
        }

        if (arena.getSpectators().contains(player.getUniqueId())) {
            return;
        }

        if (to.getBlockY() <= arena.getArenaManager().getLavaY() + 0.5) {
            player.setHealth(0.0);
            return;
        }

        if (gameManager.isSuddenDeath()) {
            return;
        }

        Location floorBlockLocation = new Location(to.getWorld(), to.getBlockX(), arena.getArenaManager().getArenaY() - 1, to.getBlockZ());
        Material floorBlockType = floorBlockLocation.getBlock().getType();

        if (floorBlockType == Material.WHITE_CONCRETE || floorBlockType == Material.BEDROCK || floorBlockType == Material.COBWEB) {
            return;
        }

        Team playerTeam = gameManager.getTeamOfPlayer(player);
        if (playerTeam == null) {
            return;
        }

        Team locationTeam = arena.getArenaManager().getTeamFromLocation(to);
        if (locationTeam != null && locationTeam != playerTeam) {
            player.setHealth(0.0);
        }
    }
}