package com.aetherauth.api;

import java.util.function.Consumer;

import org.bukkit.entity.Player;

/**
 * A version-specific implementation of the authentication screen.
 *
 * <p>Implementations are loaded reflectively so the rest of the plugin keeps
 * working on servers where dialogs (Paper 1.21.6+) are unavailable.</p>
 */
public interface DialogBridge {

    /** Human readable bridge name, used in debug output. */
    String name();

    /** Whether the underlying server really supports this bridge. */
    boolean available();

    /**
     * Shows an authentication dialog.
     *
     * <p>The callback must be invoked exactly once, on the server thread, with
     * either the submitted values or a cancellation. Implementations are
     * responsible for guarding against double invocation.</p>
     */
    void show(Player player, DialogRequest request, Consumer<DialogOutcome> callback);

    /** Closes any open dialog for this player. */
    void close(Player player);
}
