package com.alka.mines.hook;

import dev.lone.itemsadder.api.CustomBlock;
import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

/**
 * Ponte opcional (estatica) com o ItemsAdder - blocos/itens custom podem entrar na
 * composicao de uma mina (ver MineBlock#isCustomBlock) ou dar XP extra de mcMMO por
 * namespace (ver McMMOHook#addMiningXp). Estatico porque e consultado em hot-path de
 * reset de regiao inteira (FAWEHook) e no evento de quebra - carregar um
 * Optional<ItemsAdderHook> so pra isso nao traria beneficio sobre o flag estatico.
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

    public static boolean isCustomBlock(Block block) {
        return enabled && block != null && CustomBlock.byAlreadyPlaced(block) != null;
    }

    public static String getBlockNamespace(Block block) {
        if (!enabled || block == null) {
            return null;
        }
        CustomBlock customBlock = CustomBlock.byAlreadyPlaced(block);
        return customBlock != null ? customBlock.getNamespacedID() : null;
    }

    public static boolean placeCustomBlock(String namespace, Location location) {
        return enabled && location != null && CustomBlock.place(namespace, location) != null;
    }

    public static ItemStack getCustomBlockItem(String namespace) {
        if (!enabled) {
            return null;
        }
        CustomBlock customBlock = CustomBlock.getInstance(namespace);
        return customBlock != null ? customBlock.getItemStack() : null;
    }

    /** So reconhece blocos custom PLACEAVEIS - usado pra decidir se um item segurado
     * pode virar entrada de composicao (ver BlockCompositionMenu#addBlock). */
    public static String getCustomBlockNamespace(ItemStack item) {
        if (!enabled || item == null) {
            return null;
        }
        CustomBlock customBlock = CustomBlock.byItemStack(item);
        return customBlock != null ? customBlock.getNamespacedID() : null;
    }

    /** Reconhece QUALQUER stack custom do ItemsAdder (bloco ou item comum) - usado so
     * pra icone cosmetico de mina (ver AdminMainMenu), que nunca e colocado no mundo. */
    public static String getCustomStackNamespace(ItemStack item) {
        if (!enabled || item == null) {
            return null;
        }
        CustomStack stack = CustomStack.byItemStack(item);
        return stack != null ? stack.getNamespacedID() : null;
    }
}
