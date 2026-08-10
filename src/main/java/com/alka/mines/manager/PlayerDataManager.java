package com.alka.mines.manager;

import org.bukkit.Bukkit;
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

    /**
     * Recarrega os dados salvos de UM jogador do disco pra memoria, se ainda nao
     * estiverem la - chamado no join. Sem isso, {@link #get(UUID)} criava um
     * PlayerMineData vazio (computeIfAbsent) pra qualquer jogador ausente do cache,
     * inclusive um que tinha acabado de deslogar (saveAndRemove tira do cache no
     * quit): o progresso ficava salvo certinho em players.yml, mas o jogador via o
     * proprio contador (blocksBroken/ranking) resetar pra 0 ao relogar, porque nada
     * recarregava esse valor de volta pra memoria - so o load() do boot inicial.
     */
    public void loadForJoin(UUID uuid) {
        if (data.containsKey(uuid) || !file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String path = "players." + uuid;
        if (!config.isConfigurationSection(path)) {
            return;
        }
        PlayerMineData playerData = new PlayerMineData();
        playerData.setBlocksBroken(config.getLong(path + ".blocks-broken", 0));
        playerData.setPickaxeLevel(config.getInt(path + ".pickaxe-level", 0));
        playerData.setCoinBonus(config.getDouble(path + ".coin-bonus", 0.0));
        data.put(uuid, playerData);
    }

    public void remove(UUID uuid) {
        data.remove(uuid);
    }

    /**
     * Persiste tudo que estiver em memoria (ver save()) e SO DEPOIS remove o jogador
     * do cache - roda fora da main thread. Sem isso, o progresso feito entre o ultimo
     * autosave periodico (a cada 5 min) e o quit era perdido: remove(uuid) sozinho no
     * quit tirava o jogador da memoria sem nunca escrever esse progresso em disco.
     */
    public void saveAndRemove(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            save();
            data.remove(uuid);
        });
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

    /**
     * Carrega o arquivo em disco ANTES de escrever - senao cada save() sobrescrevia
     * players.yml do zero so com quem estava em memoria no momento (jogadores online +
     * ainda nao removidos pelo quit), apagando pra sempre o progresso de qualquer
     * jogador offline que ja tinha sido salvo antes (bug real: todo mundo perdia o
     * progresso no proximo autosave/onDisable depois de deslogar).
     */
    public void save() {
        YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();

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
