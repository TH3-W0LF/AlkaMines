package com.alka.mines.event;

import com.alka.mines.model.Mine;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Disparado quando uma mina e deletada (MineManager#deleteMine). */
public class MineDeleteEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Mine mine;

    public MineDeleteEvent(Mine mine) {
        this.mine = mine;
    }

    public Mine getMine() {
        return mine;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
