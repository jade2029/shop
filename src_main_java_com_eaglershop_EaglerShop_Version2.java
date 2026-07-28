package com.eaglershop;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class. Initializes components, Vault hooks, commands, listeners, and scheduler.
 */
public class EaglerShop extends JavaPlugin {
    private Economy economy;
    private Permission permissions;
    private SupplyDemandEngine sdEngine;
    private EnchantAnalyzer enchantAnalyzer;
    private AuctionManager auctionManager;
    private StorageManager storageManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.storageManager = new StorageManager(this);
        this.sdEngine = new SupplyDemandEngine(this, storageManager);
        this.enchantAnalyzer = new EnchantAnalyzer(this);
        this.auctionManager = new AuctionManager(this, storageManager, sdEngine, enchantAnalyzer);

        // Vault hooks (economy + permissions)
        if (!setupVault()) {
            getLogger().warning("Vault not found or missing economy provider. Economy features require Vault + an economy plugin.");
        }

        // Register commands
        getCommand("shop").setExecutor(new ShopCommand(this, sdEngine, enchantAnalyzer, storageManager, economy));
        getCommand("shopadmin").setExecutor(new ShopAdminCommand(this, sdEngine, storageManager, auctionManager));
        getCommand("ah").setExecutor(new AuctionCommand(this, auctionManager, storageManager, economy));
        getCommand("ahadmin").setExecutor(new AuctionAdminCommand(this, auctionManager, storageManager));

        // Register listeners
        getServer().getPluginManager().registerEvents(new InventoryListener(this, sdEngine, enchantAnalyzer, auctionManager, storageManager, economy), this);

        // Load persisted data
        storageManager.loadAll(sdEngine, auctionManager);

        // Start auction expiration scheduler (run every 60 seconds)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            try {
                auctionManager.checkExpiredAndResolve();
            } catch (Exception ex) {
                getLogger().warning("Auction scheduler error: " + ex.getMessage());
            }
        }, 20L, 20L * 60L);

        getLogger().info("EaglerShop enabled.");
    }

    @Override
    public void onDisable() {
        storageManager.saveAll(sdEngine, auctionManager);
        getLogger().info("EaglerShop disabled.");
    }

    private boolean setupVault() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
        }
        RegisteredServiceProvider<Permission> rspPerm = getServer().getServicesManager().getRegistration(Permission.class);
        if (rspPerm != null) {
            permissions = rspPerm.getProvider();
        }
        return economy != null;
    }

    public Economy getEconomy() { return economy; }
    public Permission getPermissions() { return permissions; }
    public SupplyDemandEngine getSdEngine() { return sdEngine; }
    public EnchantAnalyzer getEnchantAnalyzer() { return enchantAnalyzer; }
    public AuctionManager getAuctionManager() { return auctionManager; }
    public StorageManager getStorageManager() { return storageManager; }
}