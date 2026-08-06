package com.alka.mines.manager;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado transiente por jogador (mina atual, blocos quebrados, nivel de picareta,
 * bonus de coins) + a localizacao global de "saida" configurada por um admin via
 * /minaadmin setsaida. So em memoria - reseta a cada restart do servidor.
 */
public class PlayerDataManager {

    private final Map<UUID, PlayerMineData> data = new ConcurrentHashMap<>();
    private Location exitLocation;

    public PlayerMineData get(UUID uuid) {
        return data.computeIfAbsent(uuid, k -> new PlayerMineData());
    }

    public void remove(UUID uuid) {
        data.remove(uuid);
    }

    public Location getExitLocation() {
        return exitLocation;
    }

    public void setExitLocation(Location exitLocation) {
        this.exitLocation = exitLocation;
    }
}
