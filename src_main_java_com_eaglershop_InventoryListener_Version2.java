package com.eaglershop;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

/**
 * Handles clicks in the Shop and Auction GUIs.
 * Identification is done by inventory title and lore markers on items.
 */
public class InventoryListener implements Listener {
    private final EaglerShop plugin;
    private final SupplyDemandEngine sdEngine;
    private final EnchantAnalyzer enchantAnalyzer;
    private final AuctionManager auctionManager;
    private final StorageManager storage;
    private final Economy economy;

    public InventoryListener(EaglerShop plugin, SupplyDemandEngine sdEngine, EnchantAnalyzer enchantAnalyzer, AuctionManager auctionManager, StorageManager storage, Economy economy) {
        this.plugin = plugin; this.sdEngine = sdEngine; this.enchantAnalyzer = enchantAnalyzer; this.auctionManager = auctionManager; this.storage = storage; this.economy = economy;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (e.getCurrentItem() == null) return;
        String title = e.getView().getTitle();
        ItemStack clicked = e.getCurrentItem();
        e.setCancelled(true); // handle interactions entirely

        if (title.startsWith("EaglerShop")) {
            // Shop click -> buy the clicked item (single unit behavior)
            ItemMeta meta = clicked.getItemMeta();
            double price = sdEngine.getPriceFor(clicked, p);
            if (economy != null) {
                if (economy.has(p, price)) {
                    economy.withdrawPlayer(p, price);
                    p.getInventory().addItem(clicked.clone());
                    sdEngine.recordBuy(clicked, clicked.getAmount());
                    p.sendMessage(ChatColor.GREEN + "Purchased for " + price);
                } else {
                    p.sendMessage(ChatColor.RED + "You need " + price + " to buy this item.");
                }
            } else {
                p.sendMessage(ChatColor.RED + "Economy not available.");
            }
        } else if (title.startsWith("EaglerAH")) {
            // Auction listing click -> check lore for ID or buy-it-now
            ItemMeta meta = clicked.getItemMeta();
            if (meta == null) return;
            List<String> lore = meta.getLore();
            if (lore == null || lore.isEmpty()) return;
            String idLine = lore.get(0);
            // lore first line expected: "ID: <id>"
            if (idLine.startsWith("ID: ")) {
                try {
                    int id = Integer.parseInt(idLine.substring(4).trim());
                    AuctionManager.Listing listing = auctionManager.getListing(id);
                    if (listing == null) {
                        p.sendMessage(ChatColor.RED + "Listing no longer available.");
                        return;
                    }
                    // left-click = view/info, right-click = buy-if-allowed
                    if (e.isLeftClick()) {
                        p.sendMessage(ChatColor.GOLD + "Listing " + id + " Price: " + listing.currentBid + (listing.buyItNow ? " (Buy-It-Now available)" : ""));
                        p.sendMessage(ChatColor.GRAY + "Seller: " + Bukkit.getOfflinePlayer(listing.seller).getName());
                    } else if (e.isRightClick()) {
                        // Attempt buy-it-now
                        if (listing.buyItNow) {
                            boolean ok = auctionManager.buyNow(id, p.getUniqueId());
                            if (ok) p.sendMessage(ChatColor.GREEN + "You bought listing " + id + " via Buy-It-Now!");
                            else p.sendMessage(ChatColor.RED + "Failed to buy listing. Check funds or listing status.");
                        } else {
                            p.sendMessage(ChatColor.RED + "This listing does not have Buy-It-Now enabled.");
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
    }
}