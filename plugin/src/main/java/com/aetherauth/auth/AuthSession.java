package com.aetherauth.auth;

import java.util.UUID;

import com.aetherauth.platform.BossBarHandle;
import com.aetherauth.storage.AuthRecord;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

/**
 * Per-player state. Lives only while the player is online.
 */
public final class AuthSession {

    public final UUID uuid;
    public final String name;
    public final long joinedAt = System.currentTimeMillis();

    public volatile AuthState state = AuthState.LOADING;
    public volatile AuthRecord record;
    public volatile Location returnLocation;
    public volatile boolean processing;
    public volatile boolean dialogOpen;
    public volatile boolean timedOut;
    public volatile int attempts;
    public volatile long lockedUntil;
    public volatile long lastActivityAt = System.currentTimeMillis();
    public volatile String lastEmail = "";

    public volatile BukkitTask timeoutTask;
    public volatile BukkitTask hudTask;
    public volatile BukkitTask dialogTask;
    public volatile BossBarHandle bossBar;

    public AuthSession(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public boolean isAuthenticated() {
        return state == AuthState.AUTHENTICATED;
    }

    public boolean awaitingPassword() {
        return state == AuthState.LOGIN;
    }

    public boolean awaitingRegistration() {
        return state == AuthState.REGISTER;
    }
}
