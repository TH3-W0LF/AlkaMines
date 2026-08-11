package com.alka.mines.event;

import com.alka.mines.model.Mine;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Disparado quando um jogador sai da area de uma mina (PlayerMineTrackerListener). */
public class MineLeaveEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Mine mine;

    public MineLeaveEvent(Player player, Mine mine) {
        this.player = player;
        this.mine = mine;
    }

    public Player getPlayer() {
        return player;
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
