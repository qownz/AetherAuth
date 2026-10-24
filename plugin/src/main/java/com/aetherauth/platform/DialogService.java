package com.aetherauth.platform;

import java.lang.reflect.Constructor;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.aetherauth.api.DialogBridge;
import com.aetherauth.api.DialogOutcome;
import com.aetherauth.api.DialogRequest;
import com.aetherauth.config.ConfigData;
import com.aetherauth.config.ConfigManager;
import com.aetherauth.util.ServerVersion;

import org.bukkit.entity.Player;

/**
 * Loads the Java 21 dialog bridge only when the server can actually run it.
 * Everything about this class is defensive: a missing class, an old Java
 * runtime or a Paper API change simply disables the GUI and keeps the command
 * fallback alive.
 */
public final class DialogService {

    private static final String BRIDGE_CLASS = "com.aetherauth.dialog.PaperDialogBridge";

    private final DialogBridge bridge;
    private final String status;

    public DialogService(ConfigManager config, ServerVersion version, Logger logger) {
        ConfigData.Gui gui = config.gui();
        if ("command".equals(gui.mode)) {
            this.bridge = null;
            this.status = "disabled (gui.mode=command)";
            return;
        }
        if (!version.hasDialogApi()) {
            this.bridge = null;
            this.status = "unsupported (requires Paper 1.21.6+)";
            return;
        }
        DialogBridge loaded = null;
        String reason = "unavailable";
        try {
            Class<?> type = Class.forName(BRIDGE_CLASS);
            Constructor<?> constructor = type.getDeclaredConstructor();
            Object instance = constructor.newInstance();
            if (instance instanceof DialogBridge) {
                loaded = (DialogBridge) instance;
                if (!loaded.available()) {
                    reason = "bridge reported unavailable";
                    loaded = null;
                }
            }
        } catch (Throwable throwable) {
            reason = throwable.getClass().getSimpleName();
            logger.log(Level.FINE, "Dialog köprüsü yüklenemedi: " + reason, throwable);
        }
        this.bridge = loaded;
        this.status = loaded == null ? "unavailable (" + reason + ")" : loaded.name();
    }

    public boolean available() {
        return bridge != null;
    }

    public String status() {
        return status;
    }

    public void show(Player player, DialogRequest request, Consumer<DialogOutcome> callback) {
        if (bridge == null) {
            callback.accept(DialogOutcome.cancel());
            return;
        }
        try {
            bridge.show(player, request, callback);
        } catch (Throwable throwable) {
            callback.accept(DialogOutcome.cancel());
        }
    }

    public void close(Player player) {
        if (bridge == null || player == null) {
            return;
        }
        try {
            bridge.close(player);
        } catch (Throwable ignored) {
        }
    }
}
