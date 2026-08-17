package org.byauth.listener;

import org.byauth.ByCircleGame;
import org.byauth.controller.ArenaController;
import org.byauth.game.Arena;
import org.byauth.game.ArenaState;
import org.byauth.manager.GameManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class WorldProtectionListener implements Listener {

    private final ByCircleGame plugin;
    private final ArenaController arenaController;

    public WorldProtectionListener(ByCircleGame plugin) {
        this.plugin = plugin;
        this.arenaController = plugin.getArenaController();
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }

        World spawnWorld = event.getLocation().getWorld();
        if (spawnWorld == null) return;

        Set<World> managedWorlds = new HashSet<>();
        if (plugin.getSettingsManager().getMainSpawnLocation() != null) {
            managedWorlds.add(plugin.getSettingsManager().getMainSpawnLocation().getWorld());
        }
        for (Arena arena : arenaController.getArenas().values()) {
            if (arena.getLobbyLocation() != null && arena.getLobbyLocation().getWorld() != null) {
                managedWorlds.add(arena.getLobbyLocation().getWorld());
            }
            if (arena.getCenterLocation() != null && arena.getCenterLocation().getWorld() != null) {
                managedWorlds.add(arena.getCenterLocation().getWorld());
            }
        }

        if (managedWorlds.contains(spawnWorld)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getEntity().hasMetadata("custom_tnt")) {
            event.blockList().clear();
            return;
        }

        Location eventLoc = event.getLocation();
        boolean isInLobby = false;

        Location mainSpawn = plugin.getSettingsManager().getMainSpawnLocation();
        if (mainSpawn != null && mainSpawn.getWorld().equals(eventLoc.getWorld())) {
            isInLobby = true;
        }

        if (!isInLobby) {
            for (Arena arena : arenaController.getArenas().values()) {
                if (arena.getLobbyLocation() != null && arena.getLobbyLocation().getWorld().equals(eventLoc.getWorld())) {
                    if (arena.getState() == ArenaState.WAITING || arena.getState() == ArenaState.COUNTDOWN) {
                        isInLobby = true;
                        break;
                    }
                }
            }
        }

        if (isInLobby) {
            event.blockList().clear();
            return;
        }

        Arena arena = null;
        for (Arena a : plugin.getArenaController().getArenas().values()) {
            if (a.getState() == ArenaState.ACTIVE && a.getArenaManager().getCenter() != null) {
                Location centerLoc = a.getArenaManager().getCenter();
                if (centerLoc.getWorld().equals(eventLoc.getWorld())) {
                    double dx = eventLoc.getX() - centerLoc.getX();
                    double dz = eventLoc.getZ() - centerLoc.getZ();
                    double distSq2D = (dx * dx) + (dz * dz);
                    if (distSq2D < Math.pow(plugin.getSettingsManager().ARENA_RADIUS + 10, 2)) {
                        arena = a;
                        break;
                    }
                }
            }
        }

        if (arena == null) {
            return;
        }

        GameManager gameManager = arena.getGameManager();
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (!gameManager.getPlayerPlacedBlocks().containsKey(block.getLocation())) {
                iterator.remove();
            }
        }
    }
}