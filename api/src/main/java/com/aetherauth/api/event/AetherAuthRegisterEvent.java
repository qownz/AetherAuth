package com.aetherauth.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired before a new account is written. Cancelling aborts the registration
 * (the player stays in the registration screen). Fired on the server thread.
 */
public class AetherAuthRegisterEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String email;
    private boolean cancelled;

    public AetherAuthRegisterEvent(Player player, String email) {
        super(false);
        this.player = player;
        this.email = email;
    }

    public Player getPlayer() {
        return player;
    }

    public String getEmail() {
        return email;
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
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
