package com.alka.mines.hologram;

import com.alka.mines.manager.MineManager;
import com.alka.mines.model.Mine;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Holograma por mina via DecentHolograms (DHAPI). Se o DH nao estiver instalado, os
 * metodos aqui viram no-op silencioso - {@link #isEnabled()} existe exatamente pra
 * quem chama poder avisar o admin em vez de mostrar uma mensagem de sucesso falsa.
 * Nao ha fallback para HolographicDisplays implementado (API completamente
 * diferente; se precisar, criar um segundo HologramManager e escolher qual
 * instanciar no onEnable conforme o plugin presente).
 */
public class HologramManager {

    private static final String ID_PREFIX = "alkaminas_holo_";

    private final boolean enabled;

    public HologramManager(JavaPlugin plugin) {
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");
        if (!enabled) {
            plugin.getLogger().warning("DecentHolograms nao encontrado - hologramas de mina desativados.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Cria (ou recria, se ja existir) o holograma 1.5 blocos acima da location informada. */
    public void createOrUpdate(Mine mine, Location location) {
        if (!enabled) {
            return;
        }

        Location loc = location.clone().add(0, 1.5, 0);
        String id = ID_PREFIX + mine.getId();

        if (DHAPI.getHologram(id) != null) {
            DHAPI.removeHologram(id);
        }

        DHAPI.createHologram(id, loc, buildLines(mine));
    }

    /** Substitui as linhas de um holograma ja existente com os valores atuais da mina. */
    public void updateHologram(Mine mine) {
        if (!enabled) {
            return;
        }
        Hologram hologram = DHAPI.getHologram(ID_PREFIX + mine.getId());
        if (hologram != null) {
            DHAPI.setHologramLines(hologram, buildLines(mine));
        }
    }

    public void updateAll(MineManager mineManager) {
        if (!enabled) {
            return;
        }
        for (Mine mine : mineManager.getMines()) {
            updateHologram(mine);
        }
    }

    public void delete(String mineId) {
        if (!enabled) {
            return;
        }
        DHAPI.removeHologram(ID_PREFIX + mineId);
    }

    private List<String> buildLines(Mine mine) {
        List<String> lines = new ArrayList<>();
        lines.add("§b§l" + mine.getDisplayName());
        lines.add("§fBlocos: §7" + mine.getBlocksRemaining());
        lines.add("§fReset: §7" + formatResetTime(mine));
        return lines;
    }

    private String formatResetTime(Mine mine) {
        int intervalMinutes = mine.getSettings().getResetIntervalMinutes();
        if (intervalMinutes <= 0) {
            return "-";
        }
        long elapsedMs = System.currentTimeMillis() - mine.getLastReset();
        long remainingMs = Math.max(0, intervalMinutes * 60_000L - elapsedMs);
        long remainingSec = remainingMs / 1000;
        return String.format("%02d:%02d", remainingSec / 60, remainingSec % 60);
    }
}
