package com.alka.mines.hook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Ponte opcional com o AlkaEconomy - suporta qualquer currencyId (usamos COINS pras
 * vendas e ESCARION pra recompensa de mineracao). Integracao soft: se o AlkaEconomy
 * nao estiver instalado, quem usa este hook simplesmente nao chama nada (ver Optional
 * em tryHook). Nao usa Vault: a propria AlkaEconomy ja cobre todas as moedas direto
 * via EconomyManager, Vault so serviria pra COINS mesmo assim.
 *
 * NUNCA importar com.alkacode.economy.AlkaEconomyPlugin/EconomyManager diretamente
 * aqui - a versao anterior fazia `plugin instanceof AlkaEconomyPlugin`, e o
 * `instanceof` resolve a classe na hora, incondicionalmente, mesmo pra `null`. Isso
 * derrubou o AlkaMines com NoClassDefFoundError quando o AlkaEconomy nao esta
 * instalado (softdepend no plugin.yml, nao depend - ver o mesmo bug corrigido em
 * AlkaShopHook). Tudo aqui e via reflexao: `Class#isInstance` no lugar de
 * `instanceof`, `Method#invoke` no lugar de chamada direta.
 */
public final class AlkaEconomyHook {

    private final Object economyManager;
    private final Method addBalanceMethod;
    private final Method getBalanceMethod;
    private final Method formatValueMethod;

    private AlkaEconomyHook(Object economyManager, Method addBalanceMethod, Method getBalanceMethod, Method formatValueMethod) {
        this.economyManager = economyManager;
        this.addBalanceMethod = addBalanceMethod;
        this.getBalanceMethod = getBalanceMethod;
        this.formatValueMethod = formatValueMethod;
    }

    public static Optional<AlkaEconomyHook> tryHook(JavaPlugin plugin) {
        Plugin alkaEconomy = Bukkit.getPluginManager().getPlugin("AlkaEconomy");
        if (alkaEconomy == null) {
            return Optional.empty();
        }

        try {
            Class<?> pluginClass = Class.forName("com.alkacode.economy.AlkaEconomyPlugin");
            if (!pluginClass.isInstance(alkaEconomy)) {
                return Optional.empty();
            }

            Object economyManager = pluginClass.getMethod("getEconomyManager").invoke(alkaEconomy);

            Class<?> economyManagerClass = Class.forName("com.alkacode.economy.EconomyManager");
            Method addBalanceMethod = economyManagerClass.getMethod("addBalance", UUID.class, String.class, double.class);
            Method getBalanceMethod = economyManagerClass.getMethod("getBalance", UUID.class, String.class);
            Method formatValueMethod = economyManagerClass.getMethod("formatValue", double.class);

            plugin.getLogger().info("Hook do AlkaEconomy habilitado (COINS + ESCARION).");
            return Optional.of(new AlkaEconomyHook(economyManager, addBalanceMethod, getBalanceMethod, formatValueMethod));
        } catch (Throwable e) {
            plugin.getLogger().log(Level.WARNING, "AlkaEconomy encontrado mas a API nao carregou via reflexao.", e);
            return Optional.empty();
        }
    }

    public void deposit(UUID uuid, String currency, double amount) {
        try {
            addBalanceMethod.invoke(economyManager, uuid, currency, amount);
        } catch (Throwable e) {
            Bukkit.getLogger().log(Level.WARNING, "Erro ao depositar via AlkaEconomy.", e);
        }
    }

    public double getBalance(UUID uuid, String currency) {
        try {
            return (double) getBalanceMethod.invoke(economyManager, uuid, currency);
        } catch (Throwable e) {
            return 0.0;
        }
    }

    public String format(double amount) {
        try {
            return (String) formatValueMethod.invoke(null, amount);
        } catch (Throwable e) {
            return String.valueOf(amount);
        }
    }
}
