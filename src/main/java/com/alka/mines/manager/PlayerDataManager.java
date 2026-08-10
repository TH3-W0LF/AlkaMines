package com.alka.mines.manager;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Estado por jogador (mina atual, blocos quebrados, nivel de picareta, bonus de coins)
 * + a localizacao global de "saida" configurada por um admin via /minaadmin setsaida.
 * Persistido em players.yml - carregado uma vez no onEnable, salvo no onDisable E
 * periodicamente (ver AlkaMines#onEnable, runTaskTimer) pra sobreviver a um crash sem
 * onDisable "limpo", ja que blocksBroken/pickaxeLevel sao progresso valioso do
 * jogador (nao algo facil de recalcular do zero como blocksRemaining de uma mina).
 */
public class PlayerDataManager {

    private final Map<UUID, PlayerMineData> data = new ConcurrentHashMap<>();
    private final JavaPlugin plugin;
    private final File file;
    private Location exitLocation;

    public PlayerDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        load();
    }

    public PlayerMineData get(UUID uuid) {
        return data.computeIfAbsent(uuid, k -> new PlayerMineData());
    }

    public void remove(UUID uuid) {
        data.remove(uuid);
    }

    /** Top jogadores por blocos quebrados (total, todas as minas), ordem decrescente. */
    public List<Map.Entry<UUID, PlayerMineData>> getTopBlocksBroken(int limit) {
        return data.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<UUID, PlayerMineData> e) -> e.getValue().getBlocksBroken()).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public Location getExitLocation() {
        return exitLocation;
    }

    public void setExitLocation(Location exitLocation) {
        this.exitLocation = exitLocation;
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();

        if (exitLocation != null) {
            config.set("exit-location", exitLocation);
        }

        for (Map.Entry<UUID, PlayerMineData> entry : data.entrySet()) {
            String path = "players." + entry.getKey();
            PlayerMineData playerData = entry.getValue();
            config.set(path + ".blocks-broken", playerData.getBlocksBroken());
            config.set(path + ".pickaxe-level", playerData.getPickaxeLevel());
            config.set(path + ".coin-bonus", playerData.getCoinBonus());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao salvar players.yml", e);
        }
    }

    public void load() {
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        exitLocation = config.getLocation("exit-location");

        if (!config.isConfigurationSection("players")) {
            return;
        }

        for (String uuidStr : config.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                String path = "players." + uuidStr;
                PlayerMineData playerData = new PlayerMineData();
                playerData.setBlocksBroken(config.getLong(path + ".blocks-broken", 0));
                playerData.setPickaxeLevel(config.getInt(path + ".pickaxe-level", 0));
                playerData.setCoinBonus(config.getDouble(path + ".coin-bonus", 0.0));
                data.put(uuid, playerData);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID invalido em players.yml: " + uuidStr);
            }
        }
    }
}
