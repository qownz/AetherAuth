package com.aetherauth.auth;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight in-memory rate limiting. Protects against password spraying and
 * registration floods before the database is even touched.
 */
public final class RateLimiter {

    private final Map<String, Deque<Long>> loginAttempts = new ConcurrentHashMap<String, Deque<Long>>();
    private final Map<String, Long> registerTimes = new ConcurrentHashMap<String, Long>();

    public boolean allowLogin(String ip, int attempts, long windowMillis) {
        if (ip == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        Deque<Long> queue = loginAttempts.get(ip);
        if (queue == null) {
            queue = new ArrayDeque<Long>();
            loginAttempts.put(ip, queue);
        }
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst() > windowMillis) {
                queue.removeFirst();
            }
            if (queue.size() >= attempts) {
                return false;
            }
            queue.addLast(now);
            return true;
        }
    }

    public long registerCooldownRemaining(String ip, int seconds) {
        if (ip == null || seconds <= 0) {
            return 0L;
        }
        Long last = registerTimes.get(ip);
        if (last == null) {
            return 0L;
        }
        long elapsed = System.currentTimeMillis() - last;
        long remaining = seconds * 1000L - elapsed;
        return Math.max(0L, remaining);
    }

    public void markRegister(String ip) {
        if (ip != null) {
            registerTimes.put(ip, System.currentTimeMillis());
        }
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Deque<Long>> entry : loginAttempts.entrySet()) {
            Deque<Long> queue = entry.getValue();
            synchronized (queue) {
                while (!queue.isEmpty() && now - queue.peekFirst() > 600000L) {
                    queue.removeFirst();
                }
                if (queue.isEmpty()) {
                    loginAttempts.remove(entry.getKey());
                }
            }
        }
        for (Map.Entry<String, Long> entry : registerTimes.entrySet()) {
            if (now - entry.getValue() > 3600000L) {
                registerTimes.remove(entry.getKey());
            }
        }
    }
}
