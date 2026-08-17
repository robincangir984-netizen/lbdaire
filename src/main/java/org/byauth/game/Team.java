package org.byauth.game;

import org.bukkit.ChatColor;
import org.bukkit.Material;

public enum Team {
    GRAY(ChatColor.GRAY, "Gri", Material.GRAY_CONCRETE, Material.GRAY_TERRACOTTA, Material.GRAY_STAINED_GLASS, Material.GRAY_BANNER),
    PURPLE(ChatColor.DARK_PURPLE, "Mor", Material.PURPLE_CONCRETE, Material.PURPLE_TERRACOTTA, Material.PURPLE_STAINED_GLASS, Material.PURPLE_BANNER),
    ORANGE(ChatColor.GOLD, "Turuncu", Material.ORANGE_CONCRETE, Material.ORANGE_TERRACOTTA, Material.ORANGE_STAINED_GLASS, Material.ORANGE_BANNER),
    AQUA(ChatColor.AQUA, "Aqua", Material.CYAN_CONCRETE, Material.CYAN_TERRACOTTA, Material.CYAN_STAINED_GLASS, Material.CYAN_BANNER),
    PINK(ChatColor.LIGHT_PURPLE, "Pembe", Material.PINK_CONCRETE, Material.PINK_TERRACOTTA, Material.PINK_STAINED_GLASS, Material.PINK_BANNER),
    YELLOW(ChatColor.YELLOW, "Sarı", Material.YELLOW_CONCRETE, Material.YELLOW_TERRACOTTA, Material.YELLOW_STAINED_GLASS, Material.YELLOW_BANNER),
    GREEN(ChatColor.GREEN, "Yeşil", Material.LIME_CONCRETE, Material.LIME_TERRACOTTA, Material.LIME_STAINED_GLASS, Material.LIME_BANNER);

    private final ChatColor chatColor;
    private final String displayName;
    private final Material concreteMaterial;
    private final Material terracottaMaterial;
    private final Material glassMaterial;
    private final Material bannerMaterial;

    Team(ChatColor chatColor, String displayName, Material concreteMaterial, Material terracottaMaterial, Material glassMaterial, Material bannerMaterial) {
        this.chatColor = chatColor;
        this.displayName = displayName;
        this.concreteMaterial = concreteMaterial;
        this.terracottaMaterial = terracottaMaterial;
        this.glassMaterial = glassMaterial;
        this.bannerMaterial = bannerMaterial;
    }

    public ChatColor getChatColor() {
        return chatColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getConcreteMaterial() {
        return concreteMaterial;
    }

    public Material getTerracottaMaterial() {
        return terracottaMaterial;
    }

    public Material getGlassMaterial() {
        return glassMaterial;
    }

    public Material getBannerMaterial() {
        return bannerMaterial;
    }
}