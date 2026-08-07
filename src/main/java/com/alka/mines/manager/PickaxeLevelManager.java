package com.alka.mines.manager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.TreeMap;

/** Thresholds (blocos minerados -> nivel de picareta) configuraveis via config.yml
 * (secao pickaxe-levels) - usado por {@link PlayerMineData#recalculateLevel}. */
public class PickaxeLevelManager {

    private final JavaPlugin plugin;
    private final TreeMap<Integer, Long> thresholds = new TreeMap<>();

    public PickaxeLevelManager(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void reload() {
        thresholds.clear();
        load();
    }

    private void load() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("pickaxe-levels");
        if (section == null) {
            thresholds.put(0, 0L);
            thresholds.put(1, 1000L);
            thresholds.put(2, 5000L);
            thresholds.put(3, 25000L);
            thresholds.put(4, 100000L);
            thresholds.put(5, 500000L);
            thresholds.put(6, 2000000L);
            thresholds.put(7, 5000000L);
            thresholds.put(8, 10000000L);
            thresholds.put(9, 20000000L);
            plugin.getLogger().info("pickaxe-levels nao encontrado no config.yml - usando padroes.");
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                int level = Integer.parseInt(key);
                long blocks = section.getLong(key);
                thresholds.put(level, blocks);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Nivel invalido em pickaxe-levels: " + key);
            }
        }

        thresholds.putIfAbsent(0, 0L);
    }

    public TreeMap<Integer, Long> getThresholds() {
        return thresholds;
    }

    /** Blocos exigidos pro proximo nivel a partir de currentLevel, ou -1 se ja esta no maximo. */
    public long getBlocksForNextLevel(int currentLevel) {
        Map.Entry<Integer, Long> next = thresholds.higherEntry(currentLevel);
        return next != null ? next.getValue() : -1;
    }

    public int getMaxLevel() {
        return thresholds.lastKey();
    }
}
