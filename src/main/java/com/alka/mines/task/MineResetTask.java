package com.alka.mines.task;

import com.alka.mines.manager.MineManager;
import com.alka.mines.model.Mine;
import com.alka.mines.model.MineSettings;
import com.alka.mines.service.MineResetService;

import java.util.ArrayList;
import java.util.List;

/**
 * Roda a cada 20 ticks (1s) verificando reset por tempo e por porcentagem de blocos
 * restantes. Itera uma copia da colecao de minas - MineManager.getMines() e apoiada
 * num Map mutavel, entao iterar a colecao original enquanto um reset (async) altera o
 * proprio mapa causaria ConcurrentModificationException.
 */
public class MineResetTask implements Runnable {

    private final MineManager mineManager;
    private final MineResetService resetService;

    public MineResetTask(MineManager mineManager, MineResetService resetService) {
        this.mineManager = mineManager;
        this.resetService = resetService;
    }

    @Override
    public void run() {
        List<Mine> mines = new ArrayList<>(mineManager.getMines());

        for (Mine mine : mines) {
            MineSettings settings = mine.getSettings();

            if (settings.getResetIntervalMinutes() > 0
                    && System.currentTimeMillis() - mine.getLastReset() >= settings.getResetIntervalMinutes() * 60_000L) {
                resetService.reset(mine);
                continue;
            }

            if (settings.getResetPercentage() > 0
                    && mine.getBlocksRemaining() <= (mine.getRegion().getVolume() * (settings.getResetPercentage() / 100.0))) {
                resetService.reset(mine);
            }
        }
    }
}
