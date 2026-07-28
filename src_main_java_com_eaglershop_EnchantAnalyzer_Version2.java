package com.eaglershop;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Analyze enchantments and return a total percent modifier to apply to price.
 * Values come from config.yml under enchant-valuation.enchants.
 * The analyzer supports additive stacking (sum percents). This is configurable by changing logic here if desired.
 */
public class EnchantAnalyzer {
    private final EaglerShop plugin;
    private final Map<String, Map<Integer, Double>> enchantTable = new HashMap<>();
    private double defaultPerLevel = 2.0;

    public EnchantAnalyzer(EaglerShop plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        FileConfiguration cfg = plugin.getConfig();
        defaultPerLevel = cfg.getDouble("enchant-valuation.defaults.per-level-percent", 2.0);
        ConfigurationSection section = cfg.getConfigurationSection("enchant-valuation.enchants");
        enchantTable.clear();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection levels = section.getConfigurationSection(key + ".levels");
                Map<Integer, Double> map = new HashMap<>();
                if (levels != null) {
                    for (String lvl : levels.getKeys(false)) {
                        try {
                            map.put(Integer.parseInt(lvl), levels.getDouble(lvl));
                        } catch (NumberFormatException ignored) {}
                    }
                }
                enchantTable.put(key.toUpperCase(), map);
            }
        }
    }

    public double getTotalEnchantPercent(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0.0;
        double total = 0.0;
        for (Map.Entry<Enchantment, Integer> e : item.getItemMeta().getEnchants().entrySet()) {
            Enchantment ench = e.getKey();
            int lvl = e.getValue();
            String name = ench.getName().toUpperCase();
            double percent = lookupEnchantPercent(name, lvl);
            total += percent;
        }
        return total;
    }

    private double lookupEnchantPercent(String name, int level) {
        Map<Integer, Double> map = enchantTable.get(name);
        if (map != null && !map.isEmpty()) {
            for (int l = level; l >= 1; l--) {
                if (map.containsKey(l)) return map.get(l);
            }
        }
        return defaultPerLevel * level;
    }
}