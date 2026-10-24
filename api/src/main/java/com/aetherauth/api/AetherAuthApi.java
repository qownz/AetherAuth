package com.aetherauth.api;

import java.util.UUID;

import org.bukkit.entity.Player;

/**
 * Public service exposed through Bukkit's services manager.
 */
public interface AetherAuthApi {

    /** Whether the player completed authentication in this session. */
    boolean isAuthenticated(Player player);

    /** UUID based variant, usable from asynchronous plugin code. */
    boolean isAuthenticated(UUID uuid);

    /** Number of players that are currently authenticated. */
    int authenticatedCount();

    /**
     * Full e-mail address of an authenticated player, or an empty string when
     * unknown. Plugins should treat this as personal data.
     */
    String email(Player player);

    /**
     * Ends the player's session and puts them back into the login state.
     * Must be called from the server thread.
     */
    void logout(Player player);

    /**
     * Marks the player as authenticated without a password check. Intended for
     * trusted integrations (e.g. a proxy that already authenticated them).
     * Must be called from the server thread.
     */
    boolean forceLogin(Player player);
}
