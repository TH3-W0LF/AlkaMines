package com.alka.mines.manager;

import com.alka.mines.event.MineCreateEvent;
import com.alka.mines.event.MineDeleteEvent;
import com.alka.mines.model.Mine;
import com.alka.mines.model.MineBlock;
import com.alka.mines.model.MineRegion;
import com.alka.mines.model.MineReward;
import com.alka.mines.model.MineSettings;
import com.alka.mines.util.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Cache em memoria de todas as minas + persistencia em mines.yml. Instanciado uma vez
 * no onEnable e injetado em todo mundo que precisa dele - a instancia estatica existe
 * so como atalho de conveniencia (ex: hooks/tarefas que nao tem o plugin a mao).
 */
public class MineManager {

    private static MineManager instance;

    private final JavaPlugin plugin;
    private final Map<String, Mine> mines = new LinkedHashMap<>();
    private final Map<Long, List<Mine>> chunkIndex = new HashMap<>();
    private final Set<String> dirtyIds = new HashSet<>();
    private final File file;
    private static final List<Mine> NO_MINES = List.of();

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
        indexMine(mine);
        save();
        DebugLogger.log("Mina '%s' criada: regiao %dx%dx%d, volume=%d.",
                id,
                region.getX2() - region.getX1() + 1,
                region.getY2() - region.getY1() + 1,
                region.getZ2() - region.getZ1() + 1,
                region.getVolume());
        Bukkit.getPluginManager().callEvent(new MineCreateEvent(mine));
        return mine;
    }

    public boolean deleteMine(String id) {
        Mine removed = mines.remove(id.toLowerCase());
        if (removed != null) {
            unindexMine(removed);
            dirtyIds.remove(id.toLowerCase());
            save();
            DebugLogger.log("Mina '%s' deletada.", id);
            Bukkit.getPluginManager().callEvent(new MineDeleteEvent(removed));
        }
        return removed != null;
    }

    public Optional<Mine> getMine(String id) {
        return Optional.ofNullable(mines.get(id.toLowerCase()));
    }

    /** Area de MINERACAO (blocos quebraveis) - usado por MineBreakListener/MineProtectionListener.
     * Busca via indice por chunk (chunkIndex) em vez de varrer todas as minas: O(1) por chunk
     * (que costuma conter 0-1 minas) em vez de O(n) a cada quebra de bloco. */
    public Optional<Mine> getMineAt(Location location) {
        for (Mine mine : chunkIndex.getOrDefault(chunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4), NO_MINES)) {
            if (mine.containsMining(location)) {
                return Optional.of(mine);
            }
        }
        return Optional.empty();
    }

    /** Area da MINA como um todo (lobby/dungeon, cai pra regiao de mineracao se nao
     * configurado) - usado por tracking de placeholder e protecao de comando.
     * Mesma busca por chunk do {@link #getMineAt}. */
    public Optional<Mine> getMineLobbyAt(Location location) {
        for (Mine mine : chunkIndex.getOrDefault(chunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4), NO_MINES)) {
            if (mine.containsLobby(location)) {
                return Optional.of(mine);
            }
        }
        return Optional.empty();
    }

    public Collection<Mine> getMines() {
        return mines.values();
    }

    /** Numero de chunks indexados (sanity check pra debug - deve crescer com as minas). */
    public int getIndexedChunkCount() {
        return chunkIndex.size();
    }

    /** Limite de tamanho de mina (area X*Z) do config.yml, ou 0 se nao ha limite. */
    public int getMaxMineSize() {
        return plugin.getConfig().getInt("max-mine-size", 0);
    }

    /** Area X*Z da regiao respeita o max-mine-size do config? (0 = sem limite). */
    public boolean isWithinSizeLimit(MineRegion region) {
        int max = getMaxMineSize();
        if (max <= 0) {
            return true;
        }
        long area = (long) (region.getX2() - region.getX1() + 1) * (region.getZ2() - region.getZ1() + 1);
        return area <= max;
    }

    /** Indice espacial por chunk (chunkKey = (x>>4)<<32 | (z>>4)): cada mina registra os chunks
     * da regiao de mineracao E do lobbyRegion (se houver), entao o lookup por posicao nunca
     * varre o mapa inteiro de minas. Reconstruido no load(); mantido via indexMine/unindexMine
     * em create/delete e reindexMine quando uma regiao muda. */
    public void rebuildIndex() {
        chunkIndex.clear();
        for (Mine mine : mines.values()) {
            indexMine(mine);
        }
    }

    /** Re-indexa uma mina cuja regiao mudou (ex: /alkamines setlobby / removelobby) - remove
     * os chunks antigos e registra os atuais. */
    public void reindexMine(Mine mine) {
        unindexMine(mine);
        indexMine(mine);
    }

    private void indexMine(Mine mine) {
        indexRegion(mine, mine.getRegion());
        if (mine.getLobbyRegion() != null) {
            indexRegion(mine, mine.getLobbyRegion());
        }
    }

    private void indexRegion(Mine mine, MineRegion region) {
        for (int cx = region.getX1() >> 4; cx <= region.getX2() >> 4; cx++) {
            for (int cz = region.getZ1() >> 4; cz <= region.getZ2() >> 4; cz++) {
                chunkIndex.computeIfAbsent(chunkKey(cx, cz), k -> new ArrayList<>()).add(mine);
            }
        }
    }

    private void unindexMine(Mine mine) {
        unindexRegion(mine, mine.getRegion());
        if (mine.getLobbyRegion() != null) {
            unindexRegion(mine, mine.getLobbyRegion());
        }
    }

    private void unindexRegion(Mine mine, MineRegion region) {
        for (int cx = region.getX1() >> 4; cx <= region.getX2() >> 4; cx++) {
            for (int cz = region.getZ1() >> 4; cz <= region.getZ2() >> 4; cz++) {
                long key = chunkKey(cx, cz);
                List<Mine> minesInChunk = chunkIndex.get(key);
                if (minesInChunk != null) {
                    minesInChunk.remove(mine);
                    if (minesInChunk.isEmpty()) {
                        chunkIndex.remove(key);
                    }
                }
            }
        }
    }

    private long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection root = config.createSection("mines");

        for (Mine mine : mines.values()) {
            saveMine(root.createSection(mine.getId()), mine);
        }

        dirtyIds.clear();
        saveConfig(config);
    }

    /** Persiste apenas as minas marcadas como dirty (quebra de bloco, reset, ajustes) em vez
     * de reescrever o arquivo inteiro - o save() completo so roda em acoes explicitas do admin,
     * no onDisable e no autosave periodico (ver AlkaMines#onEnable). Le o arquivo existente
     * primeiro (mesma regra do PlayerDataManager#save) senao secoes nao-dirty seriam perdidas. */
    public void saveDirty() {
        if (dirtyIds.isEmpty()) {
            return;
        }

        DebugLogger.log("saveDirty: persistindo %d mina(s) dirty.", dirtyIds.size());

        YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        for (String id : dirtyIds) {
            Mine mine = mines.get(id);
            if (mine == null) {
                continue;
            }
            config.set("mines." + id, null);
            saveMine(config.createSection("mines." + id), mine);
        }
        dirtyIds.clear();
        saveConfig(config);
    }

    /** Marca uma mina como alterada em memoria - o disco so recebe a mudanca no proximo
     * saveDirty periodico, num save() explicito ou no onDisable. */
    public void markDirty(String id) {
        dirtyIds.add(id.toLowerCase());
    }

    private void saveConfig(YamlConfiguration config) {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao salvar mines.yml", e);
        }
    }

    private void saveMine(ConfigurationSection section, Mine mine) {
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
        section.set("icon-itemsadder", mine.getIconItemsAdder());

        MineSettings settings = mine.getSettings();
        ConfigurationSection settingsSection = section.createSection("settings");
        settingsSection.set("reset-interval-minutes", settings.getResetIntervalMinutes());
        settingsSection.set("reset-percentage", settings.getResetPercentage());
        settingsSection.set("invisible-players", settings.isInvisiblePlayers());
        settingsSection.set("min-pickaxe-level", settings.getMinPickaxeLevel());
        settingsSection.set("permission", settings.getPermission());
        settingsSection.set("actionbar-enabled", settings.isActionbarEnabled());
        settingsSection.set("actionbar-range", settings.getActionbarRange());
        settingsSection.set("broadcast-mode", settings.getBroadcastMode());
        settingsSection.set("reset-commands", settings.getResetCommands());

        section.set("last-reset", mine.getLastReset());
        section.set("blocks-remaining", mine.getBlocksRemaining());

        List<Map<String, Object>> compositionList = new ArrayList<>();
        for (MineBlock block : mine.getComposition()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("material", block.getMaterial().name());
            entry.put("weight", block.getWeight());
            entry.put("xp-normal", block.getNormalXp());
            // so grava xp-mcmmo quando foi configurado na mina - sem a chave, o bloco
            // cai pra tabela global no load (McMMOHook#addMiningXp).
            if (block.isMcmmoXpConfigured()) {
                entry.put("xp-mcmmo", block.getMcmmoXp());
            }
            compositionList.add(entry);
        }
        section.set("composition", compositionList);

        List<Map<String, Object>> rewardList = new ArrayList<>();
        for (MineReward reward : mine.getRewards()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("chance", reward.getChance());
            entry.put("prevent-drops", reward.isPreventDrops());
            entry.put("blocks", reward.getBlocks().stream().map(Material::name).collect(Collectors.toList()));
            entry.put("items", reward.getItems().stream().map(ItemStack::serialize).collect(Collectors.toList()));
            entry.put("commands", reward.getCommands());
            rewardList.add(entry);
        }
        section.set("rewards", rewardList);
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
        chunkIndex.clear();

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
            mine.setIconItemsAdder(section.getString("icon-itemsadder"));

            ConfigurationSection settingsSection = section.getConfigurationSection("settings");
            if (settingsSection != null) {
                MineSettings settings = new MineSettings(
                        settingsSection.getInt("reset-interval-minutes", 0),
                        settingsSection.getDouble("reset-percentage", 40.0),
                        settingsSection.getBoolean("invisible-players", false),
                        settingsSection.getInt("min-pickaxe-level", 0),
                        settingsSection.getString("permission", ""));
                settings.setActionbarEnabled(settingsSection.getBoolean("actionbar-enabled", false));
                settings.setActionbarRange(settingsSection.getInt("actionbar-range", 10));
                settings.setBroadcastMode(settingsSection.getInt("broadcast-mode", 0));
                settings.setResetCommands(settingsSection.getStringList("reset-commands"));
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

                MineBlock mineBlock = new MineBlock(material, weight);
                mineBlock.setNormalXp(entry.get("xp-normal") instanceof Number number ? number.doubleValue() : 0.0);
                if (entry.containsKey("xp-mcmmo")) {
                    mineBlock.setMcmmoXp(entry.get("xp-mcmmo") instanceof Number number ? number.doubleValue() : 0.0);
                    mineBlock.setMcmmoXpConfigured(true);
                }
                composition.add(mineBlock);
            }
            mine.setComposition(composition);

            List<MineReward> rewards = new ArrayList<>();
            for (Map<?, ?> entry : section.getMapList("rewards")) {
                MineReward reward = new MineReward();
                reward.setChance(entry.get("chance") instanceof Number number ? number.doubleValue() : 0.0);
                reward.setPreventDrops(entry.get("prevent-drops") instanceof Boolean bool && bool);

                List<Material> rewardBlocks = new ArrayList<>();
                if (entry.get("blocks") instanceof List<?> blocks) {
                    for (Object b : blocks) {
                        Material mat = Material.matchMaterial(String.valueOf(b));
                        if (mat != null) {
                            rewardBlocks.add(mat);
                        }
                    }
                }
                reward.setBlocks(rewardBlocks);

                List<ItemStack> rewardItems = new ArrayList<>();
                if (entry.get("items") instanceof List<?> items) {
                    for (Object item : items) {
                        if (item instanceof Map<?, ?> itemMap) {
                            try {
                                rewardItems.add(ItemStack.deserialize(itemMap.entrySet().stream()
                                        .collect(Collectors.toMap(
                                                e -> String.valueOf(e.getKey()),
                                                Map.Entry::getValue))));
                            } catch (Exception e) {
                                plugin.getLogger().warning("Item de recompensa invalido em mines.yml "
                                        + "(mina '" + id + "'): " + e.getMessage());
                            }
                        }
                    }
                }
                reward.setItems(rewardItems);

                if (entry.get("commands") instanceof List<?> commands) {
                    reward.setCommands(commands.stream().map(String::valueOf).collect(Collectors.toList()));
                }

                rewards.add(reward);
            }
            mine.setRewards(rewards);

            mines.put(id.toLowerCase(), mine);

            DebugLogger.log("Mina '%s' carregada: regiao %dx%dx%d, volume=%d, composicao=%d bloco(s).",
                    id,
                    region.getX2() - region.getX1() + 1,
                    region.getY2() - region.getY1() + 1,
                    region.getZ2() - region.getZ1() + 1,
                    region.getVolume(),
                    composition.size());
        }

        rebuildIndex();
        DebugLogger.log("Indexacao concluida: %d mina(s), %d chunk(s) indexados.", mines.size(), chunkIndex.size());
    }
}
