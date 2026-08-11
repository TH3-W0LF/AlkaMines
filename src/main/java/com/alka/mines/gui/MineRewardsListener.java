package com.alka.mines.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class MineRewardsListener implements Listener {

    private final MineRewardsMenu menu;

    public MineRewardsListener(MineRewardsMenu menu) {
        this.menu = menu;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MineRewardsMenu.Holder)) {
            return;
        }

        // clique no inventario do PROPRIO jogador (bottom) - nunca cancelar aqui, senao o
        // jogador nunca consegue pegar o item pro cursor pra "arrastar" depois.
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        event.setCancelled(true);
        menu.handleClick(event);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MineRewardsMenu.Holder) {
            event.setCancelled(true);
        }
    }
}
