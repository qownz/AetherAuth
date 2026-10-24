package com.aetherauth.storage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.aetherauth.config.ConfigData;
import com.aetherauth.crypto.CryptoEngine;

/**
 * SQLite implementation with WAL, a small read pool and serialised writes.
 */
public final class SqliteStorage implements Storage {

    private static final String COLUMNS = "uuid, name, name_lower, email_stored, email_lookup, password, algorithm, "
            + "created_at, updated_at, last_login_at, auto_login_until, last_ip, registration_ip, failed_attempts, locked_until";

    private final Path databaseFile;
    private final ConfigData.Database config;
    private final CryptoEngine crypto;
    private final boolean encryptEmails;
    private final Logger logger;
    private final ReentrantLock writeLock = new ReentrantLock(true);
    private ConnectionPool pool;

    public SqliteStorage(Path databaseFile, ConfigData.Database config, CryptoEngine crypto,
                         boolean encryptEmails, Logger logger) {
        this.databaseFile = databaseFile;
        this.config = config;
        this.crypto = crypto;
        this.encryptEmails = encryptEmails;
        this.logger = logger;
    }

    @Override
    public void init() throws Exception {
        Files.createDirectories(databaseFile.toAbsolutePath().getParent());
        String url = "jdbc:sqlite:" + databaseFile.toAbsolutePath().toString().replace('\\', '/');
        pool = new ConnectionPool(url, config.poolSize, config.busyTimeoutMs, config.synchronous, logger);
        pool.init();
        createSchema();
    }

