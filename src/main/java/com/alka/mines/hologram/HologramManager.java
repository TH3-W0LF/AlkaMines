package com.alka.mines.hologram;

import com.alka.mines.manager.MineManager;
import com.alka.mines.model.Mine;
import com.alka.mines.util.ChatUtil;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Holograma por mina via DecentHolograms (DHAPI). Se o DH nao estiver instalado, os
 * metodos aqui viram no-op silencioso - {@link #isEnabled()} existe exatamente pra
 * quem chama poder avisar o admin em vez de mostrar uma mensagem de sucesso falsa.
 * Nao ha fallback para HolographicDisplays implementado (API completamente
 * diferente; se precisar, criar um segundo HologramManager e escolher qual
 * instanciar no onEnable conforme o plugin presente).
 *
 * Linhas configuraveis via holograms.yml (secao "template", placeholders %name%/
 * %category%/%blocks%/%percentage%/%min_level%/%reset_time%) - criado com um default
 * sensato na primeira vez que o plugin roda, se ainda nao existir.
 */
public class HologramManager {

    private static final String ID_PREFIX = "alkaminas_holo_";

    private final boolean enabled;
    private final JavaPlugin plugin;
    private final File templateFile;
    private List<String> templateLines;

    public HologramManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");
        this.templateFile = new File(plugin.getDataFolder(), "holograms.yml");
        loadTemplate();
        if (!enabled) {
            plugin.getLogger().warning("DecentHolograms nao encontrado - hologramas de mina desativados.");
        }
    }

    private void loadTemplate() {
        if (!templateFile.exists()) {
            YamlConfiguration fresh = new YamlConfiguration();
            fresh.set("template", defaultTemplate());
            try {
                fresh.save(templateFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Falha ao salvar holograms.yml", e);
            }
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(templateFile);
        List<String> lines = config.getStringList("template");
        templateLines = lines.isEmpty() ? defaultTemplate() : lines;
    }

    /** Recarrega o template do disco - chamado se /alkamines reload existir la fora no futuro. */
    public void reloadTemplate() {
        loadTemplate();
    }

    private List<String> defaultTemplate() {
        return List.of(
                "&d&l%name%",
                "&fBlocos restantes: &7%blocks%",
                "&fRestante: &a%percentage%%",
                "&fReset em: &7%reset_time%"
        );
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Recria no boot todo holograma cuja mina tenha uma hologramLocation persistida em mines.yml -
     * sem isso, hologramas somem a cada restart ate um admin reabrir o menu e setar de novo. */
    public void loadAll(MineManager mineManager) {
        if (!enabled) {
            return;
        }
        for (Mine mine : mineManager.getMines()) {
            if (mine.getHologramLocation() != null) {
                createOrUpdate(mine, mine.getHologramLocation());
            }
        }
    }

    /** Cria (ou recria, se ja existir) o holograma 1.5 blocos acima da location informada. */
    public void createOrUpdate(Mine mine, Location location) {
        if (!enabled) {
            return;
        }

        Location loc = location.clone().add(0, 1.5, 0);
        String id = safeHoloId(mine.getId());

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
        Hologram hologram = DHAPI.getHologram(safeHoloId(mine.getId()));
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
        DHAPI.removeHologram(safeHoloId(mineId));
    }

    /** Ids de mina ja sao restritos a [a-z0-9_-] na criacao (ver AdminCommands#sanitizeId),
     * mas isso e defesa em profundidade - o id do holograma no DHAPI nunca deve carregar
     * caractere que a lib possa rejeitar/tratar diferente. */
    private String safeHoloId(String mineId) {
        return ID_PREFIX + mineId.replaceAll("[^a-zA-Z0-9_-]", "");
    }

    private List<String> buildLines(Mine mine) {
        long volume = mine.getRegion().getVolume();
        double percentage = volume > 0
                ? Math.round((mine.getBlocksRemaining() / (double) volume) * 1000.0) / 10.0
                : 0.0;
        // MiniMessage (displayName e editavel via /alkamines renomear com tags tipo
        // <rainbow>) -> legacy, ja que o DHAPI so entende &/paragraph, nunca <tag>.
        String legacyName = ChatUtil.toLegacy(mine.getDisplayName());

        List<String> lines = new ArrayList<>();
        for (String line : templateLines) {
            String processed = line
                    .replace("%name%", legacyName)
                    .replace("%category%", mine.getCategory())
                    .replace("%blocks%", String.format(Locale.US, "%,d", mine.getBlocksRemaining()))
                    .replace("%percentage%", String.valueOf(percentage))
                    .replace("%min_level%", String.valueOf(mine.getSettings().getMinPickaxeLevel()))
                    .replace("%reset_time%", formatResetTime(mine));
            lines.add(processed);
        }
        return lines;
    }

    private String formatResetTime(Mine mine) {
        int intervalMinutes = mine.getSettings().getResetIntervalMinutes();
        if (intervalMinutes <= 0) {
            return "&cManual";
        }
        long elapsedMs = System.currentTimeMillis() - mine.getLastReset();
        long remainingMs = Math.max(0, intervalMinutes * 60_000L - elapsedMs);
        long remainingSec = remainingMs / 1000;
        return String.format("&f%02d&7:&f%02d", remainingSec / 60, remainingSec % 60);
    }
}
