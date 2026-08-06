package com.alka.mines.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * GUI generico sem lib externa: tamanho dinamico (arredonda pra cima pro proximo
 * multiplo de 9), itens com Component (displayName/lore) e um Consumer por slot.
 * Registre {@link MenuListener} uma unica vez no onEnable - ele cancela todo clique e
 * drag nos menus construidos aqui (impede duplo-clique/shift-click "roubando" item do
 * menu) e despacha o clique pro Consumer daquele slot, se houver.
 */
public class MenuBuilder {

    private final Inventory inventory;
    private final Holder holder;

    public MenuBuilder(int size, Component title) {
        int rows = Math.max(1, (int) Math.ceil(size / 9.0));
        this.holder = new Holder();
        this.inventory = Bukkit.createInventory(holder, rows * 9, title);
        this.holder.inventory = inventory;
    }

    public MenuBuilder item(int slot, ItemStack item, Consumer<InventoryClickEvent> onClick) {
        inventory.setItem(slot, item);
        if (onClick != null) {
            holder.handlers.put(slot, onClick);
        }
        return this;
    }

    public MenuBuilder item(int slot, Material material, Component displayName, List<Component> lore, Consumer<InventoryClickEvent> onClick) {
        return item(slot, buildItem(material, displayName, lore), onClick);
    }

    /** Preenche a borda (linha superior/inferior + colunas laterais) com um item decorativo, so nos slots ainda vazios. */
    public MenuBuilder fillBorder(Material material) {
        int size = inventory.getSize();
        int rows = size / 9;
        ItemStack filler = buildItem(material, Component.empty(), List.of());
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            if ((row == 0 || row == rows - 1 || col == 0 || col == 8) && inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
        return this;
    }

    public static ItemStack buildItem(Material material, Component displayName, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(displayName);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public Inventory build() {
        return inventory;
    }

    public static class Holder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, Consumer<InventoryClickEvent>> handlers = new HashMap<>();

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    /** Registrar uma unica vez: {@code getServer().getPluginManager().registerEvents(new MenuBuilder.MenuListener(), this);} */
    public static class MenuListener implements Listener {

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder() instanceof Holder holder)) {
                return;
            }
            event.setCancelled(true);

            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }

            Consumer<InventoryClickEvent> handler = holder.handlers.get(event.getSlot());
            if (handler != null) {
                handler.accept(event);
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            if (event.getInventory().getHolder() instanceof Holder) {
                event.setCancelled(true);
            }
        }
    }
}
