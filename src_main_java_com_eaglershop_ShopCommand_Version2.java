package com.eaglershop;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

/**
 * /shop command implementation: open GUI, price lookup, and sell in-hand.
 */
public class ShopCommand implements CommandExecutor {
    private final EaglerShop plugin;
    private final SupplyDemandEngine sdEngine;
    private final EnchantAnalyzer enchantAnalyzer;
    private final StorageManager storage;
    private final Economy economy;

    public ShopCommand(EaglerShop plugin, SupplyDemandEngine sdEngine, EnchantAnalyzer enchantAnalyzer, StorageManager storage, Economy economy) {
        this.plugin = plugin; this.sdEngine = sdEngine; this.enchantAnalyzer = enchantAnalyzer; this.storage = storage; this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("Only players can use this command."); return true; }
        Player p = (Player) sender;
        if (args.length == 0) {
            ShopGUI.openShop(p, plugin);
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("price") && args.length >= 2) {
            String mat = args[1].toUpperCase();
            Material m = Material.matchMaterial(mat);
            if (m == null) { p.sendMessage("Unknown material."); return true; }
            ItemStack fake = new ItemStack(m, 1);
            double price = sdEngine.getPriceFor(fake, p);
            p.sendMessage("Current price for " + mat + ": " + price);
            return true;
        } else if (sub.equals("sell")) {
            if (!p.hasPermission("eagler.shop.sell")) {
                p.sendMessage("You don't have permission to sell.");
                return true;
            }
            ItemStack inHand = p.getInventory().getItemInMainHand();
            if (inHand == null || inHand.getType() == Material.AIR) {
                p.sendMessage("Hold an item in hand to sell.");
                return true;
            }
            double price = sdEngine.getPriceFor(inHand, p);
            if (economy != null) economy.depositPlayer(p, price);
            sdEngine.recordSell(inHand, inHand.getAmount());
            p.getInventory().setItemInMainHand(null);
            p.sendMessage("Sold for " + price);
            return true;
        }
        p.sendMessage("Usage: /shop [sell|price <material>]");
        return true;
    }
}