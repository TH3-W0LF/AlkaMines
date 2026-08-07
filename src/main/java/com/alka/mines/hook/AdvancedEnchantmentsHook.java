package com.alka.mines.hook;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Ponte opcional com o AdvancedEnchantments via reflexao - e um plugin pago, sem jar
 * publico em repositorio, entao nao da pra usar compileOnly normal. O AE processa
 * BlockBreakEvent em prioridade HIGH; como MineBreakListener roda em HIGHEST e
 * cancela o evento pra assumir controle manual do bloco, chamamos applyBreakEffects
 * manualmente ANTES do cancelamento pra nao perder os efeitos do AE (particulas,
 * drops extras dos encantamentos, etc).
 */
public final class AdvancedEnchantmentsHook {

    private final Object apiInstance;
    private final Method applyEnchantMethod;
    private final Method hasEnchantmentsMethod;
    private final Method applyBreakEffectsMethod;

    private AdvancedEnchantmentsHook(Object apiInstance, Method applyEnchantMethod,
                                      Method hasEnchantmentsMethod, Method applyBreakEffectsMethod) {
        this.apiInstance = apiInstance;
        this.applyEnchantMethod = applyEnchantMethod;
        this.hasEnchantmentsMethod = hasEnchantmentsMethod;
        this.applyBreakEffectsMethod = applyBreakEffectsMethod;
    }

    public static Optional<AdvancedEnchantmentsHook> tryHook(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("AdvancedEnchantments") == null) {
            return Optional.empty();
        }

        try {
            Class<?> apiClass = Class.forName("net.advancedplugins.ae.api.AEAPI");
            Object apiInstance = apiClass.getDeclaredConstructor().newInstance();

            Method applyEnchantMethod = apiClass.getMethod("applyEnchant", String.class, int.class, ItemStack.class);
            Method hasEnchantmentsMethod = apiClass.getMethod("hasEnchantments", ItemStack.class);

            Method applyBreakEffectsMethod = null;
            try {
                applyBreakEffectsMethod = apiClass.getMethod("applyBreakEffects", Player.class, Block.class);
            } catch (NoSuchMethodException e) {
                plugin.getLogger().warning("AdvancedEnchantments sem applyBreakEffects nesta versao - efeitos de quebra nao serao replicados.");
            }

            plugin.getLogger().info("Hook AdvancedEnchantments ativado (via reflexao).");
            return Optional.of(new AdvancedEnchantmentsHook(apiInstance, applyEnchantMethod, hasEnchantmentsMethod,
                    applyBreakEffectsMethod));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.WARNING, "AdvancedEnchantments encontrado mas API nao carregou via reflexao.", e);
            return Optional.empty();
        }
    }

    /** Chamado pelo MineBreakListener ANTES de cancelar o BlockBreakEvent vanilla. */
    public void processBlockBreak(Player player, Block block) {
        if (applyBreakEffectsMethod == null) {
            return;
        }
        try {
            applyBreakEffectsMethod.invoke(null, player, block);
        } catch (ReflectiveOperationException e) {
            // efeito colateral raro, nao deve impedir a quebra do bloco na mina.
        }
    }

    public boolean hasEnchantments(ItemStack item) {
        if (item == null) {
            return false;
        }
        try {
            return (boolean) hasEnchantmentsMethod.invoke(apiInstance, item);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public ItemStack applyEnchant(String enchant, int level, ItemStack item) {
        if (item == null) {
            return null;
        }
        try {
            return (ItemStack) applyEnchantMethod.invoke(apiInstance, enchant, level, item);
        } catch (ReflectiveOperationException e) {
            return item;
        }
    }
}
