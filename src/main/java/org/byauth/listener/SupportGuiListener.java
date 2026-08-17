package org.byauth.listener;

import org.byauth.ByCircleGame;
import org.byauth.controller.ArenaController;
import org.byauth.data.ShopItem;
import org.byauth.manager.PlayerDataManager;
import org.byauth.manager.ShopManager;
import org.byauth.utils.SettingsManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public class SupportGuiListener implements Listener {

    private final ByCircleGame plugin;
    private final ArenaController arenaController;
    private final SettingsManager settings;
    private final ShopManager shopManager;
    private final PlayerDataManager playerDataManager;

    public SupportGuiListener(ByCircleGame plugin) {
        this.plugin = plugin;
        this.arenaController = plugin.getArenaController();
        this.settings = plugin.getSettingsManager();
        this.shopManager = plugin.getShopManager();
        this.playerDataManager = plugin.getPlayerDataManager();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player spectator = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        String teammateTitle = settings.getMessage("support.gui-teammate-title");
        String shopTitlePrefix = ChatColor.stripColor(settings.getMessage("support.gui-shop-title").split("%")[0]);

        if (title.equals(teammateTitle)) {
            handleTeammateSelection(event, spectator);
        } else if (ChatColor.stripColor(title).startsWith(shopTitlePrefix)) {
            handleShopPurchase(event, spectator);
        }
    }

    private void handleTeammateSelection(InventoryClickEvent event, Player spectator) {
        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() != Material.PLAYER_HEAD) return;

        SkullMeta meta = (SkullMeta) clickedItem.getItemMeta();
        if (meta == null || meta.getOwningPlayer() == null || meta.getOwningPlayer().getName() == null) return;

        Player targetTeammate = Bukkit.getPlayerExact(ChatColor.stripColor(meta.getDisplayName()));

        if (targetTeammate != null && targetTeammate.isOnline()) {
            arenaController.openSupportShopGui(spectator, targetTeammate);
            spectator.playSound(spectator.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
        }
    }

    private void handleShopPurchase(InventoryClickEvent event, Player spectator) {
        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;

        NamespacedKey backKey = new NamespacedKey(plugin, "gui_action");
        if (meta.getPersistentDataContainer().has(backKey, PersistentDataType.STRING) &&
                meta.getPersistentDataContainer().get(backKey, PersistentDataType.STRING).equals("back_to_teammates")) {

            spectator.closeInventory();
            arenaController.openTeammateSupportGui(spectator);
            spectator.playSound(spectator.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        NamespacedKey shopKey = new NamespacedKey(plugin, "shop_item_id");
        if (!meta.getPersistentDataContainer().has(shopKey, PersistentDataType.STRING)) return;

        String shopItemId = meta.getPersistentDataContainer().get(shopKey, PersistentDataType.STRING);
        ShopItem shopItem = shopManager.getShopItemById(shopItemId);

        Player targetTeammate = arenaController.getSupportTarget(spectator);

        if (shopItem == null || targetTeammate == null || !targetTeammate.isOnline()) {
            spectator.closeInventory();
            return;
        }

        int price = shopItem.getPrice();
        int playerBCoins = playerDataManager.getStats(spectator.getUniqueId()).getBCoin();

        if (playerBCoins >= price) {
            playerDataManager.addBCoin(spectator, -price);

            ItemStack itemToGive = shopItem.getGiveItem().clone();
            Map<Integer, ItemStack> returned = targetTeammate.getInventory().addItem(itemToGive);
            if (!returned.isEmpty()) {
                targetTeammate.getWorld().dropItem(targetTeammate.getLocation(), returned.get(0));
            }

            spectator.sendMessage(settings.PREFIX + settings.getMessage("support.purchase-success-spectator")
                    .replace("%target%", targetTeammate.getName())
                    .replace("%item%", ChatColor.stripColor(shopItem.getDisplayItem().getItemMeta().getDisplayName())));

            targetTeammate.sendMessage(settings.PREFIX + settings.getMessage("support.purchase-notification-teammate")
                    .replace("%spectator%", spectator.getName())
                    .replace("%item%", ChatColor.stripColor(shopItem.getDisplayItem().getItemMeta().getDisplayName())));

            spectator.playSound(spectator.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            targetTeammate.playSound(targetTeammate.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);

            arenaController.openSupportShopGui(spectator, targetTeammate);

        } else {
            spectator.sendMessage(settings.PREFIX + settings.getMessage("support.insufficient-currency")
                    .replace("%coins%", String.valueOf(playerBCoins))
                    .replace("%coin_name%", settings.COIN_SYSTEM_NAME));
            spectator.playSound(spectator.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
    }
}