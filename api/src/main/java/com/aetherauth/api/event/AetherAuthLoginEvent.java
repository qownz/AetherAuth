package com.aetherauth.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after a player successfully authenticated. This is a notification;
 * cancelling it is not supported (use the register event for pre-flight
 * checks). Fired on the server thread.
 */
public class AetherAuthLoginEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final boolean automatic;

    public AetherAuthLoginEvent(Player player, boolean automatic) {
        super(false);
        this.player = player;
        this.automatic = automatic;
    }

    public Player getPlayer() {
        return player;
    }

    /** True when the login came from a remembered IP session. */
    public boolean isAutomatic() {
        return automatic;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
