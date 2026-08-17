package org.byauth.listener;

import org.byauth.ByCircleGame;
import org.byauth.data.PlayerStats;
import org.byauth.data.VictoryEffect;
import org.byauth.manager.CosmeticManager;
import org.byauth.manager.PlayerDataManager;
import org.byauth.utils.SettingsManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class CosmeticGuiListener implements Listener {

    private final ByCircleGame plugin;
    private final CosmeticManager cosmeticManager;
    private final PlayerDataManager playerDataManager;
    private final SettingsManager settingsManager;

    public CosmeticGuiListener(ByCircleGame plugin) {
        this.plugin = plugin;
        this.cosmeticManager = plugin.getCosmeticManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.settingsManager = plugin.getSettingsManager();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals(cosmeticManager.mainMenuTitle) && !title.equals(cosmeticManager.victoryGuiTitle) &&
                !title.equals(cosmeticManager.killGuiTitle) && !title.equals(cosmeticManager.arrowGuiTitle)) {
            return;
        }

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR || !clickedItem.hasItemMeta()) {
            return;
        }

        ItemMeta meta = clickedItem.getItemMeta();
        NamespacedKey categoryKey = new NamespacedKey(plugin, "cosmetic_category");
        NamespacedKey idKey = new NamespacedKey(plugin, "cosmetic_id");
        NamespacedKey actionKey = new NamespacedKey(plugin, "cosmetic_action");

        if (title.equals(cosmeticManager.mainMenuTitle)) {
            if (meta.getPersistentDataContainer().has(categoryKey, PersistentDataType.STRING)) {
                String category = meta.getPersistentDataContainer().get(categoryKey, PersistentDataType.STRING);
                cosmeticManager.openCategoryGui(player, category);
            }
        } else {
            if (meta.getPersistentDataContainer().has(actionKey, PersistentDataType.STRING)) {
                cosmeticManager.openCosmeticGui(player);
                return;
            }

            if (meta.getPersistentDataContainer().has(idKey, PersistentDataType.STRING)) {
                handleEffectSelection(player, meta);
            }
        }
    }

    private void handleEffectSelection(Player player, ItemMeta meta) {
        NamespacedKey idKey = new NamespacedKey(plugin, "cosmetic_id");
        NamespacedKey typeKey = new NamespacedKey(plugin, "cosmetic_type");

        String cosmeticId = meta.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        String cosmeticType = meta.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);

        VictoryEffect effect = cosmeticManager.getEffectById(cosmeticId);
        if (effect == null) return;

        PlayerStats stats = playerDataManager.getStats(player.getUniqueId());
        if (stats == null) return;

        if (stats.getOwnedCosmetics().contains(cosmeticId)) {
            selectEffect(stats, cosmeticType, cosmeticId);
            player.sendMessage(settingsManager.PREFIX + settingsManager.format("&a" + effect.getDisplayName() + " &aefekti seçildi!"));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        } else {
            if (stats.getBCoin() >= effect.getPrice()) {
                playerDataManager.addBCoin(player, -effect.getPrice());
                stats.getOwnedCosmetics().add(cosmeticId);
                selectEffect(stats, cosmeticType, cosmeticId);
                player.sendMessage(settingsManager.PREFIX + settingsManager.format("&a" + effect.getDisplayName() + " &aefekti başarıyla satın alındı ve seçildi!"));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            } else {
                player.sendMessage(settingsManager.PREFIX + settingsManager.format("&cBu efekti almak için yeterli &e" + settingsManager.COIN_SYSTEM_NAME + " &cyok!"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                player.closeInventory();
                return;
            }
        }
        playerDataManager.savePlayerData(player, false);
        cosmeticManager.openCategoryGui(player, cosmeticType);
    }

    private void selectEffect(PlayerStats stats, String type, String id) {
        switch (type) {
            case "victory":
                stats.setSelectedCosmetic(id);
                break;
            case "kill":
                stats.setSelectedKillEffect(id);
                break;
            case "arrow":
                stats.setSelectedArrowEffect(id);
                break;
        }
    }
}