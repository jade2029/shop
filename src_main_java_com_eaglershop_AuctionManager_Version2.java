package com.eaglershop;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Auction manager with persistence and scheduled expiration handling.
 *
 * Listings are persisted into auctions.yml under 'listings.<id>'.
 * Pending claims for offline players are persisted under 'claims.<uuid>'.
 *
 * When a listing expires, the highest bidder (if present) wins and receives the item and seller receives funds minus tax.
 * If no bidder, listing returns to seller (as pending item if offline).
 */
public class AuctionManager {
    private final EaglerShop plugin;
    private final StorageManager storage;
    private final SupplyDemandEngine sdEngine;
    private final EnchantAnalyzer enchantAnalyzer;
    private final Map<Integer, Listing> listings = new HashMap<>();
    private int nextId = 1;

    public AuctionManager(EaglerShop plugin, StorageManager storage, SupplyDemandEngine sdEngine, EnchantAnalyzer enchantAnalyzer) {
        this.plugin = plugin; this.storage = storage; this.sdEngine = sdEngine; this.enchantAnalyzer = enchantAnalyzer;
    }

    public synchronized Listing createListing(UUID seller, ItemStack item, double startPrice, long durationSeconds, boolean buyItNow) {
        int id = nextId++;
        Listing l = new Listing(id, seller, item, startPrice, System.currentTimeMillis(), durationSeconds * 1000L, buyItNow);
        listings.put(id, l);
        // charge listing fee
        double feePercent = plugin.getConfig().getDouble("auctions.listing-fee-percent", 5.0);
        double fee = startPrice * (feePercent / 100.0);
        Economy econ = plugin.getEconomy();
        if (econ != null) {
            OfflinePlayer op = plugin.getServer().getOfflinePlayer(seller);
            econ.withdrawPlayer(op, fee);
        }
        plugin.getStorageManager().saveAuctions();
        return l;
    }

    public synchronized Listing getListing(int id) { return listings.get(id); }

    public synchronized Collection<Listing> getAllListings() { return new ArrayList<>(listings.values()); }

    public synchronized boolean placeBid(int id, UUID bidder, double amount) {
        Listing l = listings.get(id);
        if (l == null) return false;
        if (l.buyItNow && amount >= l.startPrice) {
            // buy-it-now behavior handled via buyNow
            return false;
        }
        if (amount <= l.currentBid) return false;
        Economy econ = plugin.getEconomy();
        if (econ == null) return false;
        OfflinePlayer off = plugin.getServer().getOfflinePlayer(bidder);
        if (!econ.has(off, amount)) return false;
        // withdraw immediately to hold funds
        econ.withdrawPlayer(off, amount);
        // refund previous bidder
        if (l.currentBidder != null) {
            econ.depositPlayer(plugin.getServer().getOfflinePlayer(l.currentBidder), l.currentBid);
        }
        l.currentBid = amount;
        l.currentBidder = bidder;
        plugin.getStorageManager().saveAuctions();
        return true;
    }

    public synchronized boolean buyNow(int id, UUID buyer) {
        Listing l = listings.get(id);
        if (l == null || !l.buyItNow) return false;
        Economy econ = plugin.getEconomy();
        if (econ == null) return false;
        OfflinePlayer off = plugin.getServer().getOfflinePlayer(buyer);
        if (!econ.has(off, l.startPrice)) return false;
        // withdraw buyer
        econ.withdrawPlayer(off, l.startPrice);
        // payout to seller minus tax
        double taxPercent = plugin.getConfig().getDouble("auctions.sale-tax-percent", 2.0);
        double payout = l.startPrice * (1.0 - taxPercent / 100.0);
        econ.depositPlayer(plugin.getServer().getOfflinePlayer(l.seller), payout);
        // deliver item to buyer (online or claim)
        deliverItemToPlayerOrClaim(buyer, l.item);
        listings.remove(id);
        plugin.getStorageManager().saveAuctions();
        return true;
    }

    private void deliverItemToPlayerOrClaim(UUID target, ItemStack item) {
        org.bukkit.entity.Player online = plugin.getServer().getPlayer(target);
        if (online != null && online.isOnline()) {
            online.getInventory().addItem(item);
            online.sendMessage("You received an auction item.");
        } else {
            // add to pending claims
            FileConfiguration cfg = storage.getAuctionsConfig();
            String path = "claims." + target.toString() + ".items";
            List<ItemStack> items = (List<ItemStack>) cfg.getList(path, new ArrayList<>());
            items.add(item);
            cfg.set(path, items);
            storage.saveAuctions();
        }
    }

    private void addMoneyToClaim(UUID target, double amount) {
        FileConfiguration cfg = storage.getAuctionsConfig();
        String path = "claims." + target.toString() + ".money";
        double prev = cfg.getDouble(path, 0.0);
        cfg.set(path, prev + amount);
        storage.saveAuctions();
    }

