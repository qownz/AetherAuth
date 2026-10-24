package com.aetherauth.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after a player logged out and was put back into the login state.
 */
public class AetherAuthLogoutEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;

    public AetherAuthLogoutEvent(Player player) {
        super(false);
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
