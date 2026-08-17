package org.byauth.manager;

import org.byauth.ByCircleGame;
import org.byauth.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

public class LootManager {

    private static class LootItem {
        ItemStack item;
        int weight;

        LootItem(ItemStack item, int weight) {
            this.item = item;
            this.weight = weight;
        }
    }

    private final ByCircleGame plugin;
    private final File itemsFile;
    private FileConfiguration itemsConfig;
    private final Map<String, ItemStack> specialItems = new HashMap<>();
    private final Map<Integer, List<LootItem>> lootTiers = new HashMap<>();

    public LootManager(ByCircleGame plugin) {
        this.plugin = plugin;
        this.itemsFile = new File(plugin.getDataFolder(), "items.yml");
        loadLoot();
    }

    public void reload() {
        loadLoot();
    }

    private void loadLoot() {
        if (!itemsFile.exists()) {
            plugin.saveResource("items.yml", false);
        }
        itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);

        specialItems.clear();
        ConfigurationSection specialItemsSection = itemsConfig.getConfigurationSection("special-items");
        if (specialItemsSection != null) {
            for (String key : specialItemsSection.getKeys(false)) {
                Material material = Material.matchMaterial(specialItemsSection.getString(key + ".material", "STONE"));
                String name = plugin.getSettingsManager().format(specialItemsSection.getString(key + ".name", "Özel Eşya"));
                List<String> lore = specialItemsSection.getStringList(key + ".lore");
                ItemStack item = new ItemBuilder(material).setName(name).setLore(lore).build();
                specialItems.put(key, item);
            }
        }

        lootTiers.clear();
        ConfigurationSection lootTiersSection = itemsConfig.getConfigurationSection("loot-tiers");
        if (lootTiersSection != null) {
            for (String key : lootTiersSection.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(key.replace("tier-", ""));
                    List<LootItem> items = new ArrayList<>();
                    for (String itemString : lootTiersSection.getStringList(key)) {
                        String[] parts = itemString.split(",");
                        if (parts.length != 3) continue;

                        int amount = Integer.parseInt(parts[1]);
                        int weight = Integer.parseInt(parts[2]);
                        ItemStack item;

                        if (parts[0].startsWith("special:")) {
                            String specialId = parts[0].substring(8);
                            item = specialItems.get(specialId);
                            if (item != null) {
                                item = item.clone();
                                item.setAmount(amount);
                            }
                        } else {
                            Material material = Material.matchMaterial(parts[0]);
                            if (material != null) {
                                item = new ItemStack(material, amount);
                            } else {
                                item = null;
                            }
                        }
                        if (item != null) {
                            items.add(new LootItem(item, weight));
                        }
                    }
                    lootTiers.put(tier, items);
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    public ItemStack getSpecialItem(String id) {
        ItemStack item = specialItems.get(id);
        return item != null ? item.clone() : null;
    }

    public List<ItemStack> getLootForRound(int round) {
        List<LootItem> weightedList = new ArrayList<>();
        int maxTier = Collections.max(lootTiers.keySet());
        int currentTier = Math.min(round, maxTier);

        for (int i = 1; i <= currentTier; i++) {
            if (lootTiers.containsKey(i)) {
                weightedList.addAll(lootTiers.get(i));
            }
        }

        if (weightedList.isEmpty()) {
            return new ArrayList<>();
        }

        int totalWeight = weightedList.stream().mapToInt(li -> li.weight).sum();
        List<ItemStack> finalLoot = new ArrayList<>();
        Random random = new Random();

        if (totalWeight <= 0) return finalLoot;

        for (int i = 0; i < 27; i++) {
            int randomWeight = random.nextInt(totalWeight);
            int currentWeight = 0;
            for (LootItem lootItem : weightedList) {
                currentWeight += lootItem.weight;
                if (randomWeight < currentWeight) {
                    finalLoot.add(lootItem.item.clone());
                    break;
                }
            }
        }
        return finalLoot;
    }
}