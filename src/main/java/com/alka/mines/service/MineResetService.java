package com.alka.mines.service;

import com.alka.mines.event.MineResetEvent;
import com.alka.mines.hook.FAWEHook;
import com.alka.mines.manager.MineManager;
import com.alka.mines.model.Mine;
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
        teleportPlayersOut(mine);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            FAWEHook.resetRegion(mine.getRegion(), mine.getComposition());

            Bukkit.getScheduler().runTask(plugin, () -> {
                mine.setLastReset(System.currentTimeMillis());
                mine.setBlocksRemaining((int) Math.min(mine.getRegion().getVolume(), Integer.MAX_VALUE));

                MineManager manager = MineManager.getInstance();
                if (manager != null) {
                    manager.save();
                }

                Bukkit.getPluginManager().callEvent(new MineResetEvent(mine));
            });
        });
    }

    private void teleportPlayersOut(Mine mine) {
        Location destination = mine.getSpawn();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (mine.contains(player.getLocation())) {
                player.teleport(destination);
            }
        }
    }
}
