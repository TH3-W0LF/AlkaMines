package com.alka.mines.hook;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

/**
 * Ponte opcional (estatica) com o ItemsAdder - usada so pra icone cosmetico de mina
 * (ver Mine#getIconItemsAdder, AdminMainMenu, MineListMenu). Suporte a bloco custom
 * na composicao da mina foi removido: CustomBlock.place() em massa travava o
 * servidor mesmo em lotes, e nao cancelar o BlockBreakEvent pra esses blocos ainda
 * deixava o ItemsAdder com estado interno inconsistente (ghost blocks, drop custom
 * perdido). Icone e so cosmetico (nunca colocado no mundo), entao fica de fora desse
 * problema.
 */
public final class ItemsAdderHook {

    private static volatile boolean enabled = false;

    private ItemsAdderHook() {
    }

    public static Optional<ItemsAdderHook> tryHook(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
            return Optional.empty();
        }
        enabled = true;
        plugin.getLogger().info("Hook ItemsAdder ativado.");
        return Optional.of(new ItemsAdderHook());
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static ItemStack getCustomItem(String namespace) {
        if (!enabled) {
            return null;
        }
        CustomStack stack = CustomStack.getInstance(namespace);
        return stack != null ? stack.getItemStack() : null;
    }

    /** Reconhece qualquer stack custom do ItemsAdder (bloco ou item comum) - usado so
     * pra icone cosmetico de mina, nunca colocado no mundo. */
    public static String getCustomStackNamespace(ItemStack item) {
        if (!enabled || item == null) {
            return null;
        }
        CustomStack stack = CustomStack.byItemStack(item);
        return stack != null ? stack.getNamespacedID() : null;
    }
}
