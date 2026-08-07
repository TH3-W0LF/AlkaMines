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
 * Impede jogadores sem alkaminas.admin.build de colocar OU quebrar bloco em qualquer
 * lugar da mina - lobby (Mine#containsLobby, ignora Y) incluido, nao so a regiao de
 * mineracao exata. Break so libera quando o bloco esta na regiao de mineracao exata
 * (Mine#containsMining) E faz parte da composicao configurada - o MineBreakListener
 * sozinho nao distingue nada disso, ele processa qualquer bloco dentro da regiao.
 *
 * onBlockBreak roda em LOW, ANTES do MineBreakListener (HIGHEST + ignoreCancelled):
 * cancela tudo que nao e um bloco de composicao dentro da regiao de mineracao; o resto
 * (composicao de verdade) passa direto (evento continua nao-cancelado) pro
 * MineBreakListener processar a quebra oficial normalmente. Isso e proposital - um
 * segundo listener cancelando TODO BlockBreakEvent pra quem nao tem essa permissao
 * impediria jogadores comuns de minerar de verdade.
 */
public class MineProtectionListener implements Listener {

    private final MineManager mineManager;

    public MineProtectionListener(MineManager mineManager) {
        this.mineManager = mineManager;
    }

    /** Protege PLACE em toda a area da mina (lobby + mineracao). */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getPlayer().hasPermission("alkaminas.admin.build")) {
            return;
        }
        if (mineManager.getMineLobbyAt(event.getBlock().getLocation()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** Protege BREAK em toda a area da mina (lobby + mineracao) - so libera bloco de
     * composicao dentro da regiao de mineracao exata. */
    @EventHandler(priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().hasPermission("alkaminas.admin.build")) {
            return;
        }

        Mine mine = mineManager.getMineLobbyAt(event.getBlock().getLocation()).orElse(null);
        if (mine == null) {
            return;
        }

        if (mine.containsMining(event.getBlock().getLocation())) {
            Material blockType = event.getBlock().getType();
            boolean isMineable = mine.getComposition().stream().anyMatch(b -> b.getMaterial() == blockType);
            if (isMineable) {
                return;
            }
        }

        event.setCancelled(true);
    }
}
