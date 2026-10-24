package com.aetherauth.crypto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads (or creates) the master secret used by the password and e-mail
 * encryption layer.
 *
 * <p>The secret never lives inside the database. It can be supplied through
 * the {@code AETHERAUTH_SECRET} environment variable (useful for containers)
 * or is generated once into {@code plugins/AetherAuth/secret.key}. Losing this
 * file makes existing accounts unrecoverable, so it is included in backups.</p>
 */
public final class SecretStore {

    public static final String ENV_VARIABLE = "AETHERAUTH_SECRET";
    public static final String FILE_NAME = "secret.key";

    private SecretStore() {
    }

    public static byte[] loadOrCreate(Path dataFolder, int bytes, Logger logger) throws IOException {
        String environment = System.getenv(ENV_VARIABLE);
        if (environment != null && !environment.trim().isEmpty()) {
            byte[] secret = decodeEnvironment(environment.trim());
            logger.info(ENV_VARIABLE + " ortam değişkeni kullanılıyor (secret.key yok sayıldı).");
            return secret;
        }

        Path file = dataFolder.resolve(FILE_NAME);
        if (Files.exists(file)) {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
            byte[] decoded = decodeFlexible(content);
            if (decoded == null || decoded.length < 16) {
                throw new IOException("secret.key geçersiz. Dosyayı silmeyin; yedekten geri yükleyin.");
            }
            return decoded;
        }

        Files.createDirectories(dataFolder);
        byte[] secret = new byte[Math.max(16, bytes)];
        new SecureRandom().nextBytes(secret);
        String encoded = Base64.getEncoder().encodeToString(secret);
        Files.write(file, (encoded + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        restrictPermissions(file, logger);
        logger.info("Yeni bir master secret oluşturuldu: " + file.getFileName()
                + " — bu dosyayı yedekleyin, kaybı hâlinde hesaplar kurtarılamaz.");
        return secret;
    }

    private static byte[] decodeEnvironment(String value) {
        byte[] flexible = decodeFlexible(value);
        if (flexible != null && flexible.length >= 16) {
            return flexible;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 kullanılamıyor.", exception);
        }
    }

    private static byte[] decodeFlexible(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ignored) {
            // Not base64 — try URL-safe without padding.
        }
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void restrictPermissions(Path file, Logger logger) {
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(file, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Windows and other non-POSIX file systems.
        } catch (Exception exception) {
            logger.log(Level.FINE, "secret.key izinleri ayarlanamadı.", exception);
        }
    }
}
