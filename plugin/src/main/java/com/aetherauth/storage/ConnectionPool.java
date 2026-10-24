package com.aetherauth.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tiny JDBC pool tuned for SQLite.
 *
 * <p>SQLite only needs one writer at a time; the pool exists so concurrent
 * read operations (login lookups, admin info) never block each other. Write
 * statements are additionally serialised by {@link SqliteStorage}.</p>
 */
final class ConnectionPool implements AutoCloseable {

    private final String url;
    private final int size;
    private final int busyTimeoutMs;
    private final String synchronous;
    private final Logger logger;
    private final BlockingQueue<Connection> idle;
    private final Set<Connection> all = Collections.newSetFromMap(new ConcurrentHashMap<Connection, Boolean>());
    private final AtomicBoolean closed = new AtomicBoolean(false);

    ConnectionPool(String url, int size, int busyTimeoutMs, String synchronous, Logger logger) {
        this.url = url;
        this.size = Math.max(1, size);
        this.busyTimeoutMs = busyTimeoutMs;
        this.synchronous = synchronous;
        this.logger = logger;
        this.idle = new ArrayBlockingQueue<Connection>(this.size);
    }

    void init() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("SQLite JDBC sürücüsü bulunamadı.", exception);
        }
        List<Connection> created = new ArrayList<Connection>(size);
        try {
            for (int i = 0; i < size; i++) {
                Connection connection = open();
                created.add(connection);
                idle.offer(connection);
            }
        } catch (SQLException exception) {
            for (Connection connection : created) {
                closeQuietly(connection);
            }
            throw exception;
        }
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        all.add(connection);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=" + busyTimeoutMs);
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA temp_store=MEMORY");
            statement.execute("PRAGMA synchronous=" + synchronous);
            statement.execute("PRAGMA journal_mode=WAL");
        }
        return connection;
    }

    Connection borrow() throws SQLException {
        if (closed.get()) {
            throw new SQLException("Bağlantı havuzu kapatıldı.");
        }
        Connection connection = null;
        try {
            connection = idle.poll(busyTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SQLException("Bağlantı beklenirken kesildi.", exception);
        }
        if (connection == null) {
            connection = open();
            logger.fine("Bağlantı havuzu geçici olarak büyütüldü.");
        }
        if (connection.isClosed()) {
            all.remove(connection);
            connection = open();
        }
        return connection;
    }

    void release(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            if (closed.get() || connection.isClosed()) {
                closeQuietly(connection);
                all.remove(connection);
                return;
            }
            if (!idle.offer(connection)) {
                closeQuietly(connection);
                all.remove(connection);
            }
        } catch (SQLException exception) {
            closeQuietly(connection);
            all.remove(connection);
        }
    }

    int size() {
        return size;
    }

    private void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException exception) {
            logger.log(Level.FINE, "SQLite bağlantısı kapatılırken hata.", exception);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (Connection connection : all) {
            closeQuietly(connection);
        }
        all.clear();
        idle.clear();
    }
}
