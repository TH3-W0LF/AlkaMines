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
    private final Method isSellableMethod;
    private final Method sellItemMethod;

    private AlkaShopHook(Object api, Method isAutoSellActiveMethod, Method isSellableMethod, Method sellItemMethod) {
        this.api = api;
        this.isAutoSellActiveMethod = isAutoSellActiveMethod;
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

            plugin.getLogger().info("Hook do AlkaShop habilitado (auto-venda ao minerar).");
            return Optional.of(new AlkaShopHook(registration.getProvider(), isAutoSellActiveMethod, isSellableMethod, sellItemMethod));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.WARNING, "AlkaShop encontrado mas a API nao carregou via reflexao.", e);
            return Optional.empty();
        }
    }

    public boolean isAutoSellActive(Player player) {
        try {
            return (boolean) isAutoSellActiveMethod.invoke(api, player);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public boolean isSellable(Material material) {
        try {
            return (boolean) isSellableMethod.invoke(api, material);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Double> sell(Player player, ItemStack item) {
        try {
            return (Map<String, Double>) sellItemMethod.invoke(api, player, item);
        } catch (ReflectiveOperationException e) {
            return Map.of();
        }
    }
}
