package org.byauth.manager;

import org.byauth.ByCircleGame;
import org.byauth.data.PlayerStats;
import org.byauth.data.VictoryEffect;
import org.byauth.utils.ItemBuilder;
import org.byauth.utils.SettingsManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CosmeticManager {

    private final ByCircleGame plugin;
    private final SettingsManager settingsManager;
    private final PlayerDataManager playerDataManager;

    private final List<VictoryEffect> victoryEffects = new ArrayList<>();
    private final List<VictoryEffect> killEffects = new ArrayList<>();
    private final List<VictoryEffect> arrowEffects = new ArrayList<>();

    public String mainMenuTitle, victoryGuiTitle, killGuiTitle, arrowGuiTitle;

    public CosmeticManager(ByCircleGame plugin) {
        this.plugin = plugin;
        this.settingsManager = plugin.getSettingsManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        loadCosmetics();
    }

    public void loadCosmetics() {
        File cosmeticsFile = new File(plugin.getDataFolder(), "cosmetics.yml");
        if (!cosmeticsFile.exists()) {
            plugin.saveResource("cosmetics.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(cosmeticsFile);

        mainMenuTitle = settingsManager.format(config.getString("main-menu-title", "&8Kozmetik Menüsü"));
        victoryGuiTitle = settingsManager.format(config.getString("victory-effects.gui-title", "&8Zafer Efektleri"));
        killGuiTitle = settingsManager.format(config.getString("kill-effects.gui-title", "&8Öldürme Efektleri"));
        arrowGuiTitle = settingsManager.format(config.getString("arrow-effects.gui-title", "&8Ok İzi Efektleri"));

        loadEffectsFromConfig(config, "victory-effects", victoryEffects);
        loadEffectsFromConfig(config, "kill-effects", killEffects);
        loadEffectsFromConfig(config, "arrow-effects", arrowEffects);
    }

    private void loadEffectsFromConfig(FileConfiguration config, String section, List<VictoryEffect> effectList) {
        effectList.clear();
        List<Map<?, ?>> itemsList = config.getMapList(section + ".items");
        for (Map<?, ?> map : itemsList) {
            String id = (String) map.get("id");
            String displayName = settingsManager.format((String) map.get("display-name"));
            Material material = Material.matchMaterial((String) map.get("material"));
            int slot = (int) map.get("slot");
            int price = (int) map.get("price");
            List<String> loreAvailable = (List<String>) map.get("lore-available");
            List<String> loreOwned = (List<String>) map.get("lore-owned");
            List<String> loreSelected = (List<String>) map.get("lore-selected");

            if (id != null && displayName != null && material != null) {
                effectList.add(new VictoryEffect(id, displayName, material, slot, price, loreAvailable, loreOwned, loreSelected));
            }
        }
    }

    public void openCosmeticGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, mainMenuTitle);
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName(" ").build();
        for (int i = 0; i < gui.getSize(); i++) gui.setItem(i, filler);

        ItemStack victoryItem = new ItemBuilder(Material.BEACON).setName(victoryGuiTitle).setLore("&7Zafer efektlerini görüntülemek", "&7ve yönetmek için tıkla.").build();
        ItemMeta victoryMeta = victoryItem.getItemMeta();
        victoryMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "cosmetic_category"), PersistentDataType.STRING, "victory");
        victoryItem.setItemMeta(victoryMeta);

        ItemStack killItem = new ItemBuilder(Material.DIAMOND_SWORD).setName(killGuiTitle).setLore("&7Öldürme efektlerini görüntülemek", "&7ve yönetmek için tıkla.").build();
        ItemMeta killMeta = killItem.getItemMeta();
        killMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "cosmetic_category"), PersistentDataType.STRING, "kill");
        killItem.setItemMeta(killMeta);

        ItemStack arrowItem = new ItemBuilder(Material.ARROW).setName(arrowGuiTitle).setLore("&7Ok izi efektlerini görüntülemek", "&7ve yönetmek için tıkla.").build();
        ItemMeta arrowMeta = arrowItem.getItemMeta();
        arrowMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "cosmetic_category"), PersistentDataType.STRING, "arrow");
        arrowItem.setItemMeta(arrowMeta);

        gui.setItem(11, victoryItem);
        gui.setItem(13, killItem);
        gui.setItem(15, arrowItem);

        player.openInventory(gui);
    }

    public void openCategoryGui(Player player, String category) {
        List<VictoryEffect> effects;
        String title;
        String selectedId;
        PlayerStats stats = playerDataManager.getStats(player.getUniqueId());

        switch (category) {
            case "victory":
                effects = victoryEffects;
                title = victoryGuiTitle;
                selectedId = stats.getSelectedCosmetic();
                break;
            case "kill":
                effects = killEffects;
                title = killGuiTitle;
                selectedId = stats.getSelectedKillEffect();
                break;
            case "arrow":
                effects = arrowEffects;
                title = arrowGuiTitle;
                selectedId = stats.getSelectedArrowEffect();
                break;
            default:
                return;
        }

        Inventory gui = Bukkit.createInventory(null, 54, title);
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName(" ").build();
        for (int i = 0; i < gui.getSize(); i++) gui.setItem(i, filler);

        for (VictoryEffect effect : effects) {
            boolean isOwned = stats.getOwnedCosmetics().contains(effect.getId());
            boolean isSelected = effect.getId().equals(selectedId);

            List<String> lore;
            if (isSelected) {
                lore = effect.getLoreSelected();
            } else if (isOwned) {
                lore = effect.getLoreOwned();
            } else {
                lore = effect.getLoreAvailable();
            }

            List<String> formattedLore = lore.stream()
                    .map(line -> settingsManager.format(line
                            .replace("%price%", String.valueOf(effect.getPrice()))
                            .replace("%coin_name%", settingsManager.COIN_SYSTEM_NAME)))
                    .collect(Collectors.toList());

            ItemStack item = new ItemBuilder(effect.getMaterial())
                    .setName(effect.getDisplayName())
                    .setLore(formattedLore)
                    .build();

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "cosmetic_id"), PersistentDataType.STRING, effect.getId());
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "cosmetic_type"), PersistentDataType.STRING, category);
                item.setItemMeta(meta);
            }
            gui.setItem(effect.getSlot(), item);
        }

        ItemStack backButton = new ItemBuilder(Material.ARROW).setName("&cAna Menüye Dön").build();
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "cosmetic_action"), PersistentDataType.STRING, "back");
        backButton.setItemMeta(backMeta);
        gui.setItem(49, backButton);

        player.openInventory(gui);
    }

    public VictoryEffect getEffectById(String id) {
        return victoryEffects.stream().filter(e -> e.getId().equals(id)).findFirst()
                .or(() -> killEffects.stream().filter(e -> e.getId().equals(id)).findFirst())
                .or(() -> arrowEffects.stream().filter(e -> e.getId().equals(id)).findFirst())
                .orElse(null);
    }

    public List<VictoryEffect> getVictoryEffects() { return victoryEffects; }
}