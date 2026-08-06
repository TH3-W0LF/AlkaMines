package com.alka.mines.hook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

/** Deteccao de presenca apenas - sem chamadas de API ainda, pronto pra uso futuro. */
public final class ItemsAdderHook {

    private ItemsAdderHook() {
    }

    public static Optional<ItemsAdderHook> tryHook(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
            return Optional.empty();
        }
        plugin.getLogger().info("Hook ItemsAdder ativado.");
        return Optional.of(new ItemsAdderHook());
    }
}
