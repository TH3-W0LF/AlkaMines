package com.alka.mines.hook;

import com.alkacode.economy.AlkaEconomyPlugin;
import com.alkacode.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;

/**
 * Ponte opcional com o AlkaEconomy - suporta qualquer CurrencyType (usamos COINS
 * pras vendas e ESCARION pra recompensa de mineracao). Integracao soft: se o
 * AlkaEconomy nao estiver instalado, quem usa este hook simplesmente nao chama nada
 * (ver Optional em tryHook). Nao usa Vault: a propria AlkaEconomy ja cobre todas as
 * moedas direto via EconomyManager, Vault so serviria pra COINS mesmo assim.
 */
public final class AlkaEconomyHook {

    private final EconomyManager economyManager;

    private AlkaEconomyHook(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    public static Optional<AlkaEconomyHook> tryHook(JavaPlugin plugin) {
        if (!(Bukkit.getPluginManager().getPlugin("AlkaEconomy") instanceof AlkaEconomyPlugin alkaEconomy)) {
            return Optional.empty();
        }
        plugin.getLogger().info("Hook do AlkaEconomy habilitado (COINS + ESCARION).");
        return Optional.of(new AlkaEconomyHook(alkaEconomy.getEconomyManager()));
    }

    public void deposit(UUID uuid, String currency, double amount) {
        economyManager.addBalance(uuid, currency, amount);
    }

    public double getBalance(UUID uuid, String currency) {
        return economyManager.getBalance(uuid, currency);
    }

    public String format(double amount) {
        return EconomyManager.formatValue(amount);
    }
}
