package org.byauth.listener;

import org.byauth.ByCircleGame;
import org.byauth.controller.ArenaController;
import org.byauth.game.Arena;
import org.byauth.game.ArenaState;
import org.byauth.utils.SettingsManager;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class LobbyGuiListener implements Listener {

    private final ByCircleGame plugin;
    private final ArenaController arenaController;
    private final SettingsManager settings;

    public LobbyGuiListener(ByCircleGame plugin) {
        this.plugin = plugin;
        this.arenaController = plugin.getArenaController();
        this.settings = plugin.getSettingsManager();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(settings.getMessage("gui.lobby-title"))) {
            return;
        }

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || !clickedItem.hasItemMeta() || clickedItem.getItemMeta().getDisplayName().equals(" ")) {
            return;
        }

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;

        NamespacedKey key = new NamespacedKey(plugin, "arena_name");
        if (!meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
            return;
        }

        String arenaName = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        Arena clickedArena = arenaController.getArenas().get(arenaName);

        if (clickedArena == null) {
            settings.sendMessage(player, settings.getMessage("errors.arena-not-found"));
            player.closeInventory();
            return;
        }

        if (clickedArena.getLobbyLocation() == null || clickedArena.getCenterLocation() == null || clickedArena.getCenterLocation().getWorld() == null) {
            settings.sendMessage(player, settings.getMessage("errors.arena-not-ready"));
            player.closeInventory();
            return;
        }

        ArenaState state = clickedArena.getState();

        if (state == ArenaState.WAITING || state == ArenaState.COUNTDOWN) {
            arenaController.addPlayer(player, clickedArena.getName());
        } else if (state == ArenaState.ACTIVE || state == ArenaState.STARTING || state == ArenaState.ENDING) {
            arenaController.spectateArena(player, clickedArena.getName());
        } else if (state == ArenaState.RESETTING) {
            settings.sendMessage(player, settings.getMessage("errors.arena-resetting"));
        }

        player.closeInventory();
    }
}