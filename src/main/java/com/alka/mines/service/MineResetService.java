package com.alka.mines.service;

import com.alka.mines.event.MineResetEvent;
import com.alka.mines.hook.FAWEHook;
import com.alka.mines.manager.MineManager;
import com.alka.mines.model.Mine;
import com.alka.mines.model.MineBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Orquestra o reset de uma mina: teleporta jogadores (sync, API do Bukkit), roda o
 * FAWEHook e so entao atualiza o estado + dispara o evento, de volta na main thread.
 *
 * O reset puro por FAWE (EditSession/RandomPattern) e seguro fora da main thread.
 * Mas se a composicao tiver bloco custom do ItemsAdder, FAWEHook cai pro reset
 * hibrido bloco-a-bloco (ver FAWEHook#resetWithCustomBlocks), que chama
 * Block#setType e CustomBlock.place diretamente - APIs do Bukkit/ItemsAdder que
 * NAO sao seguras fora da main thread. Por isso o reset roda inteiro sincrono
 * quando ha bloco custom, e so cai pra async quando e FAWE puro.
 */
public class MineResetService {

    private final JavaPlugin plugin;

    public MineResetService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reset(Mine mine) {
        teleportPlayersOut(mine);

        boolean hasCustomBlocks = mine.getComposition().stream().anyMatch(MineBlock::isCustomBlock);

        if (hasCustomBlocks) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                FAWEHook.resetRegion(mine.getRegion(), mine.getComposition());
                finishReset(mine);
            });
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                FAWEHook.resetRegion(mine.getRegion(), mine.getComposition());
                Bukkit.getScheduler().runTask(plugin, () -> finishReset(mine));
            });
        }
    }

    private void finishReset(Mine mine) {
        mine.setLastReset(System.currentTimeMillis());
        mine.setBlocksRemaining((int) Math.min(mine.getRegion().getVolume(), Integer.MAX_VALUE));

        MineManager manager = MineManager.getInstance();
        if (manager != null) {
            manager.save();
        }

        Bukkit.getPluginManager().callEvent(new MineResetEvent(mine));
    }

    private void teleportPlayersOut(Mine mine) {
        Location destination = mine.getSpawn();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (mine.containsMining(player.getLocation())) {
                player.teleport(destination);
            }
        }
    }
}
