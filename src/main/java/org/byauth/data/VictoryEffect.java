package org.byauth.data;

import org.bukkit.Material;

import java.util.List;

public class VictoryEffect {
    private final String id;
    private final String displayName;
    private final Material material;
    private final int slot;
    private final int price;
    private final List<String> loreAvailable;
    private final List<String> loreOwned;
    private final List<String> loreSelected;

    public VictoryEffect(String id, String displayName, Material material, int slot, int price, List<String> loreAvailable, List<String> loreOwned, List<String> loreSelected) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.slot = slot;
        this.price = price;
        this.loreAvailable = loreAvailable;
        this.loreOwned = loreOwned;
        this.loreSelected = loreSelected;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getMaterial() {
        return material;
    }

    public int getSlot() {
        return slot;
    }

    public int getPrice() {
        return price;
    }

    public List<String> getLoreAvailable() {
        return loreAvailable;
    }

    public List<String> getLoreOwned() {
        return loreOwned;
    }

    public List<String> getLoreSelected() {
        return loreSelected;
    }
}