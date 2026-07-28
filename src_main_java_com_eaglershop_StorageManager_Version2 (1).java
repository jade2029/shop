package com.eaglershop;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

/**
 * Storage manager using YAML files (sd.yml and auctions.yml).
 * Persists per-item counters and auction listings and pending claims.
 */
public class StorageManager {
    private final JavaPlugin plugin;
    private File sdFile;
    private FileConfiguration sdConfig;
    private File auctionsFile;
    private FileConfiguration auctionsConfig;

    public StorageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setupFiles();
    }

    private void setupFiles() {
        sdFile = new File(plugin.getDataFolder(), "sd.yml");
        auctionsFile = new File(plugin.getDataFolder(), "auctions.yml");
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        try {
            if (!sdFile.exists()) sdFile.createNewFile();
            if (!auctionsFile.exists()) auctionsFile.createNewFile();
        } catch (IOException e) {
            plugin.getLogger().severe("Could not create storage files: " + e.getMessage());
        }
        sdConfig = YamlConfiguration.loadConfiguration(sdFile);
        auctionsConfig = YamlConfiguration.loadConfiguration(auctionsFile);
    }

    // sd.yml accessors
    public FileConfiguration getSdConfig() { return sdConfig; }
    public void saveSd() {
        try { sdConfig.save(sdFile); } catch (IOException e) { plugin.getLogger().warning("Failed to save sd.yml: " + e.getMessage()); }
    }

    // auctions.yml accessors
    public FileConfiguration getAuctionsConfig() { return auctionsConfig; }
    public void saveAuctions() {
        try { auctionsConfig.save(auctionsFile); } catch (IOException e) { plugin.getLogger().warning("Failed to save auctions.yml: " + e.getMessage()); }
    }

    // High-level load/save
    public void loadAll(SupplyDemandEngine sdEngine, AuctionManager auctionManager) {
        sdEngine.loadFromConfig(sdConfig);
        auctionManager.loadFromConfig(auctionsConfig);
    }

    public void saveAll(SupplyDemandEngine sdEngine, AuctionManager auctionManager) {
        sdEngine.saveToConfig(sdConfig);
        saveSd();
        auctionManager.saveToConfig(auctionsConfig);
        saveAuctions();
    }
}