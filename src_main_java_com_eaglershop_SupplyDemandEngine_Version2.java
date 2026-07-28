package com.eaglershop;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/**
 * Rules-based supply & demand engine.
 * Responsible for computing price multipliers from historical counters and storing rules.
 *
 * Order of price computation:
 *  - Start from basePrice (per-item rule or default)
 *  - Apply supply/demand multiplier based on counters and elasticity
 *  - Apply enchant percent (applied after SD)
 *  - Apply rank modifier (discount based on LuckPerms group via Vault)
 */
public class SupplyDemandEngine {
    private final EaglerShop plugin;
    private final StorageManager storage;
    // per-material rules and counters keyed by material name (Material#name)
    private final Map<String, ItemRule> rules = new HashMap<>();
    private final Map<String, Counters> counters = new HashMap<>();

    public SupplyDemandEngine(EaglerShop plugin, StorageManager storage) {
        this.plugin = plugin;
        this.storage = storage;
        loadDefaults();
    }

    private void loadDefaults() {
        // If sd.yml has no items, set a couple of sensible defaults in memory (persist on save).
        if (!storage.getSdConfig().isConfigurationSection("items")) {
            rules.put("DIAMOND_SWORD", new ItemRule("DIAMOND_SWORD", 1000, 1.0, 1.0, 0.5, 3.0));
            rules.put("DIAMOND", new ItemRule("DIAMOND", 250, 1.0, 1.0, 0.5, 2.5));
            // Will be persisted when plugin saves.
        }
    }

    public void loadFromConfig(FileConfiguration sdConfig) {
        rules.clear();
        counters.clear();
        if (sdConfig.isConfigurationSection("items")) {
            for (String key : sdConfig.getConfigurationSection("items").getKeys(false)) {
                String path = "items." + key;
                double base = sdConfig.getDouble(path + ".base-price", 100);
                double demandW = sdConfig.getDouble(path + ".demand-weight", 1.0);
                double supplyW = sdConfig.getDouble(path + ".supply-weight", 1.0);
                double min = sdConfig.getDouble(path + ".min-multiplier", 0.5);
                double max = sdConfig.getDouble(path + ".max-multiplier", 3.0);
                rules.put(key, new ItemRule(key, base, demandW, supplyW, min, max));
            }
        }
        if (sdConfig.isConfigurationSection("counters")) {
            for (String key : sdConfig.getConfigurationSection("counters").getKeys(false)) {
                double supply = sdConfig.getDouble("counters." + key + ".supply", 0.0);
                double demand = sdConfig.getDouble("counters." + key + ".demand", 0.0);
                counters.put(key, new Counters(supply, demand));
            }
        }
    }

    public void saveToConfig(FileConfiguration sdConfig) {
        sdConfig.set("items", null);
        for (Map.Entry<String, ItemRule> e : rules.entrySet()) {
            String base = "items." + e.getKey();
            ItemRule r = e.getValue();
            sdConfig.set(base + ".base-price", r.basePrice);
            sdConfig.set(base + ".demand-weight", r.demandWeight);
            sdConfig.set(base + ".supply-weight", r.supplyWeight);
            sdConfig.set(base + ".min-multiplier", r.minMultiplier);
            sdConfig.set(base + ".max-multiplier", r.maxMultiplier);
        }
        sdConfig.set("counters", null);
        for (Map.Entry<String, Counters> e : counters.entrySet()) {
            String base = "counters." + e.getKey();
            sdConfig.set(base + ".supply", e.getValue().supply);
            sdConfig.set(base + ".demand", e.getValue().demand);
        }
    }

