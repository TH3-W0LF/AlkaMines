package com.alka.mines.hook;

import com.alkacode.shop.api.AlkaShopAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Optional;

/**
 * Ponte opcional com o AlkaShop - a mina NAO guarda nenhum preco nem sabe o que e
 * "coins": so pergunta se a auto-venda esta ativa pro jogador e, se sim, entrega o
 * drop pro AlkaShop vender em vez de por no inventario. Preco/moeda sao decisao
 * exclusiva do AlkaShop (ver AlkaShopAPI#getPrice). Integracao soft via
 * ServicesManager - AlkaShop registra sua API la, entao funciona mesmo que a mina
 * so tenha `compileOnly` (nunca `depend`) sobre o AlkaShop.
 */
public final class AlkaShopHook {

    private final AlkaShopAPI api;

    private AlkaShopHook(AlkaShopAPI api) {
        this.api = api;
    }

    public static Optional<AlkaShopHook> tryHook(JavaPlugin plugin) {
        RegisteredServiceProvider<AlkaShopAPI> registration = Bukkit.getServicesManager().getRegistration(AlkaShopAPI.class);
        if (registration == null) {
            return Optional.empty();
        }
        plugin.getLogger().info("Hook do AlkaShop habilitado (auto-venda ao minerar).");
        return Optional.of(new AlkaShopHook(registration.getProvider()));
    }

    public boolean isAutoSellActive(Player player) {
        return api.isAutoSellActive(player);
    }

    public boolean isSellable(org.bukkit.Material material) {
        return api.isSellable(material);
    }

    public Map<String, Double> sell(Player player, ItemStack item) {
        return api.sellItem(player, item);
    }
}
