package com.alka.mines.event;

import com.alka.mines.model.Mine;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Disparado ANTES de uma mina comecar a resetar (teleporte + substituicao dos blocos),
 * como contraparte cancelavel do {@link MineResetEvent}. Cancelar aborta o reset - util
 * pra segurar reset durante eventos/guerras.
 */
public class MinePreResetEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Mine mine;
    private boolean cancelled;

    public MinePreResetEvent(Mine mine) {
        this.mine = mine;
    }

    public Mine getMine() {
        return mine;
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
