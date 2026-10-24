package com.aetherauth.storage;

import java.util.List;

/**
 * Storage abstraction. All methods are blocking and must be called from an
 * asynchronous worker, never from the server thread.
 */
public interface Storage extends AutoCloseable {

    void init() throws Exception;

    AuthRecord findByName(String name);

    AuthRecord findByUuid(java.util.UUID uuid);

    boolean emailExists(String emailLookup);

    int countByRegistrationIp(String ip);

    /** Toplam yan hesap sayısı: aynı kayıt IP'sini paylaşan fazladan hesaplar. */
    int countAltAccounts();

    void insert(AuthRecord record) throws DuplicateEntryException;

    void updateLogin(java.util.UUID uuid, long time, String ip, long autoLoginUntil);

    void clearAutoLogin(java.util.UUID uuid);

    void updatePassword(java.util.UUID uuid, String encoded, String algorithm, long time);

    void updateEmail(java.util.UUID uuid, String emailStored, String emailLookup, long time);

    void updateFailures(java.util.UUID uuid, int failures, long lockedUntil);

    void delete(java.util.UUID uuid);

    int count();

    void audit(java.util.UUID uuid, String name, String action, String ip, String detail);

    void pruneAudit(int keep);

    void vacuumInto(java.nio.file.Path target) throws Exception;

    long databaseSizeBytes();

    List<AuthRecord> snapshot(int limit);

    @Override
    void close();
}
