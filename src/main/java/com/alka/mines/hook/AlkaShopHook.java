package com.alka.mines.hook;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Ponte opcional com o AlkaShop - a mina NAO guarda nenhum preco nem sabe o que e
 * "coins": so pergunta se a auto-venda esta ativa pro jogador e, se sim, entrega o
 * drop pro AlkaShop vender em vez de por no inventario. Preco/moeda sao decisao
 * exclusiva do AlkaShop (ver AlkaShopAPI#getPrice). Integracao soft via
 * ServicesManager - AlkaShop registra sua API la.
 *
 * NUNCA importar com.alkacode.shop.api.AlkaShopAPI diretamente aqui - so ter esse
 * tipo em qualquer assinatura desta classe (campo, parametro, `.class` literal como
 * `AlkaShopAPI.class`) faz a JVM resolver a classe assim que este hook e carregado,
 * mesmo com softdepend no plugin.yml e mesmo com um check de presenca antes -
 * resultado real observado: NoClassDefFoundError ao iniciar sem o AlkaShop
 * instalado. Tudo aqui e via reflexao pra so tocar em AlkaShopAPI depois de
 * confirmar que o plugin esta presente.
 */
public final class AlkaShopHook {

    private final Object api;
    private final Method isAutoSellActiveMethod;
    private final Method isAutoSellActiveMaterialMethod;
    private final Method isSellableMethod;
    private final Method sellItemMethod;

    private AlkaShopHook(Object api, Method isAutoSellActiveMethod, Method isAutoSellActiveMaterialMethod,
                          Method isSellableMethod, Method sellItemMethod) {
        this.api = api;
        this.isAutoSellActiveMethod = isAutoSellActiveMethod;
        this.isAutoSellActiveMaterialMethod = isAutoSellActiveMaterialMethod;
        this.isSellableMethod = isSellableMethod;
        this.sellItemMethod = sellItemMethod;
    }

    public static Optional<AlkaShopHook> tryHook(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("AlkaShop") == null) {
            return Optional.empty();
        }

        try {
            Class<?> apiClass = Class.forName("com.alkacode.shop.api.AlkaShopAPI");
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(apiClass);
            if (registration == null) {
                return Optional.empty();
            }

            Method isAutoSellActiveMethod = apiClass.getMethod("isAutoSellActive", Player.class);
            Method isSellableMethod = apiClass.getMethod("isSellable", Material.class);
            Method sellItemMethod = apiClass.getMethod("sellItem", Player.class, ItemStack.class);

            // isAutoSellActive(Player, Material) e mais novo que o resto da API (auto-venda
            // por categoria/material, nao so um toggle global) - versao antiga do AlkaShop
            // instalada pode nao ter, cai pro isAutoSellActive(Player) generico nesse caso.
            Method isAutoSellActiveMaterialMethod;
            try {
                isAutoSellActiveMaterialMethod = apiClass.getMethod("isAutoSellActive", Player.class, Material.class);
            } catch (NoSuchMethodException e) {
                isAutoSellActiveMaterialMethod = null;
                plugin.getLogger().info("AlkaShop instalado nao tem auto-venda por material (versao antiga) - "
                        + "usando o toggle generico ao minerar.");
            }

            plugin.getLogger().info("Hook do AlkaShop habilitado (auto-venda ao minerar).");
            return Optional.of(new AlkaShopHook(registration.getProvider(), isAutoSellActiveMethod,
                    isAutoSellActiveMaterialMethod, isSellableMethod, sellItemMethod));
        } catch (Throwable e) {
            plugin.getLogger().log(Level.WARNING, "AlkaShop encontrado mas a API nao carregou via reflexao.", e);
            return Optional.empty();
        }
    }

    /** Preferencia de auto-venda do jogador pra este material especifico - respeita categoria/material se o AlkaShop suportar, senao cai no toggle generico. */
    public boolean isAutoSellActive(Player player, Material material) {
        try {
            if (isAutoSellActiveMaterialMethod != null) {
                return (boolean) isAutoSellActiveMaterialMethod.invoke(api, player, material);
            }
            return (boolean) isAutoSellActiveMethod.invoke(api, player);
        } catch (Throwable e) {
            return false;
        }
    }

    public boolean isSellable(Material material) {
        try {
            return (boolean) isSellableMethod.invoke(api, material);
        } catch (Throwable e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Double> sell(Player player, ItemStack item) {
        try {
            return (Map<String, Double>) sellItemMethod.invoke(api, player, item);
        } catch (Throwable e) {
            return Map.of();
        }
    }
}
