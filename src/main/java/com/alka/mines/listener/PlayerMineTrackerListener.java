package com.alka.mines.listener;

import com.alka.mines.event.MineEnterEvent;
import com.alka.mines.event.MineLeaveEvent;
import com.alka.mines.manager.MineManager;
import com.alka.mines.manager.PlayerDataManager;
import com.alka.mines.manager.PlayerMineData;
import com.alka.mines.manager.PrivateMineManager;
import com.alka.mines.model.Mine;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
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
 * Fonte e sempre Mine#containsLobby (MineManager#getMineLobbyAt) - respeita o
 * lobbyRegion opcional definido via /alkamines setlobby (ou a propria regiao de
 * mineracao como fallback), e ambos ignoram Y (MineRegion#containsIgnoreY): uma
 * plataforma de entrada em outra altura, mesma coluna X/Z, ja conta como "na mina" sem
 * precisar de nenhuma heuristica de distancia.
 *
 * onTeleport existe a parte de onMove porque nem todo teleport necessariamente dispara
 * um PlayerMoveEvent no mesmo tick (varia entre versoes/implementacoes) - comandos
 * proprios (PlayerCommands, MineListMenu) ja forcam o valor certo na hora, mas um /tp de
 * outro plugin ou de um admin passa batido sem isso.
 */
public class PlayerMineTrackerListener implements Listener {

    private final MineManager mineManager;
    private final PlayerDataManager playerDataManager;
    private final PrivateMineManager privateMineManager;

    public PlayerMineTrackerListener(MineManager mineManager, PlayerDataManager playerDataManager,
                                     PrivateMineManager privateMineManager) {
        this.mineManager = mineManager;
        this.playerDataManager = playerDataManager;
        this.privateMineManager = privateMineManager;
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
        Optional<Mine> lobbyMine = mineManager.getMineLobbyAt(to);

        // mina particular: currentMineId = "privado" (placeholder mostra o status via
        // PrivateMineManager; sem MineEnter/Leave por enquanto).
        String newMineId = lobbyMine.map(Mine::getId)
                .orElseGet(() -> privateMineManager.getMineProtectingAt(to).isPresent() ? "privado" : null);

        String oldMineId = data.getCurrentMineId();
        if (!Objects.equals(oldMineId, newMineId)) {
            data.setCurrentMineId(newMineId);

            // API: eventos de entrada/saida de mina (so minas publicas).
            if (newMineId != null && lobbyMine.isPresent()) {
                Bukkit.getPluginManager().callEvent(new MineEnterEvent(player, lobbyMine.get()));
            } else if (oldMineId != null && !oldMineId.equals("privado")) {
                mineManager.getMine(oldMineId)
                        .ifPresent(mine -> Bukkit.getPluginManager().callEvent(new MineLeaveEvent(player, mine)));
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        playerDataManager.loadForJoin(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        playerDataManager.saveAndRemove(event.getPlayer().getUniqueId());
    }
}
