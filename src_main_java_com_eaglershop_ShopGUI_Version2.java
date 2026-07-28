package com.eaglershop;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a simple shop GUI showing configured rules from SupplyDemandEngine.
 * Each item shows price in lore so clicking the item in InventoryListener triggers a buy.
 */
public class ShopGUI {
    public static void openShop(Player p, EaglerShop plugin) {
        int size = plugin.getConfig().getInt("gui.shop-size", 36);
        Inventory inv = Bukkit.createInventory(null, size, "EaglerShop - Main");
        int slot = 0;
        for (String mat : plugin.getSdEngine().getRules().keySet()) {
            if (slot >= size) break;
            org.bukkit.Material matEnum = org.bukkit.Material.matchMaterial(mat);
            if (matEnum == null) continue;
            ItemStack is = new ItemStack(matEnum, 1);
            ItemMeta meta = is.getItemMeta();
            meta.setDisplayName("§a" + mat);
            double price = plugin.getSdEngine().getPriceFor(is, p);
            List<String> lore = new ArrayList<>();
            lore.add("Price: " + price);
            lore.add("Click to buy");
            meta.setLore(lore);
            is.setItemMeta(meta);
            inv.setItem(slot++, is);
        }
        // Add placeholder item for config info
        if (slot < size) inv.setItem(slot, plugin.getSdEngine().getSdConfigItemPlaceholder());
        p.openInventory(inv);
    }
}