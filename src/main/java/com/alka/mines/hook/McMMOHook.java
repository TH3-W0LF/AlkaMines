package com.alka.mines.hook;

import com.alka.mines.manager.MineManager;
import com.alka.mines.model.MineBlock;
import com.gmail.nossr50.api.ExperienceAPI;
import com.gmail.nossr50.events.experience.McMMOPlayerXpGainEvent;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Ponte opcional com o mcMMO - concede XP de Mineracao por bloco quebrado dentro de
 * uma mina. Prioridade do valor de XP: 1) override por bloco configurado direto na
 * composicao da mina via BlockCompositionMenu (MineBlock#getMcmmoXp - editavel em
 * jogo, sem precisar de reload); 2) tabela global `mcmmo-xp` do config.yml (Material
 * -> XP). Integracao soft: sem mcMMO instalado o hook nem existe e MineBreakListener
 * so pula a chamada (ver Optional<McMMOHook>).
 *
 * Alem de conceder, este hook TAMBEM bloqueia o XP nativo do mcMMO dentro de minas:
 * o mcMMO concede XP de Mineracao pelo proprio listener de BlockBreakEvent, que NAO
 * respeita o soft-cancel do MineBreakListener (por isso dava XP em dobro - o nativo
 * + o nosso). A forma deterministica de parar isso e cancelar o evento de ganho de
 * XP do proprio mcMMO (McMMOPlayerXpGainEvent) enquanto o jogador estiver dentro de
 * uma mina. O XP que concedemos usa ExperienceAPI.addRawXP, que NAO dispara esse
 * evento - entao o que configurarmos na mina continua valendo exatamente.
 */
public final class McMMOHook implements Listener {

    private final Map<String, Double> xpTable = new HashMap<>();
    private final ThreadLocal<Boolean> grantingRawXp = ThreadLocal.withInitial(() -> false);

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
        try {
            McMMOHook hook = new McMMOHook(plugin);
            // registra o bloqueio de XP nativo dentro de minas (ver javadoc da classe).
            Bukkit.getPluginManager().registerEvents(hook, plugin);
            plugin.getLogger().info("Hook mcMMO ativado (XP de Mineracao por bloco + bloqueio de XP nativo dentro de minas).");
            return Optional.of(hook);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "mcMMO encontrado mas o hook de XP nao registrou (versao incompativel?) - "
                    + "XP nativo dentro de minas NAO sera bloqueado.", t);
            return Optional.empty();
        }
    }

    /** Cancela QUALQUER ganho de XP do mcMMO enquanto o jogador estiver dentro de uma
     * mina (lobby/dungeon). Nao afeta nosso proprio XP (addRawXP nao dispara este
     * evento, e o flag grantingRawXp protege ate se alguma versao disparar). */
    @EventHandler
    public void onXpGain(McMMOPlayerXpGainEvent event) {
        if (Boolean.TRUE.equals(grantingRawXp.get())) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) {
            return;
        }
        MineManager manager = MineManager.getInstance();
        if (manager != null && manager.getMineLobbyAt(player.getLocation()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /**
     * Chamado pelo MineBreakListener apos confirmar que o bloco pertence a uma mina.
     * compositionBlock e a entrada correspondente na composicao da mina (pode ser
     * null se o bloco quebrado nao faz parte de nenhuma composicao configurada).
     *
     * Prioridade: se o bloco tiver XP de mcMMO CONFIGURADO na composicao da mina
     * (mcmmoXpConfigured=true), esse valor e o unico usado - 0 = sem XP nenhum, sem
     * cair pra tabela global. So usa a tabela global `mcmmo-xp` do config.yml quando
     * o bloco nunca teve XP configurado na mina (mcmmoXpConfigured=false).
     */
    public void addMiningXp(Player player, Block block, MineBlock compositionBlock) {
        double xp = computeXp(block, compositionBlock);
        if (xp <= 0) {
            return;
        }
        grantingRawXp.set(true);
        try {
            ExperienceAPI.addRawXP(player, "Mining", (float) xp, "UNKNOWN");
        } catch (Throwable t) {
            // nunca derrubar a mineracao por causa de XP.
        } finally {
            grantingRawXp.set(false);
        }
    }

    private double computeXp(Block block, MineBlock compositionBlock) {
        if (compositionBlock != null && compositionBlock.isMcmmoXpConfigured()) {
            return compositionBlock.getMcmmoXp();
        }
        return xpTable.getOrDefault(normalize(block.getType().name()), 0.0);
    }

    private static String normalize(String key) {
        return key.toUpperCase(Locale.ROOT);
    }
}
