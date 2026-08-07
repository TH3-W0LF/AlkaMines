package com.alka.mines.manager;

import com.alka.mines.model.Mine;
import com.alka.mines.model.MineBlock;
import com.alka.mines.model.MineRegion;
import com.alka.mines.model.MineSettings;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Cache em memoria de todas as minas + persistencia em mines.yml. Instanciado uma vez
 * no onEnable e injetado em todo mundo que precisa dele - a instancia estatica existe
 * so como atalho de conveniencia (ex: hooks/tarefas que nao tem o plugin a mao).
 */
public class MineManager {

    private static MineManager instance;

    private final JavaPlugin plugin;
    private final Map<String, Mine> mines = new LinkedHashMap<>();
    private final File file;

    public MineManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mines.yml");
        instance = this;
    }

    public static MineManager getInstance() {
        return instance;
    }

    public Mine createMine(String id, MineRegion region) {
        Mine mine = new Mine(id, id, region);
        mines.put(id.toLowerCase(), mine);
        save();
        return mine;
    }

    public boolean deleteMine(String id) {
        boolean removed = mines.remove(id.toLowerCase()) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public Optional<Mine> getMine(String id) {
        return Optional.ofNullable(mines.get(id.toLowerCase()));
    }

    /** Area de MINERACAO (blocos quebraveis) - usado por MineBreakListener/MineProtectionListener. */
    public Optional<Mine> getMineAt(Location location) {
        for (Mine mine : mines.values()) {
            if (mine.containsMining(location)) {
                return Optional.of(mine);
            }
        }
        return Optional.empty();
    }

    /** Area da MINA como um todo (lobby/dungeon, cai pra regiao de mineracao se nao
     * configurado) - usado por tracking de placeholder e protecao de comando. */
    public Optional<Mine> getMineLobbyAt(Location location) {
        for (Mine mine : mines.values()) {
            if (mine.containsLobby(location)) {
                return Optional.of(mine);
            }
        }
        return Optional.empty();
    }

    public Collection<Mine> getMines() {
        return mines.values();
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection root = config.createSection("mines");

        for (Mine mine : mines.values()) {
            ConfigurationSection section = root.createSection(mine.getId());
            section.set("display-name", mine.getDisplayName());

            saveRegion(section.createSection("region"), mine.getRegion());
            if (mine.getLobbyRegion() != null) {
                saveRegion(section.createSection("lobby-region"), mine.getLobbyRegion());
            }

            section.set("spawn", mine.getSpawn());
            section.set("exit", mine.getExit());
            section.set("hologram", mine.getHologramLocation());
            section.set("category", mine.getCategory());
            section.set("icon", mine.getIcon() != null ? mine.getIcon().name() : null);

            MineSettings settings = mine.getSettings();
            ConfigurationSection settingsSection = section.createSection("settings");
            settingsSection.set("reset-interval-minutes", settings.getResetIntervalMinutes());
            settingsSection.set("reset-percentage", settings.getResetPercentage());
            settingsSection.set("invisible-players", settings.isInvisiblePlayers());
            settingsSection.set("min-pickaxe-level", settings.getMinPickaxeLevel());
            settingsSection.set("permission", settings.getPermission());

            section.set("last-reset", mine.getLastReset());
            section.set("blocks-remaining", mine.getBlocksRemaining());

            List<Map<String, Object>> compositionList = new ArrayList<>();
            for (MineBlock block : mine.getComposition()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("material", block.getMaterial().name());
                entry.put("weight", block.getWeight());
                compositionList.add(entry);
            }
            section.set("composition", compositionList);
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao salvar mines.yml", e);
        }
    }

    private void saveRegion(ConfigurationSection section, MineRegion region) {
        section.set("world", region.getWorld());
        section.set("x1", region.getX1());
        section.set("y1", region.getY1());
        section.set("z1", region.getZ1());
        section.set("x2", region.getX2());
        section.set("y2", region.getY2());
        section.set("z2", region.getZ2());
    }

    private MineRegion loadRegion(ConfigurationSection section) {
        return new MineRegion(
                section.getString("world", "world"),
                section.getInt("x1"), section.getInt("y1"), section.getInt("z1"),
                section.getInt("x2"), section.getInt("y2"), section.getInt("z2"));
    }

    public void load() {
        mines.clear();

        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("mines");
        if (root == null) {
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }

            ConfigurationSection regionSection = section.getConfigurationSection("region");
            if (regionSection == null) {
                plugin.getLogger().warning("Mina '" + id + "' sem regiao valida - ignorada.");
                continue;
            }

            MineRegion region = loadRegion(regionSection);
            Mine mine = new Mine(id, section.getString("display-name", id), region);

            ConfigurationSection lobbySection = section.getConfigurationSection("lobby-region");
            if (lobbySection != null) {
                mine.setLobbyRegion(loadRegion(lobbySection));
            }

            Location spawn = section.getLocation("spawn");
            if (spawn != null) {
                mine.setSpawn(spawn);
            }
            mine.setExit(section.getLocation("exit"));
            mine.setHologramLocation(section.getLocation("hologram"));
            mine.setCategory(section.getString("category", "geral"));

            String iconName = section.getString("icon");
            if (iconName != null) {
                mine.setIcon(Material.matchMaterial(iconName));
            }

            ConfigurationSection settingsSection = section.getConfigurationSection("settings");
            if (settingsSection != null) {
                MineSettings settings = new MineSettings(
                        settingsSection.getInt("reset-interval-minutes", 0),
                        settingsSection.getDouble("reset-percentage", 40.0),
                        settingsSection.getBoolean("invisible-players", false),
                        settingsSection.getInt("min-pickaxe-level", 0),
                        settingsSection.getString("permission", ""));
                mine.setSettings(settings);
            }

            mine.setLastReset(section.getLong("last-reset", System.currentTimeMillis()));
            mine.setBlocksRemaining(section.getInt("blocks-remaining", (int) Math.min(region.getVolume(), Integer.MAX_VALUE)));

            List<MineBlock> composition = new ArrayList<>();
            for (Map<?, ?> entry : section.getMapList("composition")) {
                Material material = Material.matchMaterial(String.valueOf(entry.get("material")));
                if (material == null) {
                    continue;
                }
                double weight = entry.get("weight") instanceof Number number ? number.doubleValue() : 0.0;
                composition.add(new MineBlock(material, weight));
            }
            mine.setComposition(composition);

            mines.put(id.toLowerCase(), mine);
        }
    }
}
