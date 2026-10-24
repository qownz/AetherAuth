package com.aetherauth.storage;

import java.util.UUID;

/**
 * One account row. Mutable because the same instance is kept in the in-memory
 * cache and updated after successful logins.
 */
public final class AuthRecord {

    public UUID uuid;
    public String name;
    public String nameLower;
    public String email;
    public String emailLookup;
    public String emailStored;
    public String password;
    public String algorithm;
    public long createdAt;
    public long updatedAt;
    public long lastLoginAt;
    public long autoLoginUntil;
    public String lastIp;
    public String registrationIp;
    public int failedAttempts;
    public long lockedUntil;

    public AuthRecord copy() {
        AuthRecord copy = new AuthRecord();
        copy.uuid = uuid;
        copy.name = name;
        copy.nameLower = nameLower;
        copy.email = email;
        copy.emailLookup = emailLookup;
        copy.emailStored = emailStored;
        copy.password = password;
        copy.algorithm = algorithm;
        copy.createdAt = createdAt;
        copy.updatedAt = updatedAt;
        copy.lastLoginAt = lastLoginAt;
        copy.autoLoginUntil = autoLoginUntil;
        copy.lastIp = lastIp;
        copy.registrationIp = registrationIp;
        copy.failedAttempts = failedAttempts;
        copy.lockedUntil = lockedUntil;
        return copy;
    }

    public boolean isLocked(long now) {
        return lockedUntil > now;
    }
}
