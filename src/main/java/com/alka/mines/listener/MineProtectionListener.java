package com.alka.mines.listener;

import com.alka.mines.manager.MineManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Impede jogadores sem alkaminas.admin.build de colocar blocos dentro da area de uma
 * mina (evita poluir a composicao configurada). Nao mexe em BlockBreakEvent aqui - isso
 * ja e responsabilidade exclusiva do MineBreakListener (HIGHEST + ignoreCancelled), que
 * cancela e processa a quebra oficial de qualquer jogador dentro de uma mina. Um segundo
 * listener cancelando BlockBreakEvent para quem nao tem essa permissao rodaria ANTES
 * (prioridade NORMAL) e impediria jogadores comuns de minerar de verdade.
 */
public class MineProtectionListener implements Listener {

    private final MineManager mineManager;

    public MineProtectionListener(MineManager mineManager) {
        this.mineManager = mineManager;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (mineManager.getMineAt(event.getBlock().getLocation()).isPresent()
                && !event.getPlayer().hasPermission("alkaminas.admin.build")) {
            event.setCancelled(true);
        }
    }
}
