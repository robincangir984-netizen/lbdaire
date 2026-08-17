package org.byauth.listener;

import org.byauth.ByCircleGame;
import org.byauth.controller.ArenaController;
import org.byauth.game.Arena;
import org.byauth.game.ArenaState;
import org.byauth.manager.GameManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class SpecialItemUseListener implements Listener {

    private final ByCircleGame plugin;
    private final ArenaController arenaController;

    public SpecialItemUseListener(ByCircleGame plugin) {
        this.plugin = plugin;
        this.arenaController = plugin.getArenaController();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Arena arena = arenaController.getArenaByPlayer(player);

        if (arena == null || arena.getState() != ArenaState.ACTIVE) {
            return;
        }

        GameManager gameManager = arena.getGameManager();
        Action action = event.getAction();

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            ItemStack itemInHand = player.getInventory().getItemInMainHand();

            if (itemInHand.getType() == Material.AIR || itemInHand.getType() == null) return;

            if (itemInHand.getType() == Material.SNOWBALL && itemInHand.hasItemMeta()) {
                ItemMeta meta = itemInHand.getItemMeta();
                if (meta.hasDisplayName() && ChatColor.stripColor(meta.getDisplayName()).equals("Beyazlatıcı Kartopu")) {
                    gameManager.getSnowballCooldown().put(player.getUniqueId(), System.currentTimeMillis());
                }
            } else if (itemInHand.getType() == Material.ENDER_PEARL && itemInHand.hasItemMeta()) {
                ItemMeta meta = itemInHand.getItemMeta();
                if (meta.hasDisplayName() && ChatColor.stripColor(meta.getDisplayName()).equals("Değiş Tokuş İncisi")) {
                    gameManager.getSnowballCooldown().put(player.getUniqueId(), System.currentTimeMillis());
                }
            }
        }
    }
}