    // Expiration checker, called regularly
    public synchronized void checkExpiredAndResolve() {
        List<Listing> expired = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Listing l : listings.values()) {
            if (l.createdAt + l.durationMs <= now) expired.add(l);
        }
        for (Listing l : expired) {
            resolveListing(l);
        }
        if (!expired.isEmpty()) storage.saveAuctions();
    }

    private void resolveListing(Listing l) {
        // If someone bid, winner gets item and seller gets money (currentBid) minus tax.
        Economy econ = plugin.getEconomy();
        if (l.currentBidder != null && l.currentBid > 0) {
            double taxPercent = plugin.getConfig().getDouble("auctions.sale-tax-percent", 2.0);
            double payout = l.currentBid * (1.0 - taxPercent / 100.0);
            if (econ != null) {
                OfflinePlayer sellerOff = plugin.getServer().getOfflinePlayer(l.seller);
                if (Bukkit.getPlayer(l.seller) != null) {
                    econ.depositPlayer(sellerOff, payout);
                } else {
                    // seller offline: add to pending money claims
                    addMoneyToClaim(l.seller, payout);
                }
            }
            // deliver item to winner (or claim)
            deliverItemToPlayerOrClaim(l.currentBidder, l.item);
        } else {
            // No bids: return to seller (as item claim if offline)
            deliverItemToPlayerOrClaim(l.seller, l.item);
        }
        listings.remove(l.id);
    }

    public synchronized void loadFromConfig(FileConfiguration cfg) {
        listings.clear();
        nextId = cfg.getInt("next-id", 1);
        if (cfg.isConfigurationSection("listings")) {
            for (String key : cfg.getConfigurationSection("listings").getKeys(false)) {
                try {
                    int id = Integer.parseInt(key);
                    String base = "listings." + key;
                    UUID seller = UUID.fromString(cfg.getString(base + ".seller"));
                    ItemStack item = cfg.getItemStack(base + ".item");
                    double start = cfg.getDouble(base + ".startPrice", 0.0);
                    long created = cfg.getLong(base + ".createdAt", System.currentTimeMillis());
                    long duration = cfg.getLong(base + ".durationMs", 86400L * 1000L);
                    boolean buyit = cfg.getBoolean(base + ".buyItNow", true);
                    UUID currentBidder = null;
                    String cb = cfg.getString(base + ".currentBidder", null);
                    if (cb != null && !cb.isEmpty()) currentBidder = UUID.fromString(cb);
                    double currentBid = cfg.getDouble(base + ".currentBid", start);
                    Listing l = new Listing(id, seller, item, start, created, duration, buyit);
                    l.currentBid = currentBid;
                    l.currentBidder = currentBidder;
                    listings.put(id, l);
                    if (id >= nextId) nextId = id + 1;
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to load listing: " + key + " error: " + ex.getMessage());
                }
            }
        }
    }

    public synchronized void saveToConfig(FileConfiguration cfg) {
        cfg.set("next-id", nextId);
        cfg.set("listings", null);
        for (Map.Entry<Integer, Listing> e : listings.entrySet()) {
            String base = "listings." + e.getKey();
            Listing l = e.getValue();
            cfg.set(base + ".seller", l.seller.toString());
            cfg.set(base + ".item", l.item);
            cfg.set(base + ".startPrice", l.startPrice);
            cfg.set(base + ".createdAt", l.createdAt);
            cfg.set(base + ".durationMs", l.durationMs);
            cfg.set(base + ".buyItNow", l.buyItNow);
            cfg.set(base + ".currentBid", l.currentBid);
            cfg.set(base + ".currentBidder", l.currentBidder == null ? null : l.currentBidder.toString());
        }
    }

    public synchronized Map<UUID, PendingClaim> getClaimsFor(UUID uuid) {
        FileConfiguration cfg = storage.getAuctionsConfig();
        String base = "claims." + uuid.toString();
        List<ItemStack> items = (List<ItemStack>) cfg.getList(base + ".items", new ArrayList<>());
        double money = cfg.getDouble(base + ".money", 0.0);
        return new HashMap<UUID, PendingClaim>() {{ put(uuid, new PendingClaim(items, money)); }};
    }

    public synchronized PendingClaim popClaims(UUID uuid) {
        FileConfiguration cfg = storage.getAuctionsConfig();
        String base = "claims." + uuid.toString();
        List<ItemStack> items = (List<ItemStack>) cfg.getList(base + ".items", new ArrayList<>());
        double money = cfg.getDouble(base + ".money", 0.0);
        cfg.set(base, null);
        storage.saveAuctions();
        return new PendingClaim(items, money);
    }

    // Inner classes
    public static class Listing {
        public final int id;
        public final UUID seller;
        public final ItemStack item;
        public final double startPrice;
        public final long createdAt;
        public final long durationMs;
        public boolean buyItNow;
        public UUID currentBidder;
        public double currentBid;

        public Listing(int id, UUID seller, ItemStack item, double startPrice, long createdAt, long durationMs, boolean buyItNow) {
            this.id = id; this.seller = seller; this.item = item; this.startPrice = startPrice; this.createdAt = createdAt; this.durationMs = durationMs; this.buyItNow = buyItNow;
            this.currentBid = startPrice; this.currentBidder = null;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > createdAt + durationMs;
        }
    }

    public static class PendingClaim {
        public final List<ItemStack> items;
        public final double money;
        public PendingClaim(List<ItemStack> items, double money) { this.items = items; this.money = money; }
    }
}