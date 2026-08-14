package com.alka.mines.hook;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ponte opcional com o AdvancedEnchantments via reflexao - e um plugin pago, sem jar
 * publico em repositorio, entao nao da pra usar compileOnly normal. Assinaturas
 * confirmadas em 2026-08-13 via javap direto no jar real (AdvancedEnchantments-9.24.1.jar,
 * instalado no servidor de dev) - a versao anterior deste hook usava
 * {@code new AEAPI().applyEnchant(...)}/{@code hasEnchantments(...)} como instancia; a
 * API real (classe {@code net.advancedplugins.ae.api.AEAPI}) e 100% ESTATICA, sem
 * singleton nenhum, entao {@code getDeclaredConstructor().newInstance()} criava uma
 * instancia descartavel que os metodos nem usavam (efeito colateral inofensivo, mas sem
 * sentido). {@code applyEnchant} retorna um ItemStack NOVO (nao muta o parametro
 * in-place).
 *
 * <p><b>{@code applyBreakEffects(Player, Block)} nao existe em nenhuma classe do jar
 * real</b> (confirmado varrendo as 986 classes do jar, nao so a AEAPI) - o antigo
 * {@code processBlockBreak} nunca teve chance de funcionar, nao e so uma versao
 * diferente do AE. O AE processa BlockBreakEvent internamente em prioridade HIGH sem
 * expor um jeito publico de "replay" manual dos efeitos - como o MineBreakListener
 * cancela o evento em HIGHEST pra assumir controle manual do bloco, os efeitos de
 * quebra do AE (particulas, drops extras de encantamento) permanecem uma lacuna
 * conhecida sem solucao via API publica, nao um bug a corrigir.
 */
public final class AdvancedEnchantmentsHook {

    private static final String API_CLASS = "net.advancedplugins.ae.api.AEAPI";

    private final Method applyEnchantMethod;
    private final Method getEnchantmentsOnItemMethod;
    private final Logger logger;

    private AdvancedEnchantmentsHook(Method applyEnchantMethod, Method getEnchantmentsOnItemMethod, Logger logger) {
        this.applyEnchantMethod = applyEnchantMethod;
        this.getEnchantmentsOnItemMethod = getEnchantmentsOnItemMethod;
        this.logger = logger;
    }

    public static Optional<AdvancedEnchantmentsHook> tryHook(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("AdvancedEnchantments") == null) {
            return Optional.empty();
        }

        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            Method applyEnchantMethod = apiClass.getMethod("applyEnchant", String.class, int.class, ItemStack.class);
            Method getEnchantmentsOnItemMethod = apiClass.getMethod("getEnchantmentsOnItem", ItemStack.class);

            plugin.getLogger().info("Hook AdvancedEnchantments ativado (via reflexao).");
            return Optional.of(new AdvancedEnchantmentsHook(applyEnchantMethod, getEnchantmentsOnItemMethod,
                    plugin.getLogger()));
        } catch (Throwable e) {
            plugin.getLogger().log(Level.WARNING, "AdvancedEnchantments encontrado mas API nao carregou via reflexao.", e);
            return Optional.empty();
        }
    }

    /**
     * Sem equivalente publico no AE hoje - ver nota de classe. Mantido como no-op
     * documentado (nao removido) para o call site em MineBreakListener continuar
     * expressivo sobre a intencao original, caso uma versao futura do AE exponha isso.
     */
    public void processBlockBreak(Player player, Block block) {
        // Nenhum metodo publico equivalente existe no jar real - ver javadoc da classe.
    }

    public boolean hasEnchantments(ItemStack item) {
        return !getEnchantments(item).isEmpty();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Integer> getEnchantments(ItemStack item) {
        if (item == null) {
            return Map.of();
        }
        try {
            Object result = getEnchantmentsOnItemMethod.invoke(null, item);
            return result instanceof Map<?, ?> map ? (Map<String, Integer>) map : Map.of();
        } catch (Throwable e) {
            return Map.of();
        }
    }

    /** Retorna o ItemStack resultante (a API do AE nao muta in-place). */
    public ItemStack applyEnchant(String enchant, int level, ItemStack item) {
        if (item == null) {
            return null;
        }
        try {
            Object result = applyEnchantMethod.invoke(null, enchant, level, item);
            return result instanceof ItemStack stack ? stack : item;
        } catch (Throwable e) {
            logger.log(Level.FINE, "Falha ao aplicar encantamento AE '" + enchant + "'", e);
            return item;
        }
    }
}
