package org.byauth.listener;

import org.byauth.ByCircleGame;
import org.byauth.controller.ArenaController;
import org.byauth.game.Arena;
import org.byauth.game.ArenaState;
import org.byauth.game.Team;
import org.byauth.manager.GameManager;
import org.byauth.utils.SettingsManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.Arrays;
import java.util.List;

public class BlockPlaceListener implements Listener {

    private final ByCircleGame plugin;
    private final ArenaController arenaController;
    private final SettingsManager settings;
    private final List<Material> specialCandles = Arrays.asList(
            Material.LIME_CANDLE,
            Material.RED_CANDLE,
            Material.PINK_CANDLE
    );

    public BlockPlaceListener(ByCircleGame plugin) {
        this.plugin = plugin;
        this.arenaController = plugin.getArenaController();
        this.settings = plugin.getSettingsManager();
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Arena arena = arenaController.getArenaByPlayer(player);

        if (arena == null) {
            if (plugin.getSettingsManager().getMainSpawnLocation() != null && player.getWorld().equals(plugin.getSettingsManager().getMainSpawnLocation().getWorld())) {
                if (!player.hasPermission("bycirclegame.admin")) {
                    event.setCancelled(true);
                }
            }
            return;
        }

        GameManager gameManager = arena.getGameManager();
        if (gameManager.isPlayerWaiting(player)) {
            event.setCancelled(true);
            return;
        }

        if (arena.getState() == ArenaState.STARTING) {
            event.setCancelled(true);
            return;
        }

        if (arena.getState() != ArenaState.ACTIVE) {
            return;
        }

        ItemStack itemInHand = event.getItemInHand();

        if (event.getBlockPlaced().getType() == Material.TNT) {
            event.setCancelled(true);
            itemInHand.setAmount(itemInHand.getAmount() - 1);

            Location loc = event.getBlockPlaced().getLocation().add(0.5, 0.5, 0.5);
            World world = loc.getWorld();
            TNTPrimed tnt = world.spawn(loc, TNTPrimed.class);
            tnt.setFuseTicks(settings.INSTANT_TNT_FUSE_TICKS);
            tnt.setSource(player);
            tnt.setMetadata("custom_tnt", new FixedMetadataValue(plugin, true));
            return;
        }

        Location placedLoc = event.getBlock().getLocation();
        if (placedLoc.getBlockY() >= arena.getArenaManager().getArenaY()) {
            for (int y = placedLoc.getBlockY() - 1; y >= arena.getArenaManager().getLavaY(); y--) {
                Block blockBelow = placedLoc.getWorld().getBlockAt(placedLoc.getBlockX(), y, placedLoc.getBlockZ());
                Material type = blockBelow.getType();

                if (type == Material.COBWEB || type == Material.SHULKER_BOX) {
                    event.setCancelled(true);
                    settings.sendMessage(player, settings.getMessage("errors.cannot-place-on-border"));
                    return;
                }
                if (type != Material.AIR && !blockBelow.isLiquid()) {
                    break;
                }
            }
        }

        Location blockLocation = event.getBlock().getLocation();
        Team placerTeam = gameManager.getTeamOfPlayer(player);

        if (placerTeam == null) return;

        if (specialCandles.contains(itemInHand.getType())) {
            event.setCancelled(true);
            gameManager.getSpecialItemManager().handleCandlePlace(player, placerTeam, itemInHand.getType());
        }
        else if (itemInHand.getType() == Material.TERRACOTTA && itemInHand.hasItemMeta() && itemInHand.getItemMeta().getDisplayName().contains("Tuzaklı Kil")) {
            event.setCancelled(true);
            settings.sendMessage(player, settings.getMessage("errors.special-item-disabled"));
        } else if (itemInHand.getType() == Material.MOSSY_COBBLESTONE && itemInHand.hasItemMeta() && itemInHand.getItemMeta().getDisplayName().contains("Hain Yosun")) {
            event.setCancelled(true);
            settings.sendMessage(player, settings.getMessage("errors.special-item-disabled"));
        } else if (itemInHand.getType() == Material.OBSIDIAN && itemInHand.hasItemMeta() && itemInHand.getItemMeta().getDisplayName().contains("Çekim Bombası")) {
            event.setCancelled(true);
            settings.sendMessage(player, settings.getMessage("errors.special-item-disabled"));
        }
        else {
            gameManager.getPlayerPlacedBlocks().put(blockLocation, placerTeam);
        }
    }
}