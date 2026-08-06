package com.alka.mines.hook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

/** Deteccao de presenca apenas - sem chamadas de API ainda, pronto pra uso futuro. */
public final class AdvancedEnchantmentsHook {

    private AdvancedEnchantmentsHook() {
    }

    public static Optional<AdvancedEnchantmentsHook> tryHook(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("AdvancedEnchantments") == null) {
            return Optional.empty();
        }
        plugin.getLogger().info("Hook AdvancedEnchantments ativado.");
        return Optional.of(new AdvancedEnchantmentsHook());
    }
}
