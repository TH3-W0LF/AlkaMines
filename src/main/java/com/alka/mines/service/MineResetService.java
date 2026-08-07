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
 * reset() so e chamado de contextos ja na main thread (comando /minaadmin e
 * MineResetTask via runTaskTimer) - por isso pode invocar FAWEHook.resetRegion()
 * direto. O reset puro por FAWE (EditSession/RandomPattern) e rapido o bastante pra
 * despachar pra fora da main thread sem lag perceptivel. Ja o reset com bloco custom
 * do ItemsAdder (FAWEHook#resetWithCustomBlocks) EXIGE main thread (CustomBlock.place)
 * e e pesado demais pra rodar tudo num tick so - por isso FAWEHook mesmo se encarrega
 * de espalhar em lotes por tick, e so chama onComplete (finishReset) no ultimo lote.
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
            FAWEHook.resetRegion(plugin, mine.getRegion(), mine.getComposition(), () -> finishReset(mine));
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                FAWEHook.resetRegion(plugin, mine.getRegion(), mine.getComposition(), null);
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
