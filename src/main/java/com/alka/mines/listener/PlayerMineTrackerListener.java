package com.alka.mines.listener;

import com.alka.mines.manager.MineManager;
import com.alka.mines.manager.PlayerDataManager;
import com.alka.mines.manager.PlayerMineData;
import com.alka.mines.model.Mine;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Optional;

/**
 * Atualiza PlayerMineData.currentMineId quando o jogador entra/sai da area de uma
 * mina - usado pela scoreboard e pelos placeholders (%alkaminas_mina%). So reage quando
 * o jogador muda de bloco (nao a cada micro-movimento da camera/subpixel).
 */
public class PlayerMineTrackerListener implements Listener {

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

        Player player = event.getPlayer();
        PlayerMineData data = playerDataManager.get(player.getUniqueId());
        Optional<Mine> mine = mineManager.getMineAt(event.getTo());
        data.setCurrentMineId(mine.map(Mine::getId).orElse(null));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        playerDataManager.remove(event.getPlayer().getUniqueId());
    }
}
