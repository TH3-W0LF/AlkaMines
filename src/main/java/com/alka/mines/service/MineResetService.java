package com.alka.mines.service;

import com.alka.mines.event.MinePreResetEvent;
import com.alka.mines.event.MineResetEvent;
import com.alka.mines.hook.FAWEHook;
import com.alka.mines.manager.MineManager;
import com.alka.mines.model.Mine;
import com.alka.mines.util.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Orquestra o reset de uma mina: teleporta jogadores (sync, API do Bukkit), roda o
 * FAWEHook fora da main thread (FAWE e seguro pra isso, ao contrario do bloco por
 * bloco do Bukkit puro) e so entao atualiza o estado + dispara o evento, de volta na
 * main thread.
 */
public class MineResetService {

    private final JavaPlugin plugin;

    public MineResetService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reset(Mine mine) {
        // API: MinePreResetEvent cancelavel - segura reset durante eventos/guerras.
        MinePreResetEvent preReset = new MinePreResetEvent(mine);
        Bukkit.getPluginManager().callEvent(preReset);
        if (preReset.isCancelled()) {
            DebugLogger.log("Reset da mina '%s' cancelado (MinePreResetEvent).", mine.getId());
            return;
        }

        DebugLogger.log("Reset da mina '%s' iniciado (volume=%d, restantes=%d).",
                mine.getId(), mine.getRegion().getVolume(), mine.getBlocksRemaining());
        teleportPlayersOut(mine);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long start = System.nanoTime();
            FAWEHook.resetRegion(mine.getRegion(), mine.getComposition());
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            DebugLogger.log("Reset '%s': FAWE concluido em %d ms.", mine.getId(), elapsedMs);

            Bukkit.getScheduler().runTask(plugin, () -> {
                mine.setLastReset(System.currentTimeMillis());
                mine.setBlocksRemaining((int) Math.min(mine.getRegion().getVolume(), Integer.MAX_VALUE));

                MineManager manager = MineManager.getInstance();
                if (manager != null) {
                    manager.markDirty(mine.getId());
                }

                runResetCommands(mine);
                broadcastReset(mine);

                DebugLogger.log("Reset '%s' concluido na main thread (restantes=%d).",
                        mine.getId(), mine.getBlocksRemaining());
                Bukkit.getPluginManager().callEvent(new MineResetEvent(mine));
            });
        });
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
