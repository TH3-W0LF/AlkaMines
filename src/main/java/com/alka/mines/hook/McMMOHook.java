package com.alka.mines.hook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

/** Deteccao de presenca apenas - sem chamadas de API ainda, pronto pra uso futuro. */
public final class McMMOHook {

    private McMMOHook() {
    }

    public static Optional<McMMOHook> tryHook(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("mcMMO") == null) {
            return Optional.empty();
        }
        plugin.getLogger().info("Hook mcMMO ativado.");
        return Optional.of(new McMMOHook());
    }
}
