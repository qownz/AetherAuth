package com.aetherauth.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.aetherauth.util.Json;
import com.aetherauth.util.Text;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Loads {@code config.yml} and {@code messages.yml}.
 *
 * <p>Both files are created from the bundled, commented defaults on first
 * start. Existing files are never rewritten, so user comments survive
 * reloads. Missing keys simply fall back to the defaults compiled into the
 * plugin.</p>
 */
public final class ConfigManager {

    private static final Map<String, String> FALLBACK_MESSAGES = ConfigData.defaultMessages();

    private final Path folder;
    private final Path file;
    private final Path messagesFile;
    private final Logger logger;
    private volatile ConfigData data;
    private volatile Map<String, String> messages;
    private volatile String fingerprint = "";

    public ConfigManager(Path dataFolder, Logger logger) {
        this.folder = dataFolder;
        this.file = dataFolder.resolve("config.yml");
        this.messagesFile = dataFolder.resolve("messages.yml");
        this.logger = logger;
        this.data = new ConfigData();
        this.messages = new LinkedHashMap<String, String>(FALLBACK_MESSAGES);
    }

    public boolean load() {
        try {
            Files.createDirectories(folder);
            copyDefault("config.yml", file);
            copyDefault("messages.yml", messagesFile);

            Map<String, Object> tree = readYaml(file);
            ConfigData parsed = Json.gson().fromJson(Json.gson().toJsonTree(tree), ConfigData.class);
            if (parsed == null) {
                parsed = new ConfigData();
            }
            validate(parsed);

            Map<String, String> loadedMessages = readMessages(messagesFile);
            for (Map.Entry<String, String> entry : FALLBACK_MESSAGES.entrySet()) {
                if (!loadedMessages.containsKey(entry.getKey())) {
                    loadedMessages.put(entry.getKey(), entry.getValue());
                }
            }

            this.data = parsed;
            this.messages = loadedMessages;
            this.fingerprint = tree.toString() + "|" + loadedMessages.size();
            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "config.yml okunamadı, varsayılan ayarlar kullanılıyor.", exception);
            this.data = new ConfigData();
            this.messages = new LinkedHashMap<String, String>(FALLBACK_MESSAGES);
            return false;
        }
    }

    /** Only ensures both files exist; it never overwrites user comments. */
    public void save() {
        try {
            Files.createDirectories(folder);
            copyDefault("config.yml", file);
            copyDefault("messages.yml", messagesFile);
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Varsayılan ayar dosyaları oluşturulamadı.", exception);
        }
    }

    public Path folder() {
        return folder;
    }

    public Path file() {
        return file;
    }

    public Path messagesFile() {
        return messagesFile;
    }

    public ConfigData data() {
        return data;
    }

    public ConfigData.Database database() {
        return data.database;
    }

    public ConfigData.Security security() {
        return data.security;
    }

    public ConfigData.Auth auth() {
        return data.auth;
    }

    public ConfigData.Gui gui() {
        return data.gui;
    }

    public ConfigData.Sounds sounds() {
        return data.sounds;
    }

    public ConfigData.Update update() {
        return data.update;
    }

    public String raw(String key) {
        String value = messages.get(key);
        if (value == null) {
            value = FALLBACK_MESSAGES.get(key);
        }
        return value == null ? "<red>[" + key + "]" : value;
    }

    /** Legacy string (chat, titles, action bars). */
    public String message(String key, Object... placeholders) {
        return Text.legacy(raw(key), withDefaults(placeholders));
    }

    /** MiniMessage string (Paper dialogs). */
    public String miniMessage(String key, Object... placeholders) {
        return Text.mini(raw(key), withDefaults(placeholders));
    }

    public String prefix() {
        return Text.legacy(raw("prefix"));
    }

    public String configFingerprint() {
        return fingerprint;
    }

    private Map<String, String> withDefaults(Object... placeholders) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        if (placeholders != null) {
            for (int i = 0; i + 1 < placeholders.length; i += 2) {
                if (placeholders[i] == null) {
                    continue;
                }
                map.put(String.valueOf(placeholders[i]),
                        placeholders[i + 1] == null ? "" : String.valueOf(placeholders[i + 1]));
            }
        }
        if (!map.containsKey("prefix")) {
            map.put("prefix", prefix());
        }
        return map;
    }

    private void copyDefault(String resourceName, Path target) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        InputStream stream = ConfigManager.class.getResourceAsStream("/" + resourceName);
        if (stream == null) {
            logger.warning("Varsayılan " + resourceName + " kaynağı bulunamadı.");
            return;
        }
        try {
            Files.copy(stream, target);
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }

    private Map<String, Object> readYaml(Path path) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
        return sectionToMap(yaml);
    }

    private Map<String, Object> sectionToMap(ConfigurationSection section) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection) {
                map.put(key, sectionToMap((ConfigurationSection) value));
            } else {
                map.put(key, value);
            }
        }
        return map;
    }

    private Map<String, String> readMessages(Path path) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
        flattenMessages(yaml, "", out);
        return out;
    }

    private void flattenMessages(ConfigurationSection section, String prefix, Map<String, String> out) {
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection) {
                flattenMessages((ConfigurationSection) value, prefix + key + ".", out);
            } else if (value != null) {
                out.put(prefix + key, String.valueOf(value));
            }
        }
    }

    private void validate(ConfigData value) {
        value.configVersion = Math.max(1, value.configVersion);
        if (value.update == null) value.update = new ConfigData.Update();
        if (value.update.versionUrl == null) {
            value.update.versionUrl = "https://raw.githubusercontent.com/qownz/AetherAuth/main/version.txt";
        }
        if (value.update.downloadUrl == null) {
            value.update.downloadUrl = "https://github.com/qownz/AetherAuth/releases/latest";
        }

        if (value.database == null) value.database = new ConfigData.Database();
        if (value.database.poolSize < 1) value.database.poolSize = 1;
        if (value.database.poolSize > 16) value.database.poolSize = 16;
        if (value.database.busyTimeoutMs < 500) value.database.busyTimeoutMs = 500;
        if (value.database.cacheSize < 64) value.database.cacheSize = 64;
        if (value.database.cacheSize > 200000) value.database.cacheSize = 200000;
        if (value.database.backupsToKeep < 1) value.database.backupsToKeep = 1;
        if (value.database.auditRetention < 1000) value.database.auditRetention = 1000;
        String sync = value.database.synchronous == null ? "NORMAL" : value.database.synchronous.toUpperCase(Locale.ROOT);
        if (!sync.equals("NORMAL") && !sync.equals("FULL") && !sync.equals("OFF")) {
            sync = "NORMAL";
        }
        value.database.synchronous = sync;
        if (value.database.file == null || value.database.file.trim().isEmpty()) {
            value.database.file = "aetherauth.db";
        }

        if (value.security == null) value.security = new ConfigData.Security();
        if (value.security.iterations < 10000) value.security.iterations = 10000;
        if (value.security.iterations > 2000000) value.security.iterations = 2000000;
        if (value.security.saltBytes < 16) value.security.saltBytes = 16;
        if (value.security.saltBytes > 64) value.security.saltBytes = 64;
        String cipher = value.security.cipher == null ? "GCM" : value.security.cipher.toUpperCase(Locale.ROOT);
        if (!cipher.equals("GCM") && !cipher.equals("CBC_HMAC")) {
            cipher = "GCM";
        }
        value.security.cipher = cipher;

        if (value.auth == null) value.auth = new ConfigData.Auth();
        if (value.auth.minPasswordLength < 4) value.auth.minPasswordLength = 4;
        if (value.auth.minPasswordLength > 128) value.auth.minPasswordLength = 128;
        if (value.auth.maxPasswordLength < value.auth.minPasswordLength) {
            value.auth.maxPasswordLength = value.auth.minPasswordLength;
        }
        if (value.auth.maxPasswordLength > 256) value.auth.maxPasswordLength = 256;
        if (value.auth.maxAttempts < 1) value.auth.maxAttempts = 1;
        if (value.auth.maxAttempts > 100) value.auth.maxAttempts = 100;
        if (value.auth.lockoutSeconds < 0) value.auth.lockoutSeconds = 0;
        if (value.auth.timeoutSeconds < 0) value.auth.timeoutSeconds = 0;
        if (value.auth.timeoutSeconds > 3600) value.auth.timeoutSeconds = 3600;
        if (value.auth.maxAccountsPerIp < 0) value.auth.maxAccountsPerIp = 0;
        if (value.auth.registerCooldownSeconds < 0) value.auth.registerCooldownSeconds = 0;
        if (value.auth.loginAttemptsPerIp < 1) value.auth.loginAttemptsPerIp = 1;
        if (value.auth.loginWindowSeconds < 10) value.auth.loginWindowSeconds = 10;
        if (value.auth.workerThreads < 1) value.auth.workerThreads = 1;
        if (value.auth.workerThreads > 16) value.auth.workerThreads = 16;
        if (value.auth.sessionDurationMinutes < 0) value.auth.sessionDurationMinutes = 0;
        if (value.auth.authSpawn == null) value.auth.authSpawn = new ConfigData.Spawn();
        if (value.auth.authSpawn.world == null || value.auth.authSpawn.world.isEmpty()) {
            value.auth.authSpawn.world = "world";
        }
        if (value.auth.allowedCommands == null) {
            value.auth.allowedCommands = new ConfigData.Auth().allowedCommands;
        } else {
            value.auth.allowedCommands = normalizeCommands(value.auth.allowedCommands);
        }
        if (value.auth.loginCommands == null) value.auth.loginCommands = new ArrayList<String>();
        if (value.auth.registerCommands == null) value.auth.registerCommands = new ArrayList<String>();
        if (value.auth.logoutCommands == null) value.auth.logoutCommands = new ArrayList<String>();

        if (value.gui == null) value.gui = new ConfigData.Gui();
        String mode = value.gui.mode == null ? "auto" : value.gui.mode.toLowerCase(Locale.ROOT);
        if (!mode.equals("auto") && !mode.equals("dialog") && !mode.equals("command")) {
            mode = "auto";
        }
        value.gui.mode = mode;
        if (value.gui.openDelayTicks < 0) value.gui.openDelayTicks = 0;
        if (value.gui.openDelayTicks > 200) value.gui.openDelayTicks = 200;
        if (value.gui.width < 100) value.gui.width = 100;
        if (value.gui.width > 400) value.gui.width = 400;
        if (value.gui.buttonWidth < 40) value.gui.buttonWidth = 40;
        if (value.gui.buttonWidth > 400) value.gui.buttonWidth = 400;

        if (value.sounds == null) value.sounds = new ConfigData.Sounds();
        if (value.sounds.volume < 0.0F) value.sounds.volume = 0.0F;
        if (value.sounds.volume > 1.0F) value.sounds.volume = 1.0F;
        if (value.sounds.pitch < 0.5F) value.sounds.pitch = 0.5F;
        if (value.sounds.pitch > 2.0F) value.sounds.pitch = 2.0F;
    }

    private List<String> normalizeCommands(List<String> input) {
        List<String> out = new ArrayList<String>();
        for (String command : input) {
            if (command == null) continue;
            String cleaned = command.trim().toLowerCase(Locale.ROOT);
            while (cleaned.startsWith("/")) {
                cleaned = cleaned.substring(1);
            }
            if (cleaned.isEmpty()) continue;
            int colon = cleaned.indexOf(':');
            if (colon > 0) {
                cleaned = cleaned.substring(colon + 1);
            }
            out.add(cleaned.trim());
        }
        return out;
    }
}
