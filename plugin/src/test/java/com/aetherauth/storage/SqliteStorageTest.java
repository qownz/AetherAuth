package com.aetherauth.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

import com.aetherauth.config.ConfigData;
import com.aetherauth.crypto.CryptoEngine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteStorageTest {

    private static final byte[] MASTER =
            "storage-test-master-secret-0123456789ab".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    private SqliteStorage storage;
    private CryptoEngine crypto;

    @BeforeEach
    void open() throws Exception {
        ConfigData.Database config = new ConfigData.Database();
        config.poolSize = 2;
        crypto = new CryptoEngine(MASTER, 1000, "GCM", 32);
        storage = new SqliteStorage(directory.resolve("aetherauth.db"), config, crypto, true,
                Logger.getLogger("AetherAuthTest"));
        storage.init();
    }

    @AfterEach
    void close() {
        if (storage != null) {
            storage.close();
        }
    }

    @Test
    void insertsAndFindsAnAccountCaseInsensitively() throws Exception {
        AuthRecord record = record("Ada", "ada@example.com");
        storage.insert(record);

        AuthRecord found = storage.findByName("ADA");
        assertNotNull(found);
        assertEquals(record.uuid, found.uuid);
        assertEquals("Ada", found.name);
        assertEquals("ada@example.com", found.email);
        assertEquals("127.0.0.1", found.registrationIp);
        assertTrue(crypto.verify("Kartal-42", found.password).ok());
        assertEquals(found.uuid, storage.findByUuid(record.uuid).uuid);
        assertNull(storage.findByName("missing"));
    }

    @Test
    void rejectsDuplicateNames() throws Exception {
        storage.insert(record("Ada", "ada@example.com"));
        AuthRecord duplicate = record("ada", "other@example.com");
        assertThrows(DuplicateEntryException.class, () -> storage.insert(duplicate));
    }

    @Test
    void tracksEmailUniquenessAndIpCounts() throws Exception {
        storage.insert(record("Ada", "ada@example.com"));
        storage.insert(record("Grace", "grace@example.com"));

        assertTrue(storage.emailExists(crypto.emailLookup("ada@example.com")));
        assertFalse(storage.emailExists(crypto.emailLookup("nobody@example.com")));
        assertEquals(2, storage.countByRegistrationIp("127.0.0.1"));
        assertEquals(0, storage.countByRegistrationIp("10.0.0.1"));
        assertEquals(2, storage.count());
        assertEquals(1, storage.countAltAccounts());

        AuthRecord third = record("Linus", "linus@example.com");
        third.registrationIp = "10.0.0.1";
        storage.insert(third);
        assertEquals(1, storage.countAltAccounts());

        AuthRecord fourth = record("Dennis", "dennis@example.com");
        fourth.registrationIp = "10.0.0.1";
        storage.insert(fourth);
        assertEquals(2, storage.countAltAccounts());
    }

    @Test
    void updatesLoginPasswordFailuresAndAutoLogin() throws Exception {
        AuthRecord record = record("Ada", "ada@example.com");
        storage.insert(record);

        long now = System.currentTimeMillis();
        storage.updateLogin(record.uuid, now, "10.0.0.7", now + 60_000L);
        AuthRecord afterLogin = storage.findByUuid(record.uuid);
        assertEquals(now, afterLogin.lastLoginAt);
        assertEquals(now + 60_000L, afterLogin.autoLoginUntil);
        assertEquals("10.0.0.7", afterLogin.lastIp);

        storage.clearAutoLogin(record.uuid);
        assertEquals(0L, storage.findByUuid(record.uuid).autoLoginUntil);

        String newBlob = crypto.hash("Yeni-Sifre-9");
        storage.updatePassword(record.uuid, newBlob, "GCM:1000:256", now + 1L);
        AuthRecord afterPassword = storage.findByUuid(record.uuid);
        assertTrue(crypto.verify("Yeni-Sifre-9", afterPassword.password).ok());
        assertEquals("GCM:1000:256", afterPassword.algorithm);

        storage.updateFailures(record.uuid, 4, now + 30_000L);
        AuthRecord afterFailures = storage.findByUuid(record.uuid);
        assertEquals(4, afterFailures.failedAttempts);
        assertEquals(now + 30_000L, afterFailures.lockedUntil);
    }

    @Test
    void changesAndClearsEmail() throws Exception {
        AuthRecord record = record("Ada", "ada@example.com");
        storage.insert(record);

        String stored = crypto.encryptEmail("yeni@example.com");
        String lookup = crypto.emailLookup("yeni@example.com");
        storage.updateEmail(record.uuid, stored, lookup, System.currentTimeMillis());

        AuthRecord updated = storage.findByUuid(record.uuid);
        assertEquals("yeni@example.com", updated.email);
        assertTrue(storage.emailExists(lookup));
        assertFalse(storage.emailExists(crypto.emailLookup("ada@example.com")));
    }

    @Test
    void writesAndPrunesAuditEntries() throws Exception {
        storage.insert(record("Ada", "ada@example.com"));
        for (int i = 0; i < 10; i++) {
            storage.audit(UUID.randomUUID(), "Ada", "login", "127.0.0.1", "deneme " + i);
        }
        storage.pruneAudit(3);
        // Pruning must not throw even when the table has fewer rows than the limit.
        storage.pruneAudit(1000);
        assertTrue(storage.databaseSizeBytes() > 0L);
    }

    @Test
    void snapshotsAndDeletesAccounts() throws Exception {
        storage.insert(record("Ada", "ada@example.com"));
        storage.insert(record("Grace", "grace@example.com"));

        List<AuthRecord> snapshot = storage.snapshot(10);
        assertEquals(2, snapshot.size());
        assertEquals(1, storage.snapshot(1).size());

        storage.delete(snapshot.get(0).uuid);
        assertEquals(1, storage.count());
    }

    @Test
    void createsAConsistentBackup() throws Exception {
        storage.insert(record("Ada", "ada@example.com"));
        Path backup = directory.resolve("backup.db");
        storage.vacuumInto(backup);
        assertTrue(Files.exists(backup));
        assertTrue(Files.size(backup) > 0L);
    }

    @Test
    void reopensTheExistingDatabaseWithoutLosingData() throws Exception {
        storage.insert(record("Ada", "ada@example.com"));
        storage.close();

        ConfigData.Database config = new ConfigData.Database();
        config.poolSize = 2;
        storage = new SqliteStorage(directory.resolve("aetherauth.db"), config, crypto, true,
                Logger.getLogger("AetherAuthTest"));
        storage.init();

        assertNotNull(storage.findByName("ada"));
        assertEquals(1, storage.count());
    }

    private static AuthRecord record(String name, String email) throws Exception {
        CryptoEngine crypto = new CryptoEngine(MASTER, 1000, "GCM", 32);
        long now = System.currentTimeMillis();
        AuthRecord record = new AuthRecord();
        record.uuid = UUID.randomUUID();
        record.name = name;
        record.nameLower = name.toLowerCase(Locale.ROOT);
        record.email = email;
        record.emailStored = crypto.encryptEmail(email);
        record.emailLookup = crypto.emailLookup(email);
        record.password = crypto.hash("Kartal-42");
        record.algorithm = "GCM:1000:256";
        record.createdAt = now;
        record.updatedAt = now;
        record.lastLoginAt = 0L;
        record.autoLoginUntil = 0L;
        record.lastIp = "127.0.0.1";
        record.registrationIp = "127.0.0.1";
        record.failedAttempts = 0;
        record.lockedUntil = 0L;
        return record;
    }
}
