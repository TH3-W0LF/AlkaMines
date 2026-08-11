package com.alka.mines.task;

import com.alka.mines.manager.MineManager;
import com.alka.mines.model.Mine;
import com.alka.mines.model.MineSettings;
import com.alka.mines.service.MineResetService;
import com.alka.mines.util.ChatUtil;
import com.alka.mines.util.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Roda a cada 20 ticks (1s) verificando reset por tempo e por porcentagem de blocos
 * restantes, e envia o ActionBar de status pras minas com settings.actionbar-enabled.
 * Itera uma copia da colecao de minas - MineManager.getMines() e apoiada num Map
 * mutavel, entao iterar a colecao original enquanto um reset (async) altera o proprio
 * mapa causaria ConcurrentModificationException.
 */
public class MineResetTask implements Runnable {

    private final MineManager mineManager;
    private final MineResetService resetService;
    private int actionBarTick;

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
                DebugLogger.log("Reset por TEMPO: mina '%s' (intervalo=%d min, ultimo reset ha %d s).",
                        mine.getId(), settings.getResetIntervalMinutes(),
                        (System.currentTimeMillis() - mine.getLastReset()) / 1000);
                resetService.reset(mine);
                continue;
            }

            if (settings.getResetPercentage() > 0
                    && mine.getBlocksRemaining() <= (mine.getRegion().getVolume() * (settings.getResetPercentage() / 100.0))) {
                DebugLogger.log("Reset por PORCENTAGEM: mina '%s' restantes=%d volume=%d (pct=%.1f).",
                        mine.getId(), mine.getBlocksRemaining(), mine.getRegion().getVolume(),
                        settings.getResetPercentage());
                resetService.reset(mine);
            }
        }

        // ActionBar de status a cada 10 ticks (0.5s), so pra minas habilitadas - evita
        // mandar packet a cada tick pra todo mundo.
        if (++actionBarTick < 10) {
            return;
        }
        actionBarTick = 0;
        for (Mine mine : mines) {
            if (!mine.getSettings().isActionbarEnabled()) {
                continue;
            }
            sendActionBar(mine);
        }
    }

    private void sendActionBar(Mine mine) {
        double remaining = remainingPercentage(mine);
        String message = "<gold>" + mine.getDisplayName() + "</gold> <white>|</white> "
                + "<gray>Restantes: <white>" + String.format(Locale.US, "%,d", mine.getBlocksRemaining())
                + " <gray>(<white>" + String.format("%.1f", remaining) + "%<gray>)";

        World world = Bukkit.getWorld(mine.getRegion().getWorld());
        if (world == null) {
            return;
        }
        int range = mine.getSettings().getActionbarRange();
        int rangeSquared = range * range;
        for (Player player : world.getPlayers()) {
            if (distanceToRegionSquared(player.getLocation(), mine) <= rangeSquared) {
                player.sendActionBar(ChatUtil.parse(message));
            }
        }
    }

    private double distanceToRegionSquared(Location loc, Mine mine) {
        int x = Math.max(mine.getRegion().getX1(), Math.min(loc.getBlockX(), mine.getRegion().getX2()));
        int y = Math.max(mine.getRegion().getY1(), Math.min(loc.getBlockY(), mine.getRegion().getY2()));
        int z = Math.max(mine.getRegion().getZ1(), Math.min(loc.getBlockZ(), mine.getRegion().getZ2()));
        return loc.distanceSquared(new Location(loc.getWorld(), x, y, z));
    }

    private double remainingPercentage(Mine mine) {
        long volume = mine.getRegion().getVolume();
        if (volume <= 0) {
            return 0.0;
        }
        return Math.round((mine.getBlocksRemaining() / (double) volume) * 1000.0) / 10.0;
    }
}
