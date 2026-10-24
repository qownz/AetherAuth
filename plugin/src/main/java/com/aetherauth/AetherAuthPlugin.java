package com.aetherauth;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;

import com.aetherauth.api.AetherAuthApi;
import com.aetherauth.auth.AuthManager;
import com.aetherauth.auth.PasswordPolicy;
import com.aetherauth.command.AdminCommand;
import com.aetherauth.command.ChangeEmailCommand;
import com.aetherauth.command.ChangePasswordCommand;
import com.aetherauth.command.LoginCommand;
import com.aetherauth.command.LogoutCommand;
import com.aetherauth.command.RegisterCommand;
import com.aetherauth.config.ConfigManager;
import com.aetherauth.crypto.CryptoEngine;
import com.aetherauth.crypto.SecretStore;
import com.aetherauth.listener.AuthListener;
import com.aetherauth.platform.AsyncExecutor;
import com.aetherauth.platform.Chat;
import com.aetherauth.platform.DialogService;
import com.aetherauth.platform.UpdateChecker;
import com.aetherauth.storage.AuthCache;
import com.aetherauth.storage.BackupService;
import com.aetherauth.storage.SqliteStorage;
import com.aetherauth.storage.Storage;
import com.aetherauth.util.ServerVersion;
import com.aetherauth.util.Text;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * AetherAuth — modern authentication for Spigot and Paper, 1.8 through 1.21+.
 */
public final class AetherAuthPlugin extends JavaPlugin {

    private ServerVersion version;
    private ConfigManager config;
    private byte[] secret;
    private CryptoEngine crypto;
    private Storage storage;
    private AuthCache cache;
    private Chat chat;
    private DialogService dialogs;
    private AsyncExecutor async;
    private AuthManager auth;
    private BackupService backups;
    private UpdateChecker updateChecker;
    private volatile boolean ready;
    private int housekeepingTask = -1;
    private int housekeepingRuns;

