package com.alka.mines.listener;

import com.alka.mines.manager.MineManager;
import com.alka.mines.model.Mine;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Impede jogadores sem alkaminas.admin.build de colocar blocos dentro da area de uma
 * mina (evita poluir a composicao configurada), e de quebrar blocos que NAO fazem
 * parte da composicao configurada (bedrock, estrutura, grama que por acaso esteja
 * dentro da selecao do WorldEdit) - o MineBreakListener sozinho nao distingue isso,
 * ele processa qualquer bloco dentro da regiao.
 *
 * onBlockBreak roda em LOW, ANTES do MineBreakListener (HIGHEST + ignoreCancelled):
 * cancela so o que NAO e da composicao; blocos da composicao passam direto (evento
 * continua nao-cancelado) pro MineBreakListener processar a quebra oficial normalmente.
 * Isso e proposital - um segundo listener cancelando TODO BlockBreakEvent pra quem nao
 * tem essa permissao impediria jogadores comuns de minerar de verdade.
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

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().hasPermission("alkaminas.admin.build")) {
            return;
        }

        Mine mine = mineManager.getMineAt(event.getBlock().getLocation()).orElse(null);
        if (mine == null) {
            return;
        }

        Material blockType = event.getBlock().getType();
        boolean isMineable = mine.getComposition().stream().anyMatch(b -> b.getMaterial() == blockType);
        if (!isMineable) {
            event.setCancelled(true);
        }
    }
}
