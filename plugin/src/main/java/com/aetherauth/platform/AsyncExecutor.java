package com.aetherauth.platform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Small named worker pool for password hashing and database access. Never used
 * for Bukkit API calls.
 */
public final class AsyncExecutor implements AutoCloseable {

    private final ExecutorService executor;
    private final Logger logger;
    private final Map<String, Boolean> rejectedOnce = new ConcurrentHashMap<String, Boolean>();

    public AsyncExecutor(int threads, final Logger logger) {
        this.logger = logger;
        final AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "AetherAuth-Worker-" + counter.incrementAndGet());
                thread.setDaemon(true);
                thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread t, Throwable e) {
                        logger.log(Level.SEVERE, "AetherAuth worker hatası (" + t.getName() + ")", e);
                    }
                });
                return thread;
            }
        };
        this.executor = Executors.newFixedThreadPool(Math.max(1, threads), factory);
    }

    public void execute(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException exception) {
            String key = String.valueOf(task.getClass().getName());
            if (rejectedOnce.putIfAbsent(key, Boolean.TRUE) == null) {
                logger.log(Level.WARNING, "Kapatma sırasında bir görev reddedildi.", exception);
            }
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
