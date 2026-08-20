package com.alka.mines.hook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Ponte opcional com o AlkaVips (Server Boost / "VIP Solidario") - so consulta o
 * multiplicador de chance de reward raro pra amplificar a rolagem em
 * MineBreakListener#applyRewards. AlkaVipsBoostAPI e um servico Bukkit SEPARADO do
 * AlkaVipsAPI principal (registro independente), por isso o hook so resolve esse
 * um servico, nao precisa do resto da API de VIP.
 *
 * NUNCA importar com.alkacode.vips.api.* diretamente aqui - mesmo motivo documentado
 * no AlkaShopHook/AlkaDropHook: mismatch de versao entre os jars vira
 * NoClassDefFoundError/LinkageError capaz de derrubar o onEnable inteiro mesmo com
 * softdepend no plugin.yml.
 */
public final class AlkaVipsHook {

    private final Object boostApi;
    private final Method getMineRareChanceMultiplierMethod;

    private AlkaVipsHook(Object boostApi, Method getMineRareChanceMultiplierMethod) {
        this.boostApi = boostApi;
        this.getMineRareChanceMultiplierMethod = getMineRareChanceMultiplierMethod;
    }

    public static Optional<AlkaVipsHook> tryHook(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("AlkaVips") == null) {
            return Optional.empty();
        }
        try {
            Class<?> boostApiClass = Class.forName("com.alkacode.vips.api.AlkaVipsBoostAPI");
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(boostApiClass);
            if (registration == null) {
                return Optional.empty();
            }
            Method getMineRareChanceMultiplier = boostApiClass.getMethod("getMineRareChanceMultiplier");

            plugin.getLogger().info("Hook do AlkaVips habilitado (boost de servidor amplifica chance de reward raro).");
            return Optional.of(new AlkaVipsHook(registration.getProvider(), getMineRareChanceMultiplier));
        } catch (Throwable e) {
            plugin.getLogger().log(Level.WARNING, "AlkaVips encontrado mas AlkaVipsBoostAPI nao carregou via reflexao "
                    + "(versao antiga do AlkaVips?) - boost de reward raro desativado.", e);
            return Optional.empty();
        }
    }

    /** Multiplicador de chance de reward raro do boost de servidor ativo. 1.0 se nenhum
     * boost ativo/a chamada falhar (nunca lanca, sempre seguro de multiplicar direto na chance). */
    public double getMineRareChanceMultiplier() {
        try {
            Double value = (Double) getMineRareChanceMultiplierMethod.invoke(boostApi);
            return value != null && value > 0 ? value : 1.0;
        } catch (Throwable e) {
            return 1.0;
        }
    }
}
