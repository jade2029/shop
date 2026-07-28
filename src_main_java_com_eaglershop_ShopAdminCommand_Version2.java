package com.eaglershop;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /shopadmin admin commands: reload, save, additem, removeitem, resetstats
 */
public class ShopAdminCommand implements CommandExecutor {
    private final EaglerShop plugin;
    private final SupplyDemandEngine sdEngine;
    private final StorageManager storage;
    private final AuctionManager auctionManager;

    public ShopAdminCommand(EaglerShop plugin, SupplyDemandEngine sdEngine, StorageManager storage, AuctionManager auctionManager) {
        this.plugin = plugin; this.sdEngine = sdEngine; this.storage = storage; this.auctionManager = auctionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("eagler.shop.admin")) {
            sender.sendMessage("No permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("Usage: /shopadmin reload|save|additem|removeitem|resetstats|forcedelivery");
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload":
                plugin.reloadConfig();
                sdEngine.loadDefaults();
                plugin.getEnchantAnalyzer().loadConfig();
                sender.sendMessage("EaglerShop config reloaded.");
                break;
            case "save":
                storage.saveAll(sdEngine, auctionManager);
                sender.sendMessage("Saved storage.");
                break;
            case "resetstats":
                if (args.length < 2) { sender.sendMessage("Usage: /shopadmin resetstats <MATERIAL>"); break; }
                String mat = args[1].toUpperCase();
                sdEngine.removeRule(mat); // also clears counters for safety
                sender.sendMessage("Reset stats for " + mat);
                break;
            case "additem":
                if (args.length < 6) { sender.sendMessage("Usage: /shopadmin additem <MATERIAL> <base> <dWeight> <sWeight> <minMult> <maxMult>"); break; }
                try {
                    String material = args[1].toUpperCase();
                    double base = Double.parseDouble(args[2]);
                    double dW = Double.parseDouble(args[3]);
                    double sW = Double.parseDouble(args[4]);
                    double min = Double.parseDouble(args[5]);
                    double max = Double.parseDouble(args[6]);
                    sdEngine.addRule(material, base, dW, sW, min, max);
                    sender.sendMessage("Added rule for " + material);
                } catch (Exception ex) {
                    sender.sendMessage("Invalid arguments: " + ex.getMessage());
                }
                break;
            default:
                sender.sendMessage("Unknown subcommand.");
        }
        return true;
    }
}