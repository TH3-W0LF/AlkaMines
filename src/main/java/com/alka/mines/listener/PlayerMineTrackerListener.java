package com.alka.mines.listener;

import com.alka.mines.manager.MineManager;
import com.alka.mines.manager.PlayerDataManager;
import com.alka.mines.manager.PlayerMineData;
import com.alka.mines.model.Mine;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Objects;
import java.util.Optional;

/**
 * Atualiza PlayerMineData.currentMineId quando o jogador entra/sai da area de uma
 * mina - usado pela scoreboard e pelos placeholders (%alkaminas_mina%). So reage quando
 * o jogador muda de bloco (nao a cada micro-movimento da camera/subpixel).
 *
 * Fora da regiao exata, ainda conta como "na mina" se estiver a ate 10 blocos do spawn
 * configurado (Mine#getSpawn()) - um admin costuma colocar o spawn numa plataforma de
 * entrada FORA da selecao do WorldEdit, e sem esse fallback o currentMineId oscilava
 * pra null a cada passo fora da regiao exata, mesmo o jogador ainda estando na "area"
 * visual da mina.
 *
 * onTeleport existe a parte de onMove porque nem todo teleport necessariamente dispara
 * um PlayerMoveEvent no mesmo tick (varia entre versoes/implementacoes) - comandos
 * proprios (PlayerCommands, MineListMenu) ja forcam o valor certo na hora, mas um /tp de
 * outro plugin ou de um admin passa batido sem isso.
 */
public class PlayerMineTrackerListener implements Listener {

    private static final double SPAWN_RADIUS_SQ = 100.0; // 10 blocos

    private final MineManager mineManager;
    private final PlayerDataManager playerDataManager;

    public PlayerMineTrackerListener(MineManager mineManager, PlayerDataManager playerDataManager) {
        this.mineManager = mineManager;
        this.playerDataManager = playerDataManager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        update(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        update(event.getPlayer(), event.getTo());
    }

    private void update(Player player, Location to) {
        PlayerMineData data = playerDataManager.get(player.getUniqueId());
        Optional<Mine> regionMine = mineManager.getMineAt(to);

        String newMineId;
        if (regionMine.isPresent()) {
            newMineId = regionMine.get().getId();
        } else {
            Mine currentMine = data.getCurrentMineId() != null
                    ? mineManager.getMine(data.getCurrentMineId()).orElse(null)
                    : null;
            newMineId = isNearSpawn(currentMine, to) ? currentMine.getId() : null;
        }

        if (!Objects.equals(data.getCurrentMineId(), newMineId)) {
            data.setCurrentMineId(newMineId);
        }
    }

    private boolean isNearSpawn(Mine mine, Location loc) {
        if (mine == null || mine.getSpawn() == null || mine.getSpawn().getWorld() == null || loc.getWorld() == null) {
            return false;
        }
        if (!mine.getSpawn().getWorld().equals(loc.getWorld())) {
            return false;
        }
        return mine.getSpawn().distanceSquared(loc) <= SPAWN_RADIUS_SQ;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        playerDataManager.remove(event.getPlayer().getUniqueId());
    }
}
