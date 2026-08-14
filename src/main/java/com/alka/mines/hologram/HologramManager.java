package com.alka.mines.hologram;

import com.alka.mines.manager.MineManager;
import com.alka.mines.model.Mine;
import com.alka.mines.model.MineTemplate;
import com.alka.mines.model.PrivateMine;
import com.alka.mines.util.ChatUtil;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
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
 * Linhas configuraveis via holograms.yml (secao "template"/"private-template") - criado
 * com um default sensato na primeira vez que o plugin roda, se ainda nao existir.
 * IMPORTANTE: as linhas do template usam codigo & LEGADO (nao MiniMessage) porque o
 * DHAPI so entende &/paragraph, nunca <tag> - unica excecao a regra [R5] do estudio
 * (mensagem de CHAT e que e MiniMessage-only, ver ChatUtil/MessagesConfig). O
 * %name%/displayName da mina (editavel via /alkamines renomear com <rainbow> etc) e
 * a unica peca que nasce em MiniMessage e passa por {@link ChatUtil#toLegacy} antes
 * de entrar na linha.
 */
public class HologramManager {

    private static final String ID_PREFIX = "alkaminas_holo_";

    private final boolean enabled;
    private final JavaPlugin plugin;
    private final File templateFile;
    private List<String> templateLines;
    private List<String> privateTemplateLines;

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
            fresh.set("private-template", defaultPrivateTemplate());
            try {
                fresh.save(templateFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Falha ao salvar holograms.yml", e);
            }
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(templateFile);
        List<String> lines = config.getStringList("template");
        templateLines = lines.isEmpty() ? defaultTemplate() : lines;
        List<String> privateLines = config.getStringList("private-template");
        privateTemplateLines = privateLines.isEmpty() ? defaultPrivateTemplate() : privateLines;
    }

    /** Recarrega o template do disco - chamado se /alkamines reload existir la fora no futuro. */
    public void reloadTemplate() {
        loadTemplate();
    }

    private List<String> defaultTemplate() {
        return List.of(
                "&8&m━━━━━━━━━━━━━━━",
                "&6&l⛏ %name%",
                "&7Categoria: &f%category%",
                "&7Nivel: &e%min_level%",
                "%progress_bar% &7%percentage%%",
                "&7Blocos: &f%blocks%",
                "%status%",
                "&8&m━━━━━━━━━━━━━━━",
                "&7Reset em: &f%reset_time%"
        );
    }

    private List<String> defaultPrivateTemplate() {
        return List.of(
                "&8&m━━━━━━━━━━━━━━━",
                "&b&l🏠 %name%",
                "&7Dono: &f%owner%",
                "&7Template: &e%template_name% %rarity_stars%",
                "&7Nivel: &e%upgrade_level%",
                "%progress_bar% &7%percentage%%",
                "&7Blocos: &f%blocks%&7/&f%volume%",
                "&8&m━━━━━━━━━━━━━━━",
                "&7Reset: &f%reset_interval% min"
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
        long remainingSec = resetRemainingSec(mine.getSettings().getResetIntervalMinutes(), mine.getLastReset());

        List<String> lines = new ArrayList<>();
        for (String line : templateLines) {
            String processed = line
                    .replace("%name%", legacyName)
                    .replace("%category%", mine.getCategory())
                    .replace("%blocks%", String.format(Locale.US, "%,d", mine.getBlocksRemaining()))
                    .replace("%percentage%", String.valueOf(percentage))
                    .replace("%min_level%", String.valueOf(mine.getSettings().getMinPickaxeLevel()))
                    .replace("%reset_time%", formatResetTime(mine))
                    .replace("%progress_bar%", buildProgressBar(percentage))
                    .replace("%status%", buildStatus(percentage, remainingSec))
                    .replace("%players%", String.valueOf(countPlayersInMine(mine)));
            lines.add(processed);
        }
        return lines;
    }

    private String formatResetTime(Mine mine) {
        int intervalMinutes = mine.getSettings().getResetIntervalMinutes();
        if (intervalMinutes <= 0) {
            return "&cManual";
        }
        long remainingSec = resetRemainingSec(intervalMinutes, mine.getLastReset());
        return String.format("&f%02d&7:&f%02d", remainingSec / 60, remainingSec % 60);
    }

    private long resetRemainingSec(int intervalMinutes, long lastReset) {
        if (intervalMinutes <= 0) {
            return -1;
        }
        long elapsedMs = System.currentTimeMillis() - lastReset;
        long remainingMs = Math.max(0, intervalMinutes * 60_000L - elapsedMs);
        return remainingMs / 1000;
    }

    /** Conta jogadores online dentro da area de mineracao/lobby da mina (containsLobby cobre
     * lobby customizado se houver, senao cai pra regiao de mineracao - ver Mine#containsLobby). */
    private int countPlayersInMine(Mine mine) {
        World world = Bukkit.getWorld(mine.getRegion().getWorld());
        if (world == null) {
            return 0;
        }
        int count = 0;
        for (org.bukkit.entity.Player player : world.getPlayers()) {
            if (mine.containsLobby(player.getLocation())) {
                count++;
            }
        }
        return count;
    }

    private String buildProgressBar(double percentage) {
        int filled = (int) Math.round(percentage / 10.0);
        filled = Math.max(0, Math.min(10, filled));
        int empty = 10 - filled;
        StringBuilder bar = new StringBuilder();
        if (percentage >= 75) {
            bar.append("&a");
        } else if (percentage >= 40) {
            bar.append("&e");
        } else {
            bar.append("&c");
        }
        bar.append("█".repeat(filled));
        bar.append("&7");
        bar.append("░".repeat(empty));
        return bar.toString();
    }

    private String buildStatus(double percentage, long remainingSec) {
        if (remainingSec == 0 && percentage < 100) {
            return "&a● &aResetando...";
        }
        if (percentage >= 75) {
            return "&a● &aDisponivel";
        }
        if (percentage >= 40) {
            return "&e● &eEm uso";
        }
        if (percentage >= 10) {
            return "&6● &6Quase vazia";
        }
        return "&c● &cCritica";
    }

    private String buildRarityStars(String rarity) {
        int stars = rarity == null ? 0 : rarity.replaceAll("[^★]", "").length();
        if (stars <= 0) {
            stars = 1;
        }
        stars = Math.min(stars, 5);
        return "&e" + "★".repeat(stars) + "&7" + "☆".repeat(Math.max(0, 5 - stars));
    }

    // ---------- hologramas de minas particulares ----------

    /** Cria (ou recria) o holograma de status acima de uma mina particular. */
    public void createPrivate(String mineKey, Location loc, PrivateMine mine, MineTemplate template) {
        if (!enabled) {
            return;
        }
        String id = privateId(mineKey);
        if (DHAPI.getHologram(id) != null) {
            DHAPI.removeHologram(id);
        }
        Location top = loc.clone().add(0, 2.0, 0);
        DHAPI.createHologram(id, top, buildPrivateLines(mine, template));
    }

    /** Atualiza as linhas do holograma de uma mina particular. */
    public void updatePrivate(String mineKey, PrivateMine mine, MineTemplate template) {
        if (!enabled) {
            return;
        }
        Hologram hologram = DHAPI.getHologram(privateId(mineKey));
        if (hologram != null) {
            DHAPI.setHologramLines(hologram, buildPrivateLines(mine, template));
        }
    }

    public void removePrivate(String mineKey) {
        if (!enabled) {
            return;
        }
        DHAPI.removeHologram(privateId(mineKey));
    }

    private List<String> buildPrivateLines(PrivateMine mine, MineTemplate template) {
        String displayName = template != null ? template.getDisplayName() : mine.getTemplateId();
        String legacyName = ChatUtil.toLegacy(displayName);
        OfflinePlayer owner = Bukkit.getOfflinePlayer(mine.getOwner());
        String ownerName = owner.getName() != null ? owner.getName() : mine.getOwner().toString();

        long volume = mine.volume();
        double pct = volume > 0
                ? Math.round((mine.getBlocksRemaining() / (double) volume) * 1000.0) / 10.0
                : 0.0;
        int resetInterval = mine.getResetIntervalMinutes();
        long remainingSec = resetRemainingSec(resetInterval, mine.getLastReset());
        String rarity = template != null ? template.getRarity() : "★";

        List<String> lines = new ArrayList<>();
        for (String line : privateTemplateLines) {
            lines.add(line
                    .replace("%name%", legacyName)
                    .replace("%owner%", ownerName)
                    .replace("%template_name%", legacyName)
                    .replace("%rarity_stars%", buildRarityStars(rarity))
                    .replace("%upgrade_level%", String.valueOf(mine.getUpgradeLevel()))
                    .replace("%blocks%", String.format(Locale.US, "%,d", mine.getBlocksRemaining()))
                    .replace("%volume%", String.format(Locale.US, "%,d", volume))
                    .replace("%percentage%", String.valueOf(pct))
                    .replace("%progress_bar%", buildProgressBar(pct))
                    .replace("%status%", buildStatus(pct, remainingSec))
                    .replace("%reset_interval%", String.valueOf(resetInterval)));
        }
        return lines;
    }

    private String privateId(String mineKey) {
        return "alkaminas_priv_" + mineKey.replaceAll("[^a-zA-Z0-9_-]", "");
    }
}