    private void createSchema() {
        write(new SqlFunction<Void>() {
            @Override
            public Void apply(Connection connection) throws SQLException {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS aether_players ("
                            + "uuid TEXT NOT NULL PRIMARY KEY,"
                            + "name TEXT NOT NULL,"
                            + "name_lower TEXT NOT NULL UNIQUE,"
                            + "email_stored TEXT,"
                            + "email_lookup TEXT UNIQUE,"
                            + "password TEXT NOT NULL,"
                            + "algorithm TEXT NOT NULL,"
                            + "created_at INTEGER NOT NULL,"
                            + "updated_at INTEGER NOT NULL,"
                            + "last_login_at INTEGER NOT NULL DEFAULT 0,"
                            + "auto_login_until INTEGER NOT NULL DEFAULT 0,"
                            + "last_ip TEXT,"
                            + "registration_ip TEXT,"
                            + "failed_attempts INTEGER NOT NULL DEFAULT 0,"
                            + "locked_until INTEGER NOT NULL DEFAULT 0)");
                    statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_aether_players_ip "
                            + "ON aether_players(registration_ip)");
                    statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_aether_players_login "
                            + "ON aether_players(last_login_at)");
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS aether_audit ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + "uuid TEXT,"
                            + "name TEXT,"
                            + "action TEXT NOT NULL,"
                            + "ip TEXT,"
                            + "detail TEXT,"
                            + "created_at INTEGER NOT NULL)");
                    statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_aether_audit_time "
                            + "ON aether_audit(created_at)");
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS aether_meta ("
                            + "meta_key TEXT PRIMARY KEY,"
                            + "meta_value TEXT)");
                    statement.executeUpdate("INSERT OR IGNORE INTO aether_meta(meta_key, meta_value) "
                            + "VALUES('schema_version', '1')");
                }
                return null;
            }
        });
    }

    @Override
    public AuthRecord findByName(final String name) {
        if (name == null) {
            return null;
        }
        return read(new SqlFunction<AuthRecord>() {
            @Override
            public AuthRecord apply(Connection connection) throws SQLException {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT " + COLUMNS + " FROM aether_players WHERE name_lower = ?")) {
                    statement.setString(1, name.toLowerCase(java.util.Locale.ROOT));
                    try (ResultSet result = statement.executeQuery()) {
                        return result.next() ? map(result) : null;
                    }
                }
            }
        });
    }

    @Override
    public AuthRecord findByUuid(final UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return read(new SqlFunction<AuthRecord>() {
            @Override
            public AuthRecord apply(Connection connection) throws SQLException {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT " + COLUMNS + " FROM aether_players WHERE uuid = ?")) {
                    statement.setString(1, uuid.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        return result.next() ? map(result) : null;
                    }
                }
            }
        });
    }

    @Override
    public boolean emailExists(final String emailLookup) {
        if (emailLookup == null) {
            return false;
        }
        return read(new SqlFunction<Boolean>() {
            @Override
            public Boolean apply(Connection connection) throws SQLException {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT 1 FROM aether_players WHERE email_lookup = ? LIMIT 1")) {
                    statement.setString(1, emailLookup);
                    try (ResultSet result = statement.executeQuery()) {
                        return result.next();
                    }
                }
            }
        });
    }

    @Override
    public int countByRegistrationIp(final String ip) {
        if (ip == null) {
            return 0;
        }
        return read(new SqlFunction<Integer>() {
            @Override
            public Integer apply(Connection connection) throws SQLException {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM aether_players WHERE registration_ip = ?")) {
                    statement.setString(1, ip);
                    try (ResultSet result = statement.executeQuery()) {
                        return result.next() ? result.getInt(1) : 0;
                    }
                }
            }
        });
    }

    @Override
    public int countAltAccounts() {
        return read(new SqlFunction<Integer>() {
            @Override
            public Integer apply(Connection connection) throws SQLException {
                try (Statement statement = connection.createStatement();
                     ResultSet result = statement.executeQuery(
                             "SELECT COALESCE(SUM(c - 1), 0) FROM ("
                                     + "SELECT COUNT(*) AS c FROM aether_players "
                                     + "WHERE registration_ip IS NOT NULL AND registration_ip <> '' "
                                     + "GROUP BY registration_ip HAVING COUNT(*) > 1)")) {
                    return result.next() ? result.getInt(1) : 0;
                }
            }
        });
    }

    @Override
    public void insert(final AuthRecord record) throws DuplicateEntryException {
        try {
            write(new SqlFunction<Void>() {
                @Override
                public Void apply(Connection connection) throws SQLException {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO aether_players (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                        statement.setString(1, record.uuid.toString());
                        statement.setString(2, record.name);
                        statement.setString(3, record.nameLower);
                        statement.setString(4, record.emailStored);
                        statement.setString(5, record.emailLookup);
                        statement.setString(6, record.password);
                        statement.setString(7, record.algorithm);
                        statement.setLong(8, record.createdAt);
                        statement.setLong(9, record.updatedAt);
                        statement.setLong(10, record.lastLoginAt);
                        statement.setLong(11, record.autoLoginUntil);
                        statement.setString(12, record.lastIp);
                        statement.setString(13, record.registrationIp);
                        statement.setInt(14, record.failedAttempts);
                        statement.setLong(15, record.lockedUntil);
                        statement.executeUpdate();
                    }
                    return null;
                }
            });
        } catch (StorageException exception) {
            Throwable cause = exception.getCause();
            String message = cause == null ? "" : String.valueOf(cause.getMessage()).toLowerCase(java.util.Locale.ROOT);
            if (message.contains("email_lookup")) {
                throw new DuplicateEntryException("email", "E-posta zaten kayıtlı.", exception);
            }
            if (message.contains("name_lower") || message.contains("uuid")) {
                throw new DuplicateEntryException("name", "Hesap zaten kayıtlı.", exception);
            }
            throw exception;
        }
    }

    @Override
    public void updateLogin(final UUID uuid, final long time, final String ip, final long autoLoginUntil) {
        write(new SqlFunction<Void>() {
            @Override
            public Void apply(Connection connection) throws SQLException {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE aether_players SET last_login_at = ?, last_ip = ?, auto_login_until = ?, "
                                + "updated_at = ?, failed_attempts = 0, locked_until = 0 WHERE uuid = ?")) {
                    statement.setLong(1, time);
                    statement.setString(2, ip);
                    statement.setLong(3, autoLoginUntil);
                    statement.setLong(4, time);
                    statement.setString(5, uuid.toString());
                    statement.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public void clearAutoLogin(final UUID uuid) {
        write(new SqlFunction<Void>() {
            @Override
            public Void apply(Connection connection) throws SQLException {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE aether_players SET auto_login_until = 0 WHERE uuid = ?")) {
                    statement.setString(1, uuid.toString());
                    statement.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public void updatePassword(final UUID uuid, final String encoded, final String algorithm, final long time) {
        write(new SqlFunction<Void>() {
            @Override
            public Void apply(Connection connection) throws SQLException {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE aether_players SET password = ?, algorithm = ?, updated_at = ? WHERE uuid = ?")) {
                    statement.setString(1, encoded);
                    statement.setString(2, algorithm);
                    statement.setLong(3, time);
                    statement.setString(4, uuid.toString());
                    statement.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public void updateEmail(final UUID uuid, final String emailStored, final String emailLookup, final long time) {
        write(new SqlFunction<Void>() {
            @Override
            public Void apply(Connection connection) throws SQLException {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE aether_players SET email_stored = ?, email_lookup = ?, updated_at = ? WHERE uuid = ?")) {
                    statement.setString(1, emailStored);
                    statement.setString(2, emailLookup);
                    statement.setLong(3, time);
                    statement.setString(4, uuid.toString());
                    statement.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public void updateFailures(final UUID uuid, final int failures, final long lockedUntil) {
        write(new SqlFunction<Void>() {
            @Override
            public Void apply(Connection connection) throws SQLException {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE aether_players SET failed_attempts = ?, locked_until = ?, updated_at = ? WHERE uuid = ?")) {
                    statement.setInt(1, failures);
                    statement.setLong(2, lockedUntil);
                    statement.setLong(3, System.currentTimeMillis());
                    statement.setString(4, uuid.toString());
                    statement.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public void delete(final UUID uuid) {
        write(new SqlFunction<Void>() {
            @Override
            public Void apply(Connection connection) throws SQLException {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM aether_players WHERE uuid = ?")) {
                    statement.setString(1, uuid.toString());
                    statement.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public int count() {
        return read(new SqlFunction<Integer>() {
            @Override
            public Integer apply(Connection connection) throws SQLException {
                try (Statement statement = connection.createStatement();
                     ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM aether_players")) {
                    return result.next() ? result.getInt(1) : 0;
                }
            }
        });
    }

    @Override
    public void audit(final UUID uuid, final String name, final String action, final String ip, final String detail) {
        write(new SqlFunction<Void>() {
            @Override
            public Void apply(Connection connection) throws SQLException {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO aether_audit(uuid, name, action, ip, detail, created_at) VALUES (?,?,?,?,?,?)")) {
                    statement.setString(1, uuid == null ? null : uuid.toString());
                    statement.setString(2, name);
                    statement.setString(3, action);
                    statement.setString(4, ip);
                    statement.setString(5, detail);
                    statement.setLong(6, System.currentTimeMillis());
                    statement.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public void pruneAudit(final int keep) {
        if (keep < 100) {
            return;
        }
        write(new SqlFunction<Void>() {
            @Override
            public Void apply(Connection connection) throws SQLException {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM aether_audit WHERE id NOT IN "
                                + "(SELECT id FROM aether_audit ORDER BY id DESC LIMIT ?)")) {
                    statement.setInt(1, keep);
                    statement.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public void vacuumInto(final Path target) throws Exception {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Files.deleteIfExists(target);
        writeLock.lock();
        try {
            Connection connection = pool.borrow();
            try (PreparedStatement statement = connection.prepareStatement("VACUUM INTO ?")) {
                statement.setString(1, target.toAbsolutePath().toString());
                statement.execute();
            } finally {
                pool.release(connection);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public long databaseSizeBytes() {
        long size = 0L;
        File file = databaseFile.toFile();
        size += file.length();
        File wal = new File(file.getParentFile(), file.getName() + "-wal");
        if (wal.exists()) {
            size += wal.length();
        }
        return size;
    }

    @Override
    public List<AuthRecord> snapshot(final int limit) {
        return read(new SqlFunction<List<AuthRecord>>() {
            @Override
            public List<AuthRecord> apply(Connection connection) throws SQLException {
                List<AuthRecord> records = new ArrayList<AuthRecord>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT " + COLUMNS + " FROM aether_players ORDER BY created_at ASC LIMIT ?")) {
                    statement.setInt(1, Math.max(1, limit));
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            records.add(map(result));
                        }
                    }
                }
                return records;
            }
        });
    }

    private AuthRecord map(ResultSet result) throws SQLException {
        AuthRecord record = new AuthRecord();
        record.uuid = UUID.fromString(result.getString("uuid"));
        record.name = result.getString("name");
        record.nameLower = result.getString("name_lower");
        record.emailStored = result.getString("email_stored");
        record.emailLookup = result.getString("email_lookup");
        record.password = result.getString("password");
        record.algorithm = result.getString("algorithm");
        record.createdAt = result.getLong("created_at");
        record.updatedAt = result.getLong("updated_at");
        record.lastLoginAt = result.getLong("last_login_at");
        record.autoLoginUntil = result.getLong("auto_login_until");
        record.lastIp = result.getString("last_ip");
        record.registrationIp = result.getString("registration_ip");
        record.failedAttempts = result.getInt("failed_attempts");
        record.lockedUntil = result.getLong("locked_until");
        record.email = decrypt(record.emailStored);
        return record;
    }

    private String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) {
            return "";
        }
        if (!encryptEmails) {
            return stored.startsWith("p:") ? stored.substring(2) : stored;
        }
        if (crypto == null) {
            return "";
        }
        try {
            return crypto.decryptEmail(stored);
        } catch (Exception exception) {
            logger.log(Level.WARNING, "E-posta çözülemedi (secret.key değişmiş olabilir).", exception);
            return "";
        }
    }

    private <T> T read(SqlFunction<T> function) {
        Connection connection = null;
        try {
            connection = pool.borrow();
            return function.apply(connection);
        } catch (SQLException exception) {
            throw new StorageException("Veritabanı okuma hatası.", exception);
        } finally {
            if (connection != null) {
                pool.release(connection);
            }
        }
    }

    private <T> T write(SqlFunction<T> function) {
        writeLock.lock();
        Connection connection = null;
        try {
            connection = pool.borrow();
            return function.apply(connection);
        } catch (SQLException exception) {
            throw new StorageException("Veritabanı yazma hatası.", exception);
        } finally {
            if (connection != null) {
                pool.release(connection);
            }
            writeLock.unlock();
        }
    }

    @Override
    public void close() {
        if (pool != null) {
            pool.close();
        }
    }

    private interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }
}
