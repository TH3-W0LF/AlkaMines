package com.alka.mines.event;

import com.alka.mines.model.Mine;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Disparado quando um jogador quebra um bloco de composicao dentro de uma mina, ANTES
 * do drop/XP serem processados. Cancelar impede a quebra (o bloco fica no lugar) -
 * outros plugins Alka* podem usar pra bloquear mineração (evento, cooldown, etc).
 */
public class MineBlockBreakEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Mine mine;
    private final Block block;
    private boolean cancelled;

    public MineBlockBreakEvent(Player player, Mine mine, Block block) {
        this.player = player;
        this.mine = mine;
        this.block = block;
    }

    public Player getPlayer() {
        return player;
    }

    public Mine getMine() {
        return mine;
    }

    public Block getBlock() {
        return block;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
