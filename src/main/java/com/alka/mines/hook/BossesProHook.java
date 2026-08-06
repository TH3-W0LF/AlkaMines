package com.alka.mines.hook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

/** Deteccao de presenca apenas - sem chamadas de API ainda, pronto pra uso futuro. */
public final class BossesProHook {

    private BossesProHook() {
    }

    public static Optional<BossesProHook> tryHook(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("BossesPro") == null
                && Bukkit.getPluginManager().getPlugin("BP") == null) {
            return Optional.empty();
        }
        plugin.getLogger().info("Hook BossesPro ativado.");
        return Optional.of(new BossesProHook());
    }
}