    public double getPriceFor(ItemStack item, Player player) {
        if (item == null || item.getType() == Material.AIR) return 0.0;
        String mat = item.getType().toString();
        ItemRule rule = rules.get(mat);
        if (rule == null) {
            rule = new ItemRule(mat, plugin.getConfig().getDouble("supply-demand.defaults.base-price", 100.0),
                    1.0, 1.0, 0.5, 3.0);
        }
        Counters c = counters.getOrDefault(mat, new Counters(0, 0));
        double elasticity = plugin.getConfig().getDouble("supply-demand.elasticity", 0.05);
        double delta = (c.demand - c.supply) * elasticity;
        double multiplier = 1.0 + delta;
        multiplier = Math.max(rule.minMultiplier, Math.min(rule.maxMultiplier, multiplier));
        double price = rule.basePrice * multiplier;

        // apply enchant modifiers (after supply/demand)
        double enchantPercent = plugin.getEnchantAnalyzer().getTotalEnchantPercent(item);
        price = price * (1.0 + enchantPercent / 100.0);

        // apply rank modifier based on Vault permissions (LuckPerms)
        String group = "default";
        if (plugin.getPermissions() != null && player != null) {
            try {
                group = plugin.getPermissions().getPrimaryGroup(player);
            } catch (Exception ignored) {}
            if (group == null || group.isEmpty()) group = "default";
        }
        double rankMod = plugin.getConfig().getDouble("ranks." + group + ".price-modifier", 0.0);
        price = price * (1.0 - rankMod / 100.0); // percent discount positive -> lower price

        return Math.max(0.01, Math.round(price));
    }

    public void recordBuy(ItemStack item, int amount) {
        if (item == null || item.getType() == Material.AIR) return;
        String mat = item.getType().toString();
        ItemRule r = rules.get(mat);
        if (r == null) return;
        Counters c = counters.computeIfAbsent(mat, k -> new Counters(0,0));
        c.demand += r.demandWeight * amount;
    }

    public void recordSell(ItemStack item, int amount) {
        if (item == null || item.getType() == Material.AIR) return;
        String mat = item.getType().toString();
        ItemRule r = rules.get(mat);
        if (r == null) return;
        Counters c = counters.computeIfAbsent(mat, k -> new Counters(0,0));
        c.supply += r.supplyWeight * amount;
    }

    public void decayCounters() {
        double decayPercent = plugin.getConfig().getDouble("supply-demand.decay-per-day", 5.0);
        double factor = 1.0 - decayPercent / 100.0;
        for (Counters c : counters.values()) {
            c.demand *= factor;
            c.supply *= factor;
        }
    }

    // Add / helper for GUI placeholder
    public org.bukkit.inventory.ItemStack getSdConfigItemPlaceholder() {
        // simple placeholder: diamond with name "Configure items in sd.yml"
        org.bukkit.inventory.ItemStack is = new org.bukkit.inventory.ItemStack(Material.DIAMOND, 1);
        org.bukkit.inventory.meta.ItemMeta meta = is.getItemMeta();
        meta.setDisplayName("§aConfigure shop items");
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("§7Edit sd.yml or use /shopadmin additem");
        meta.setLore(lore);
        is.setItemMeta(meta);
        return is;
    }

    // Helper classes
    public static class ItemRule {
        public final String material;
        public final double basePrice;
        public final double demandWeight;
        public final double supplyWeight;
        public final double minMultiplier;
        public final double maxMultiplier;
        public ItemRule(String material, double basePrice, double demandWeight, double supplyWeight, double minMultiplier, double maxMultiplier) {
            this.material = material; this.basePrice = basePrice; this.demandWeight = demandWeight; this.supplyWeight = supplyWeight;
            this.minMultiplier = minMultiplier; this.maxMultiplier = maxMultiplier;
        }
    }

    public static class Counters {
        public double supply;
        public double demand;
        public Counters(double supply, double demand) { this.supply = supply; this.demand = demand; }
    }

    // Admin helpers: add/remove items programmatically
    public void addRule(String material, double basePrice, double demandWeight, double supplyWeight, double minMult, double maxMult) {
        rules.put(material, new ItemRule(material, basePrice, demandWeight, supplyWeight, minMult, maxMult));
    }
    public void removeRule(String material) { rules.remove(material); counters.remove(material); }
    public Map<String, ItemRule> getRules() { return rules; }
}