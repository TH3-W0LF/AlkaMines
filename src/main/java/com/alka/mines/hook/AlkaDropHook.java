package com.alka.mines.hook;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Ponte opcional com o AlkaDrop - a mina entrega o drop bruto do bloco (block.
 * getDrops), mas se o jogador tiver auto-smelt/auto-condensar ativo no AlkaDrop,
 * o drop passa por esse processamento antes de ir pro inventario/auto-venda, do
 * mesmo jeito que ja aconteceria minerando fora da mina. A mina nao sabe nada de
 * receita de smelt/condense - so pergunta "processa esse drop pra esse jogador?".
 * Integracao via ServicesManager (mesmo padrao do AlkaShopHook nesta classe) -
 * NUNCA importar com.alkacode.drop.api.AlkaDropAPI diretamente aqui, so via
 * reflexao.
 *
 * IMPORTANTE (licao de um incidente real): captura {@link Throwable}, NAO so
 * {@link ReflectiveOperationException} - um mismatch de versao entre os dois jars
 * (ex: jar do AlkaDrop desatualizado no servidor) lanca um Error (NoSuchMethodError/
 * NoClassDefFoundError), que NAO e um Exception e escapava do catch estreito de
 * antes, derrubando o BlockBreakListener inteiro no meio (bloco ja removido, resto
 * do handler nunca rodava - minerar parava de funcionar por completo). Nenhuma
 * falha aqui pode propagar pra fora desta classe.
 */
public final class AlkaDropHook {

    private final JavaPlugin plugin;
    private final Object api;
    private final Method trySmeltMethod;
    private final Method tryCondenseInventoryMethod;
    private final Method deliverDropsMethod;

    private AlkaDropHook(JavaPlugin plugin, Object api, Method trySmeltMethod, Method tryCondenseInventoryMethod,
                          Method deliverDropsMethod) {
        this.plugin = plugin;
        this.api = api;
        this.trySmeltMethod = trySmeltMethod;
        this.tryCondenseInventoryMethod = tryCondenseInventoryMethod;
        this.deliverDropsMethod = deliverDropsMethod;
    }

    public static Optional<AlkaDropHook> tryHook(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("AlkaDrop") == null) {
            return Optional.empty();
        }

        try {
            Class<?> apiClass = Class.forName("com.alkacode.drop.api.AlkaDropAPI");
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(apiClass);
            if (registration == null) {
                return Optional.empty();
            }

            Method trySmeltMethod = apiClass.getMethod("trySmelt", Player.class, List.class, boolean.class);
            Method tryCondenseInventoryMethod = apiClass.getMethod("tryCondenseInventory", Player.class);
            Method deliverDropsMethod = apiClass.getMethod("deliverDrops", Player.class, List.class, Location.class);

            plugin.getLogger().info("Hook do AlkaDrop habilitado (auto-smelt/auto-condensar/coleta tambem valem dentro da mina).");
            return Optional.of(new AlkaDropHook(plugin, registration.getProvider(), trySmeltMethod, tryCondenseInventoryMethod,
                    deliverDropsMethod));
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "AlkaDrop encontrado mas a API nao carregou (versao incompativel?) - "
                    + "auto-smelt/condensar/coleta nao vao valer dentro da mina.", t);
            return Optional.empty();
        }
    }

    /** Nunca lanca - qualquer falha (inclusive Error) vira log + no-op, minerar nunca pode quebrar por causa disso. */
    public long trySmelt(Player player, List<ItemStack> drops, boolean silkTouch) {
        try {
            return (long) trySmeltMethod.invoke(api, player, drops, silkTouch);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Hook do AlkaDrop falhou em trySmelt - ignorado.", t);
            return 0;
        }
    }

    /** Nunca lanca - qualquer falha (inclusive Error) vira log + no-op, minerar nunca pode quebrar por causa disso. */
    public long tryCondenseInventory(Player player) {
        try {
            return (long) tryCondenseInventoryMethod.invoke(api, player);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Hook do AlkaDrop falhou em tryCondenseInventory - ignorado.", t);
            return 0;
        }
    }

    /**
     * Entrega os drops respeitando a preferencia de coleta do jogador (inventario
     * vs chao). Nunca lanca - se a chamada falhar por qualquer motivo, cai no
     * fallback direto pro inventario em vez de perder o item.
     */
    public void deliverDrops(Player player, List<ItemStack> drops, Location location) {
        try {
            deliverDropsMethod.invoke(api, player, drops, location);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Hook do AlkaDrop falhou em deliverDrops - usando fallback direto pro inventario.", t);
            fallbackDeliver(player, drops, location);
        }
    }

    private void fallbackDeliver(Player player, List<ItemStack> drops, Location location) {
        for (ItemStack drop : drops) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(drop);
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItemNaturally(location, item);
            }
        }
    }
}
