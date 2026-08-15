package com.alka.mines.service;

import com.alka.mines.event.MinePreResetEvent;
import com.alka.mines.event.MineResetEvent;
import com.alka.mines.hook.BlockFillHook;
import com.alka.mines.manager.MineManager;
import com.alka.mines.model.Mine;
import com.alka.mines.util.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orquestra o reset de uma mina: teleporta jogadores e preenche a regiao com o
 * BlockFillHook (escrita direta via Bukkit API, sincrona, sem FAWE - ver o javadoc de
 * BlockFillHook pro motivo). Tudo dentro de uma unica chamada sincrona na main thread -
 * sem fila assincrona, sem callback aninhado, sem janela de tempo pra race condition.
 */
public class MineResetService {

    public void reset(Mine mine) {
        // Guarda contra reentrada (mesmo padrao do AtomicBoolean "running" do AxMines) -
        // nao deveria mais ser estritamente necessaria agora que o fill e 100% sincrono
        // (nao ha mais janela assincrona pra reavaliar o MineResetTask no meio do caminho),
        // mas custa quase nada e protege contra reentrancia via MinePreResetEvent/comandos
        // de reset disparando outro reset() da mesma mina recursivamente.
        if (mine.isResetting()) {
            DebugLogger.log("Reset da mina '%s' ignorado - ja tem um reset em andamento.", mine.getId());
            return;
        }

        // API: MinePreResetEvent cancelavel - segura reset durante eventos/guerras.
        MinePreResetEvent preReset = new MinePreResetEvent(mine);
        Bukkit.getPluginManager().callEvent(preReset);
        if (preReset.isCancelled()) {
            DebugLogger.log("Reset da mina '%s' cancelado (MinePreResetEvent).", mine.getId());
            return;
        }

        mine.setResetting(true);
        DebugLogger.log("Reset da mina '%s' iniciado (volume=%d, restantes=%d).",
                mine.getId(), mine.getRegion().getVolume(), mine.getBlocksRemaining());
        teleportPlayersOut(mine);

        try {
            BlockFillHook.fillRegion(mine.getRegion(), mine.getComposition());
        } catch (Throwable t) {
            Logger.getLogger("AlkaMines").log(Level.SEVERE,
                    "Reset da mina '" + mine.getId() + "' falhou - liberando a guarda mesmo assim.", t);
        } finally {
            mine.setResetting(false);
        }

        mine.setLastReset(System.currentTimeMillis());
        mine.setBlocksRemaining((int) Math.min(mine.getRegion().getVolume(), Integer.MAX_VALUE));

        MineManager manager = MineManager.getInstance();
        if (manager != null) {
            manager.markDirty(mine.getId());
        }

        runResetCommands(mine);
        broadcastReset(mine);

        DebugLogger.log("Reset '%s' concluido (restantes=%d).", mine.getId(), mine.getBlocksRemaining());
        Bukkit.getPluginManager().callEvent(new MineResetEvent(mine));
    }

    private void teleportPlayersOut(Mine mine) {
        Location destination = mine.getSpawn();
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (mine.containsMining(player.getLocation())) {
                player.teleport(destination);
                count++;
            }
        }
        DebugLogger.log("Reset '%s': %d jogador(es) teleportado(s) para o spawn.", mine.getId(), count);
    }

    /** Comandos configurados (settings.reset-commands) rodados no console a cada reset -
     * placeholders %mine% (id) e %display% (nome de exibicao). */
    private void runResetCommands(Mine mine) {
        for (String command : mine.getSettings().getResetCommands()) {
            String resolved = command
                    .replace("%mine%", mine.getId())
                    .replace("%display%", mine.getDisplayName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }
    }

    /** Broadcast do reset: 0 = mundo, -1 = todos os mundos, -2 = silencioso, >=1 = raio. */
    private void broadcastReset(Mine mine) {
        int mode = mine.getSettings().getBroadcastMode();
        if (mode == -2) {
            return;
        }
        String message = "<yellow>A mina <gold>" + mine.getDisplayName()
                + "</gold><yellow> acabou de resetar!";
        if (mode == -1) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(com.alka.mines.util.ChatUtil.parse(message));
            }
        } else if (mode == 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().getName().equals(mine.getRegion().getWorld())) {
                    player.sendMessage(com.alka.mines.util.ChatUtil.parse(message));
                }
            }
        } else {
            // raio (>=1): distancia euclidiana ao cuboide
            double radiusSquared = (double) mode * mode;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().getName().equals(mine.getRegion().getWorld())
                        && distanceToRegion(player.getLocation(), mine) <= radiusSquared) {
                    player.sendMessage(com.alka.mines.util.ChatUtil.parse(message));
                }
            }
        }
    }

    private double distanceToRegion(Location loc, Mine mine) {
        int x = Math.max(mine.getRegion().getX1(), Math.min(loc.getBlockX(), mine.getRegion().getX2()));
        int y = Math.max(mine.getRegion().getY1(), Math.min(loc.getBlockY(), mine.getRegion().getY2()));
        int z = Math.max(mine.getRegion().getZ1(), Math.min(loc.getBlockZ(), mine.getRegion().getZ2()));
        return loc.distanceSquared(new Location(loc.getWorld(), x, y, z));
    }
}
