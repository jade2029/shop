package com.eaglershop;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Auction admin commands: cancel, refund, force-deliver, list
 */
public class AuctionAdminCommand implements CommandExecutor {
    private final EaglerShop plugin;
    private final AuctionManager auctionManager;
    private final StorageManager storage;

    public AuctionAdminCommand(EaglerShop plugin, AuctionManager auctionManager, StorageManager storage) {
        this.plugin = plugin; this.auctionManager = auctionManager; this.storage = storage;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("eagler.ah.admin")) { sender.sendMessage("No permission."); return true; }
        if (args.length == 0) { sender.sendMessage("Usage: /ahadmin cancel|refund|force-deliver|list"); return true; }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "cancel":
                if (args.length < 2) { sender.sendMessage("Usage: /ahadmin cancel <id>"); break; }
                int id = Integer.parseInt(args[1]);
                AuctionManager.Listing l = auctionManager.getListing(id);
                if (l == null) { sender.sendMessage("No such listing."); break; }
                auctionManager.deliverItemToPlayerOrClaim(l.seller, l.item);
                auctionManager.getAllListings().remove(l);
                storage.saveAuctions();
                sender.sendMessage("Cancelled listing " + id);
                break;
            case "refund":
                if (args.length < 3) { sender.sendMessage("Usage: /ahadmin refund <player> <amount>"); break; }
                OfflinePlayer p = Bukkit.getOfflinePlayer(args[1]);
                double amt = Double.parseDouble(args[2]);
                plugin.getEconomy().depositPlayer(p, amt);
                sender.sendMessage("Refunded " + amt + " to " + p.getName());
                break;
            case "list":
                sender.sendMessage("Active listings:");
                for (AuctionManager.Listing ls : auctionManager.getAllListings()) {
                    sender.sendMessage("ID " + ls.id + " seller=" + Bukkit.getOfflinePlayer(ls.seller).getName() + " price=" + ls.currentBid);
                }
                break;
            default:
                sender.sendMessage("Unknown subcommand.");
        }
        return true;
    }
}