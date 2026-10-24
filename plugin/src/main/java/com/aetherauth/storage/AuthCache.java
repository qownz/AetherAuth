package com.aetherauth.storage;

import java.util.UUID;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded, access-ordered cache. A public server can have hundreds of
 * thousands of accounts; keeping only the hot set in memory avoids loading the
 * whole table at startup while still removing a database round trip from the
 * common login path.
 */
public final class AuthCache {

    private final int maxEntries;
    private final LinkedHashMap<String, AuthRecord> entries;

    public AuthCache(int maxEntries) {
        this.maxEntries = Math.max(64, maxEntries);
        this.entries = new LinkedHashMap<String, AuthRecord>(128, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, AuthRecord> eldest) {
                return size() > AuthCache.this.maxEntries;
            }
        };
    }

    public synchronized AuthRecord get(String name) {
        if (name == null) {
            return null;
        }
        return entries.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    public synchronized void put(AuthRecord record) {
        if (record == null || record.nameLower == null) {
            return;
        }
        entries.put(record.nameLower, record);
    }

    public synchronized void invalidate(String name) {
        if (name == null) {
            return;
        }
        entries.remove(name.toLowerCase(java.util.Locale.ROOT));
    }

    public synchronized void remove(UUID uuid) {
        Iterator<Map.Entry<String, AuthRecord>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().uuid.equals(uuid)) {
                iterator.remove();
            }
        }
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized int size() {
        return entries.size();
    }
}
