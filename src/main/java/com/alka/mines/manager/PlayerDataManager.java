package com.alka.mines.manager;

import com.alkacode.core.api.DatabaseProvider;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Estado por jogador (mina atual, blocos quebrados, nivel de picareta, bonus de gold)
 * + a localizacao global de "saida" configurada por um admin via /alkamines setsaida.
 *
 * Persistencia no banco do AlkaCore ({@link PlayerMineRepository}) - substitui o
 * players.yml. Jogadores carregam sob demanda (join / primeiro get), o ranking vem do
 * banco mesclado com o cache (online tem valores mais recentes que o ultimo autosave),
 * e o progresso e salvo no quit + periodicamente (autosave na main thread).
 */
public class PlayerDataManager {

    private final Map<UUID, PlayerMineData> data = new ConcurrentHashMap<>();
    private final JavaPlugin plugin;
    private final PlayerMineRepository repository;
    private Location exitLocation;

    public PlayerDataManager(JavaPlugin plugin, DatabaseProvider db) {
        this.plugin = plugin;
        this.repository = new PlayerMineRepository(db);
        load();
    }

    public PlayerMineData get(UUID uuid) {
        return data.computeIfAbsent(uuid, k -> {
            PlayerMineData loaded = repository.load(uuid);
            return loaded != null ? loaded : new PlayerMineData();
        });
    }

    /** Recarrega do banco se ainda nao estiver em memoria - chamado no join. */
    public void loadForJoin(UUID uuid) {
        if (data.containsKey(uuid)) {
            return;
        }
        PlayerMineData loaded = repository.load(uuid);
        if (loaded != null) {
            data.put(uuid, loaded);
        }
    }

    public void remove(UUID uuid) {
        data.remove(uuid);
    }

    /** Salva o progresso de UM jogador (upsert no banco) e remove do cache - main thread,
     * chamado no quit. Antes era async + salvava todos; com banco, salvar so o jogador e
     * barato e evita corrida com o pool de 1 conexao. */
    public void saveAndRemove(UUID uuid) {
        PlayerMineData playerData = data.remove(uuid);
        if (playerData != null) {
            repository.save(uuid, playerData);
        }
    }

    /** Top jogadores por blocos quebrados: banco (top 3x) mesclado com o cache de quem
     * esta online (valores mais recentes), ordem decrescente. */
    public List<Map.Entry<UUID, PlayerMineData>> getTopBlocksBroken(int limit) {
        Map<UUID, PlayerMineData> merged = new HashMap<>();
        for (Map.Entry<UUID, PlayerMineData> entry : repository.getTop(limit * 3)) {
            merged.put(entry.getKey(), entry.getValue());
        }
        merged.putAll(data);
        return merged.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<UUID, PlayerMineData> e) -> e.getValue().getBlocksBroken()).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public Location getExitLocation() {
        return exitLocation;
    }

    public void setExitLocation(Location exitLocation) {
        this.exitLocation = exitLocation;
        repository.setSetting("exit-location", serializeLocation(exitLocation));
    }

    /** Persiste tudo que estiver em memoria (upserts individuais) - autosave periodico e onDisable. */
    public void save() {
        for (Map.Entry<UUID, PlayerMineData> entry : data.entrySet()) {
            repository.save(entry.getKey(), entry.getValue());
        }
    }

    /** Carrega a saida configurada + migra o players.yml legado (uma unica vez). */
    public void load() {
        migrateLegacyPlayersYml();
        String exit = repository.getSetting("exit-location");
        if (exit != null) {
            exitLocation = deserializeLocation(exit);
        }
    }

    /** Migracao unica de players.yml -> banco do AlkaCore. Preserva progresso antigo
     * (ranking) e a saida configurada; o arquivo vira players.yml.migrated (backup). */
    private void migrateLegacyPlayersYml() {
        File legacy = new File(plugin.getDataFolder(), "players.yml");
        if (!legacy.exists() || repository.getSetting("migrated") != null) {
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(legacy);
            if (config.isSet("exit-location")) {
                repository.setSetting("exit-location", serializeLocation(config.getLocation("exit-location")));
            }
            if (config.isConfigurationSection("players")) {
                for (String uuidStr : config.getConfigurationSection("players").getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        String path = "players." + uuidStr;
                        PlayerMineData playerData = new PlayerMineData();
                        playerData.setBlocksBroken(config.getLong(path + ".blocks-broken", 0));
                        playerData.setPickaxeLevel(config.getInt(path + ".pickaxe-level", 0));
                        playerData.setCoinBonus(config.getDouble(path + ".coin-bonus", 0.0));
                        repository.save(uuid, playerData);
                    } catch (IllegalArgumentException ignored) {
                        plugin.getLogger().warning("UUID invalido em players.yml: " + uuidStr);
                    }
                }
            }
            repository.setSetting("migrated", "true");
            File backup = new File(plugin.getDataFolder(), "players.yml.migrated");
            if (legacy.renameTo(backup)) {
                plugin.getLogger().info("players.yml migrado para o banco do AlkaCore (backup em players.yml.migrated).");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao migrar players.yml - mantendo arquivo.", e);
        }
    }

    private String serializeLocation(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getX() + ";" + loc.getY() + ";" + loc.getZ()
                + ";" + loc.getYaw() + ";" + loc.getPitch();
    }

    private Location deserializeLocation(String serialized) {
        if (serialized == null) {
            return null;
        }
        String[] parts = serialized.split(";");
        if (parts.length != 6) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(world,
                    Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                    Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
