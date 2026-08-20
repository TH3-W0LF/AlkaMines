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
    // API principal (perk tree) e um servico SEPARADO do boost - pode existir um sem o
    // outro dependendo da versao/config do AlkaVips instalado, por isso null-safe solto.
    private final Object vipsApi;
    private final Method getExtraMineSlotsMethod;

    private AlkaVipsHook(Object boostApi, Method getMineRareChanceMultiplierMethod,
                          Object vipsApi, Method getExtraMineSlotsMethod) {
        this.boostApi = boostApi;
        this.getMineRareChanceMultiplierMethod = getMineRareChanceMultiplierMethod;
        this.vipsApi = vipsApi;
        this.getExtraMineSlotsMethod = getExtraMineSlotsMethod;
    }

    public static Optional<AlkaVipsHook> tryHook(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("AlkaVips") == null) {
            return Optional.empty();
        }

        Object boostApi = null;
        Method getMineRareChanceMultiplier = null;
        try {
            Class<?> boostApiClass = Class.forName("com.alkacode.vips.api.AlkaVipsBoostAPI");
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(boostApiClass);
            if (registration != null) {
                boostApi = registration.getProvider();
                getMineRareChanceMultiplier = boostApiClass.getMethod("getMineRareChanceMultiplier");
            }
        } catch (Throwable e) {
            plugin.getLogger().log(Level.WARNING, "AlkaVips encontrado mas AlkaVipsBoostAPI nao carregou via reflexao "
                    + "(versao antiga do AlkaVips?) - boost de reward raro desativado.", e);
        }

        Object vipsApi = null;
        Method getExtraMineSlots = null;
        try {
            Class<?> vipsApiClass = Class.forName("com.alkacode.vips.api.AlkaVipsAPI");
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(vipsApiClass);
            if (registration != null) {
                vipsApi = registration.getProvider();
                getExtraMineSlots = vipsApiClass.getMethod("getExtraMineSlots", java.util.UUID.class);
            }
        } catch (Throwable e) {
            plugin.getLogger().log(Level.WARNING, "AlkaVips encontrado mas AlkaVipsAPI (perk tree) nao carregou via reflexao "
                    + "(versao antiga do AlkaVips?) - slots extra de mina particular desativados.", e);
        }

        if (boostApi == null && vipsApi == null) {
            return Optional.empty();
        }
        plugin.getLogger().info("Hook do AlkaVips habilitado"
                + (boostApi != null ? " (boost de servidor amplifica chance de reward raro)" : "")
                + (vipsApi != null ? " (perk tree concede slots extra de mina particular)" : "") + ".");
        return Optional.of(new AlkaVipsHook(boostApi, getMineRareChanceMultiplier, vipsApi, getExtraMineSlots));
    }

    /** Multiplicador de chance de reward raro do boost de servidor ativo. 1.0 se nenhum
     * boost ativo/a chamada falhar (nunca lanca, sempre seguro de multiplicar direto na chance). */
    public double getMineRareChanceMultiplier() {
        if (boostApi == null) {
            return 1.0;
        }
        try {
            Double value = (Double) getMineRareChanceMultiplierMethod.invoke(boostApi);
            return value != null && value > 0 ? value : 1.0;
        } catch (Throwable e) {
            return 1.0;
        }
    }

    /** Slots extra de mina particular concedidos pelos perks EXTRA_MINE_SLOT do jogador.
     * 0 se nenhum/API indisponivel/a chamada falhar. A API do AlkaVips devolve
     * CompletableFuture (contrato async), mas a implementacao atual sempre resolve na
     * hora (le de um cache em memoria) - getNow(0) nunca bloqueia a main thread aqui,
     * e se um dia virar genuinamente async, o pior caso e devolver 0 nessa chamada. */
    public int getExtraMineSlots(java.util.UUID owner) {
        if (vipsApi == null) {
            return 0;
        }
        try {
            Object future = getExtraMineSlotsMethod.invoke(vipsApi, owner);
            if (future instanceof java.util.concurrent.CompletableFuture<?> cf) {
                @SuppressWarnings("unchecked")
                java.util.concurrent.CompletableFuture<Integer> typed = (java.util.concurrent.CompletableFuture<Integer>) cf;
                Integer value = typed.getNow(0);
                return value != null ? value : 0;
            }
            return 0;
        } catch (Throwable e) {
            return 0;
        }
    }
}
