package org.byauth.listener;

import org.byauth.ByCircleGame;
import org.byauth.controller.ArenaController;
import org.byauth.game.Arena;
import org.byauth.game.ArenaState;
import org.byauth.game.Team;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class LobbyProtectionListener implements Listener {

    private final ByCircleGame plugin;
    private final ArenaController arenaController;

    public LobbyProtectionListener(ByCircleGame plugin) {
        this.plugin = plugin;
        this.arenaController = plugin.getArenaController();
    }

    private boolean isInLobby(Player player) {
        Arena arena = arenaController.getArenaByPlayer(player);
        return arena != null && (arena.getState() == ArenaState.WAITING || arena.getState() == ArenaState.COUNTDOWN);
    }

    private boolean isNearMainSpawn(Location location) {
        Location mainSpawn = plugin.getSettingsManager().getMainSpawnLocation();
        if (mainSpawn == null) {
            return false;
        }
        if (location.getWorld() == null || !location.getWorld().equals(mainSpawn.getWorld())) {
            return false;
        }
        double radius = plugin.getSettingsManager().MAIN_SPAWN_PROTECTION_RADIUS;
        return location.distanceSquared(mainSpawn) <= radius * radius;
    }

    private boolean isPlayerInActiveGame(Player player) {
        Arena arena = arenaController.getArenaByPlayer(player);
        return arena != null && arena.getState() == ArenaState.ACTIVE;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();

        if (event.getFrom().getBlockX() == to.getBlockX() && event.getFrom().getBlockY() == to.getBlockY() && event.getFrom().getBlockZ() == to.getBlockZ()) {
            return;
        }

        if (isInLobby(player)) {
            Arena arena = arenaController.getArenaByPlayer(player);
            Location lobbyLoc = arena.getLobbyLocation();

            if (player.getLocation().getY() < lobbyLoc.getY() - 5) {
                player.teleport(lobbyLoc.clone().add(0.5, 2, 0.5));
                return;
            }

            Team playerTeam = arena.getGameManager().getTeamOfPlayer(player);
            if (playerTeam != null) {
                Location teamPlatform = arenaController.getTeamPlatformLocation(arena, playerTeam);
                if (player.getLocation().distanceSquared(teamPlatform) > 25) {
                    event.setCancelled(true);
                }
            }
        } else if (isNearMainSpawn(player.getLocation())) {
            Location mainSpawn = plugin.getSettingsManager().getMainSpawnLocation();
            if (mainSpawn != null && player.getLocation().getY() < mainSpawn.getY() - 10) {
                player.teleport(mainSpawn);
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();

            if (isPlayerInActiveGame(player)) return;

            if (isInLobby(player) || isNearMainSpawn(player.getLocation())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();

            if (isPlayerInActiveGame(victim)) return;

            if (isInLobby(victim) || isNearMainSpawn(victim.getLocation())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (isPlayerInActiveGame(player)) return;

        if (isInLobby(player) || (isNearMainSpawn(player.getLocation()) && !player.hasPermission("bycirclegame.admin"))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        if (isPlayerInActiveGame(player)) return;

        if (isInLobby(player) || (isNearMainSpawn(player.getLocation()) && !player.hasPermission("bycirclegame.admin"))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        if (isPlayerInActiveGame(player)) return;

        if (isInLobby(player) || (isNearMainSpawn(player.getLocation()) && !player.hasPermission("bycirclegame.admin"))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();

            if (isPlayerInActiveGame(player)) return;

            if (isInLobby(player) || (isNearMainSpawn(player.getLocation()) && !player.hasPermission("bycirclegame.admin"))) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();

            if (isPlayerInActiveGame(player)) return;

            if (isInLobby(player)) {
                if (event.getView().getTitle().equals(arenaController.getSettingsManager().getMessage("gui.team-select-title")) || event.getView().getTitle().equals(arenaController.getSettingsManager().getMessage("gui.lobby-title"))) {
                    return;
                }
                event.setCancelled(true);
            } else if (isNearMainSpawn(player.getLocation()) && !player.hasPermission("bycirclegame.admin")) {
                event.setCancelled(true);
            }
        }
    }
}