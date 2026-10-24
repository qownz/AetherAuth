package com.aetherauth.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.aetherauth.crypto.SecretStore;

/**
 * Consistent SQLite backups via {@code VACUUM INTO} plus the master secret.
 * A database backup without secret.key is useless, so both are written to the
 * same timestamped folder.
 */
public final class BackupService {

    private final Path dataFolder;
    private final Storage storage;
    private final Logger logger;
    private volatile int keep;

    public BackupService(Path dataFolder, Storage storage, int keep, Logger logger) {
        this.dataFolder = dataFolder;
        this.storage = storage;
        this.keep = Math.max(1, keep);
        this.logger = logger;
    }

    public void setKeep(int keep) {
        this.keep = Math.max(1, keep);
    }

    public Path createBackup() throws Exception {
        Path root = dataFolder.resolve("backups");
        Files.createDirectories(root);
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        Path target = root.resolve(stamp);
        int suffix = 1;
        while (Files.exists(target)) {
            target = root.resolve(stamp + "-" + suffix);
            suffix++;
        }
        Files.createDirectories(target);
        Path database = target.resolve("aetherauth.db");
        storage.vacuumInto(database);
        Path secret = dataFolder.resolve(SecretStore.FILE_NAME);
        if (Files.exists(secret)) {
            Files.copy(secret, target.resolve(SecretStore.FILE_NAME), StandardCopyOption.REPLACE_EXISTING);
        }
        prune(root);
        return target;
    }

    private void prune(Path root) {
        try {
            List<Path> directories = new ArrayList<Path>();
            for (Path candidate : Files.newDirectoryStream(root)) {
                if (Files.isDirectory(candidate)) {
                    directories.add(candidate);
                }
            }
            Collections.sort(directories, new Comparator<Path>() {
                @Override
                public int compare(Path first, Path second) {
                    return second.getFileName().toString().compareTo(first.getFileName().toString());
                }
            });
            for (int i = keep; i < directories.size(); i++) {
                deleteRecursively(directories.get(i));
            }
        } catch (Exception exception) {
            logger.log(Level.FINE, "Eski yedekler temizlenemedi.", exception);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
