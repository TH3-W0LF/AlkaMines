package com.alka.mines.hook;

import com.alka.mines.model.MineBlock;
import com.gmail.nossr50.api.ExperienceAPI;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Ponte opcional com o mcMMO - concede XP de Mineracao por bloco quebrado dentro de
 * uma mina. Prioridade do valor de XP: 1) override por bloco configurado direto na
 * composicao da mina via BlockCompositionMenu (MineBlock#getMcmmoXp - editavel em
 * jogo, sem precisar de reload); 2) tabela global `mcmmo-xp` do config.yml (Material
 * -> XP, ou namespace do ItemsAdder). Integracao soft: sem mcMMO instalado o hook
 * nem existe e MineBreakListener so pula a chamada (ver Optional<McMMOHook>).
 */
public final class McMMOHook {

    private final Map<String, Double> xpTable = new HashMap<>();

    private McMMOHook(JavaPlugin plugin) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("mcmmo-xp");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            double xp = section.getDouble(key, 0.0);
            if (xp > 0) {
                xpTable.put(normalize(key), xp);
            }
        }
    }

    public static Optional<McMMOHook> tryHook(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("mcMMO") == null) {
            return Optional.empty();
        }
        plugin.getLogger().info("Hook mcMMO ativado (XP de Mineracao por bloco).");
        return Optional.of(new McMMOHook(plugin));
    }

    /**
     * Chamado pelo MineBreakListener apos confirmar que o bloco pertence a uma mina.
     * compositionBlock e a entrada correspondente na composicao da mina (pode ser
     * null se o bloco quebrado nao faz parte de nenhuma composicao configurada).
     */
    public void addMiningXp(Player player, Block block, MineBlock compositionBlock) {
        if (compositionBlock != null && compositionBlock.getMcmmoXp() > 0) {
            ExperienceAPI.addRawXP(player, "Mining", (float) compositionBlock.getMcmmoXp(), "UNKNOWN");
            return;
        }

        double xp = xpTable.getOrDefault(normalize(block.getType().name()), 0.0);

        if (xp == 0.0 && ItemsAdderHook.isEnabled()) {
            String namespace = ItemsAdderHook.getBlockNamespace(block);
            if (namespace != null) {
                xp = xpTable.getOrDefault(normalize(namespace), 0.0);
            }
        }

        if (xp > 0) {
            ExperienceAPI.addRawXP(player, "Mining", (float) xp, "UNKNOWN");
        }
    }

    private static String normalize(String key) {
        return key.toUpperCase(Locale.ROOT);
    }
}
