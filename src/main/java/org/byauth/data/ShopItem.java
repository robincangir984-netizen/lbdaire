package org.byauth.data;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.List;

public class ShopItem {
    private final String id;
    private final ItemStack displayItem;
    private final int price;
    private final ItemStack giveItem;

    public ShopItem(String id, ItemStack displayItem, int price, ItemStack giveItem) {
        this.id = id;
        this.displayItem = displayItem;
        this.price = price;
        this.giveItem = giveItem;
    }

    public String getId() {
        return id;
    }

    public ItemStack getDisplayItem() {
        return displayItem;
    }

    public int getPrice() {
        return price;
    }

    public ItemStack getGiveItem() {
        return giveItem;
    }
}