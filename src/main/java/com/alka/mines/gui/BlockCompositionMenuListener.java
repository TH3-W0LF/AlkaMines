package com.alka.mines.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class BlockCompositionMenuListener implements Listener {

    private final BlockCompositionMenu menu;

    public BlockCompositionMenuListener(BlockCompositionMenu menu) {
        this.menu = menu;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BlockCompositionMenu.Holder holder)) {
            return;
        }

        // clique no inventario do PROPRIO jogador (bottom) - nunca cancelar aqui, senao o
        // jogador nunca consegue nem pegar o item pro cursor pra "arrastar" pro menu depois.
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        event.setCancelled(true);
        menu.handleClick(event, holder.mineId());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof BlockCompositionMenu.Holder) {
            event.setCancelled(true);
        }
    }
}
