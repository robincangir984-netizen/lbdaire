package org.byauth.manager;

import org.byauth.ByCircleGame;
import org.byauth.data.ShopItem;
import org.byauth.utils.ItemBuilder;
import org.byauth.utils.SettingsManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ShopManager {

    private final ByCircleGame plugin;
    private final SettingsManager settings;
    private final File shopFile;
    private FileConfiguration shopConfig;
    private final List<ShopItem> shopItems = new ArrayList<>();

    public ShopManager(ByCircleGame plugin) {
        this.plugin = plugin;
        this.settings = plugin.getSettingsManager();
        this.shopFile = new File(plugin.getDataFolder(), "shops.yml");
        if (!shopFile.exists()) {
            plugin.saveResource("shops.yml", false);
        }
        this.shopConfig = YamlConfiguration.loadConfiguration(shopFile);
        loadShopItems();
    }

    private void loadShopItems() {
        shopItems.clear();
        List<Map<?, ?>> itemsList = shopConfig.getMapList("shop-items");
        if (itemsList == null) return;

        for (Map<?, ?> rawMap : itemsList) {
            @SuppressWarnings("unchecked")
            Map<String, Object> itemMap = (Map<String, Object>) rawMap;
            try {
                String id = (String) itemMap.get("id");
                String materialStr = (String) itemMap.getOrDefault("material", "STONE");
                Material material = Material.matchMaterial(materialStr.toUpperCase());
                int quantity = (Integer) itemMap.getOrDefault("quantity", 1);
                int price = (Integer) itemMap.getOrDefault("price", 100);
                String displayName = settings.format((String) itemMap.getOrDefault("display-name", "&cİsimsiz Eşya"));

                @SuppressWarnings("unchecked")
                List<String> lore = (List<String>) itemMap.get("lore");
                if (lore == null) {
                    lore = new ArrayList<>();
                }

                List<String> formattedLore = lore.stream()
                        .map(line -> settings.format(line
                                .replace("%price%", String.valueOf(price))
                                .replace("%coin_name%", settings.COIN_SYSTEM_NAME)))
                        .collect(Collectors.toList());

                String specialItemId = (String) itemMap.get("special-item-id");

                ItemStack displayItem = new ItemBuilder(material).setName(displayName).setLore(formattedLore).build();
                ItemStack giveItem = null;

                if (specialItemId != null) {
                    giveItem = plugin.getLootManager().getSpecialItem(specialItemId);
                    if (giveItem != null) {
                        giveItem.setAmount(quantity);
                    }
                } else if (material != null) {
                    giveItem = new ItemStack(material, quantity);
                }

                if (giveItem != null) {
                    shopItems.add(new ShopItem(id, displayItem, price, giveItem));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("shops.yml dosyasındaki bir eşya yüklenirken hata oluştu: " + e.getMessage());
            }
        }
    }

    public List<ShopItem> getShopItems() {
        return shopItems;
    }

    public ShopItem getShopItemById(String id) {
        for (ShopItem item : shopItems) {
            if (item.getId().equals(id)) {
                return item;
            }
        }
        return null;
    }
}