    @Override
    public void onEnable() {
        this.version = ServerVersion.detect();
        Text.setHexSupported(version.hasHexColours());

        File folder = getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            getLogger().severe("Veri klasörü oluşturulamadı: " + folder.getAbsolutePath());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.config = new ConfigManager(folder.toPath(), getLogger());
        this.config.load();

        try {
            Path databaseDir = folder.toPath().resolve("database");
            Files.createDirectories(databaseDir);
            migrateLegacyFiles(folder.toPath(), databaseDir);

            this.secret = SecretStore.loadOrCreate(databaseDir, 32, getLogger());
            this.crypto = new CryptoEngine(secret, config.security().iterations,
                    config.security().cipher, config.security().saltBytes);
            Path database = databaseDir.resolve(config.database().file);
            this.storage = new SqliteStorage(database, config.database(), crypto,
                    config.security().encryptEmail, getLogger());
            this.storage.init();
        } catch (Throwable throwable) {
            getLogger().log(Level.SEVERE, "AetherAuth başlatılamadı: " + throwable.getMessage(), throwable);
            getLogger().severe("database/secret.key dosyasını silmeyin; hesaplar bu anahtarla korunur.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.cache = new AuthCache(config.database().cacheSize);
        this.chat = new Chat(config, version);
        this.dialogs = new DialogService(config, version, getLogger());
        this.async = new AsyncExecutor(config.auth().workerThreads, getLogger());
        warmUpCrypto();
        this.auth = new AuthManager(this);
        this.backups = new BackupService(folder.toPath().resolve("database"), storage,
                config.database().backupsToKeep, getLogger());

        getServer().getPluginManager().registerEvents(new AuthListener(this), this);
        registerCommands();
        getServer().getServicesManager().register(AetherAuthApi.class, new ApiImpl(), this, ServicePriority.Normal);

        scheduleHousekeeping();
        this.ready = true;
        printStartup();
        this.updateChecker = new UpdateChecker(this, config.update());
        this.updateChecker.start();

        if (config.database().backupOnStart) {
            async.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Path path = backups.createBackup();
                        getLogger().info("Başlangıç yedeği oluşturuldu: " + path.getFileName());
                    } catch (Throwable throwable) {
                        getLogger().log(Level.WARNING, "Başlangıç yedeği alınamadı.", throwable);
                    }
                }
            });
        }
    }

    @Override
    public void onDisable() {
        ready = false;
        if (housekeepingTask != -1) {
            try {
                getServer().getScheduler().cancelTask(housekeepingTask);
            } catch (Throwable ignored) {
            }
            housekeepingTask = -1;
        }
        if (updateChecker != null) {
            updateChecker.stop();
        }
        if (auth != null) {
            auth.shutdown();
        }
        if (async != null) {
            async.close();
        }
        if (storage != null) {
            storage.close();
        }
        if (chat != null) {
            getLogger().info("AetherAuth kapatıldı.");
        }
    }

    private void warmUpCrypto() {
        if (async == null || crypto == null) {
            return;
        }
        async.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // Warm up PBKDF2/JIT so the first real login is not slower.
                    crypto.hash("AetherAuth-Warmup-Only");
                } catch (Throwable throwable) {
                    getLogger().log(Level.FINE, "Kripto ısınması başarısız.", throwable);
                }
            }
        });
    }

    private void migrateLegacyFiles(Path root, Path databaseDir) {
        String file = config.database().file;
        moveIfNeeded(root.resolve(file), databaseDir.resolve(file));
        moveIfNeeded(root.resolve(file + "-wal"), databaseDir.resolve(file + "-wal"));
        moveIfNeeded(root.resolve(file + "-shm"), databaseDir.resolve(file + "-shm"));
        moveIfNeeded(root.resolve("secret.key"), databaseDir.resolve("secret.key"));
        Path oldBackups = root.resolve("backups");
        Path newBackups = databaseDir.resolve("backups");
        if (Files.isDirectory(oldBackups) && !Files.exists(newBackups)) {
            try {
                Files.move(oldBackups, newBackups);
                getLogger().info("Eski yedekler taşındı: backups/ -> database/backups/");
            } catch (Throwable throwable) {
                getLogger().log(Level.WARNING, "Eski yedekler taşınamadı.", throwable);
            }
        }
    }

    private void moveIfNeeded(Path from, Path to) {
        try {
            if (Files.exists(from) && !Files.exists(to)) {
                Files.move(from, to);
                getLogger().info("Eski dosya taşındı: " + from.getFileName() + " -> database/");
            }
        } catch (Throwable throwable) {
            getLogger().log(Level.WARNING, "Eski dosya taşınamadı: " + from, throwable);
        }
    }

    private void registerCommands() {
        LoginCommand login = new LoginCommand(this);
        RegisterCommand register = new RegisterCommand(this);
        LogoutCommand logout = new LogoutCommand(this);
        ChangePasswordCommand changePassword = new ChangePasswordCommand(this);
        ChangeEmailCommand changeEmail = new ChangeEmailCommand(this);
        AdminCommand admin = new AdminCommand(this);

        bind("login", login);
        bind("register", register);
        bind("logout", logout);
        bind("changepassword", changePassword);
        bind("changemail", changeEmail);
        bind("aetherauth", admin);
    }

    private void bind(String name, org.bukkit.command.TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Komut plugin.yml içinde bulunamadı: " + name);
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void scheduleHousekeeping() {
        housekeepingTask = getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                if (auth != null) {
                    auth.rateLimiter().cleanup();
                }
                housekeepingRuns++;
                if (housekeepingRuns % 6 == 0 && async != null && storage != null) {
                    final int keep = config.database().auditRetention;
                    async.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                storage.pruneAudit(keep);
                            } catch (Throwable throwable) {
                                getLogger().log(Level.FINE, "Audit temizliği başarısız.", throwable);
                            }
                        }
                    });
                }
            }
        }, 20L * 60L, 20L * 60L * 5L);
    }

    private void printStartup() {
        getLogger().info("AetherAuth v" + getDescription().getVersion() + " loaded.");
        logDialogStatus();
    }

    private void logDialogStatus() {
        if ("command".equalsIgnoreCase(config.gui().mode)) {
            getLogger().info("Dialog sistemi kapalı (gui.mode=command).");
            return;
        }
        if (dialogs.available()) {
            getLogger().info("Dialog sistemi aktif.");
            return;
        }
        if (!version.atLeast(1, 21, 6)) {
            getLogger().warning("[!] DİKKAT: Sunucu sürümü " + version.shortVersion()
                    + ", dialog sisteminin gerektirdiği 1.21.6 sürümünden eski olduğu için"
                    + " dialog sistemi desteklenmemektedir.");
        } else {
            getLogger().warning("[!] DİKKAT: Paper Dialog API bulunamadı;"
                    + " dialog sistemi desteklenmemektedir. (Paper 1.21.6+ gerekir)");
        }
        getLogger().warning("[!] Oyuncular /login ve /register komutlarını kullanacak.");
    }

    /** Reloads config.yml, messages.yml and the dialog bridge. */
    public long reloadEverything() {
        long started = System.currentTimeMillis();
        config.load();
        Text.setHexSupported(version.hasHexColours());
        if (secret != null) {
            crypto = new CryptoEngine(secret, config.security().iterations,
                    config.security().cipher, config.security().saltBytes);
            if (auth != null) {
                auth.setCrypto(crypto);
            }
        }
        auth.setPolicy(new PasswordPolicy(config.auth()));
        dialogs = new DialogService(config, version, getLogger());
        if (backups != null) {
            backups.setKeep(config.database().backupsToKeep);
        }
        if (updateChecker != null) {
            updateChecker.stop();
        }
        updateChecker = new UpdateChecker(this, config.update());
        updateChecker.start();
        logDialogStatus();
        getLogger().info("AetherAuth yapılandırması yenilendi (" + (System.currentTimeMillis() - started) + " ms).");
        return System.currentTimeMillis() - started;
    }

    /**
     * Runs a task on the server thread. Safe to call from any worker; tasks are
     * dropped while the plugin is shutting down.
     */
    public void runSync(Runnable task) {
        if (task == null) {
            return;
        }
        try {
            if (Bukkit.isPrimaryThread()) {
                task.run();
                return;
            }
            if (!isEnabled() || !ready) {
                return;
            }
            getServer().getScheduler().runTask(this, task);
        } catch (Throwable throwable) {
            getLogger().log(Level.FINE, "Senkron görev çalıştırılamadı.", throwable);
        }
    }

    public ServerVersion version() {
        return version;
    }

    public ConfigManager config() {
        return config;
    }

    public CryptoEngine crypto() {
        return crypto;
    }

    public Storage storage() {
        return storage;
    }

    public AuthCache cache() {
        return cache;
    }

    public Chat chat() {
        return chat;
    }

    public DialogService dialogs() {
        return dialogs;
    }

    public AsyncExecutor async() {
        return async;
    }

    public AuthManager auth() {
        return auth;
    }

    public BackupService backups() {
        return backups;
    }

    public UpdateChecker updateChecker() {
        return updateChecker;
    }

    private final class ApiImpl implements AetherAuthApi {
        @Override
        public boolean isAuthenticated(Player player) {
            return player != null && auth.isAuthenticated(player);
        }

        @Override
        public boolean isAuthenticated(UUID uuid) {
            return auth.isAuthenticated(uuid);
        }

        @Override
        public int authenticatedCount() {
            return auth.authenticatedCount();
        }

        @Override
        public String email(Player player) {
            return player == null ? "" : auth.email(player);
        }

        @Override
        public void logout(Player player) {
            if (player != null) {
                auth.logout(player);
            }
        }

        @Override
        public boolean forceLogin(Player player) {
            if (player == null || !player.isOnline()) {
                return false;
            }
            auth.forceLogin(player);
            return auth.isAuthenticated(player);
        }
    }
}
