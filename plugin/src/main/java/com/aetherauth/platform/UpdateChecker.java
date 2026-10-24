package com.aetherauth.platform;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.aetherauth.AetherAuthPlugin;
import com.aetherauth.config.ConfigData;

import org.bukkit.entity.Player;

/**
 * Checks the public version.txt on GitHub once per start and informs the first
 * operator who joins when a newer release is available.
 */
public final class UpdateChecker {

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    private final AetherAuthPlugin plugin;
    private final Logger logger;
    private final String currentVersion;
    private final AtomicBoolean notified = new AtomicBoolean(false);

    private volatile ConfigData.Update settings;
    private volatile boolean active = true;
    private volatile boolean checked;
    private volatile boolean updateAvailable;
    private volatile String latestVersion = "";
    private volatile String downloadUrl = "";
    private volatile UUID pendingOperator;

    public UpdateChecker(AetherAuthPlugin plugin, ConfigData.Update settings) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.currentVersion = readLocalVersion(plugin);
        reload(settings);
    }

    /** Applies a new configuration and resets the one-time notification. */
    public void reload(ConfigData.Update settings) {
        this.settings = settings == null ? new ConfigData.Update() : settings;
        this.checked = false;
        this.updateAvailable = false;
        this.latestVersion = "";
        this.downloadUrl = this.settings.downloadUrl == null ? "" : this.settings.downloadUrl.trim();
        this.pendingOperator = null;
        this.notified.set(false);
    }

    public void start() {
        final ConfigData.Update snapshot = settings;
        if (snapshot == null || !snapshot.enabled) {
            checked = true;
            return;
        }
        final String url = snapshot.versionUrl == null ? "" : snapshot.versionUrl.trim();
        if (url.isEmpty()) {
            checked = true;
            return;
        }

        Runnable task = new Runnable() {
            @Override
            public void run() {
                check(url);
            }
        };
        AsyncExecutor executor = plugin.async();
        if (executor != null) {
            executor.execute(task);
            return;
        }
        Thread thread = new Thread(task, "AetherAuth-UpdateCheck");
        thread.setDaemon(true);
        thread.start();
    }

    /** Called on the server thread when a player joins. */
    public void onJoin(Player player) {
        if (!active || player == null || !player.isOp() || notified.get()) {
            return;
        }
        if (!checked) {
            if (pendingOperator == null) {
                pendingOperator = player.getUniqueId();
            }
            return;
        }
        if (updateAvailable) {
            notifyPlayer(player);
        }
    }

    /** Called on the server thread when a player leaves. */
    public void onQuit(Player player) {
        if (player != null && player.getUniqueId().equals(pendingOperator)) {
            pendingOperator = null;
        }
    }

    public void stop() {
        active = false;
    }

    public boolean updateAvailable() {
        return updateAvailable;
    }

    public String latestVersion() {
        return latestVersion;
    }

    private void check(String url) {
        String remote = fetch(url);
        if (!active) {
            return;
        }
        checked = true;
        if (remote == null || remote.isEmpty()) {
            return;
        }
        latestVersion = remote;
        if (!isNewer(remote, currentVersion)) {
            return;
        }

        updateAvailable = true;
        logger.warning("[!] Yeni AetherAuth sürümü mevcut: " + remote + " (mevcut: " + currentVersion + ")");
        logger.warning("[!] Lütfen yeni sürümü buradan indirin: " + downloadUrl);
        notifyPendingOperator();
    }

    private void notifyPendingOperator() {
        final UUID uuid = pendingOperator;
        if (uuid == null || notified.get()) {
            return;
        }
        plugin.runSync(new Runnable() {
            @Override
            public void run() {
                if (!active || notified.get()) {
                    return;
                }
                Player player = plugin.getServer().getPlayer(uuid);
                if (player != null && player.isOnline() && player.isOp()) {
                    notifyPlayer(player);
                }
            }
        });
    }

    private void notifyPlayer(final Player player) {
        if (!notified.compareAndSet(false, true)) {
            return;
        }
        final String latest = latestVersion;
        final String current = currentVersion;
        final String url = downloadUrl;
        try {
            plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        plugin.chat().msg(player, "update.available",
                                "current", current,
                                "latest", latest,
                                "url", url);
                    }
                }
            }, 20L);
        } catch (Throwable throwable) {
            notified.set(false);
            logger.log(Level.FINE, "Güncelleme mesajı planlanamadı.", throwable);
        }
    }

    private String fetch(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "AetherAuth/" + currentVersion);

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        return normalize(line);
                    }
                }
            } finally {
                reader.close();
            }
        } catch (Throwable throwable) {
            logger.log(Level.FINE, "Güncelleme kontrolü yapılamadı: " + url, throwable);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    private static String readLocalVersion(AetherAuthPlugin plugin) {
        InputStream stream = plugin.getClass().getClassLoader().getResourceAsStream("version.txt");
        if (stream != null) {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            return normalize(line);
                        }
                    }
                } finally {
                    reader.close();
                }
            } catch (Throwable ignored) {
            } finally {
                try {
                    stream.close();
                } catch (Throwable ignored) {
                }
            }
        }
        String description = plugin.getDescription().getVersion();
        return description == null ? "0.0.0" : normalize(description);
    }

    private static String normalize(String value) {
        String result = value == null ? "" : value.trim();
        int start = 0;
        while (start < result.length() && !Character.isDigit(result.charAt(start))) {
            start++;
        }
        if (start > 0) {
            result = result.substring(start);
        }
        int end = result.length();
        while (end > 0) {
            char c = result.charAt(end - 1);
            if (Character.isDigit(c) || c == '.') {
                break;
            }
            end--;
        }
        return result.substring(0, end);
    }

    /**
     * Compares dotted numeric versions. Missing parts count as zero, so
     * {@code 1.0} and {@code 1.0.0} are equal.
     */
    static boolean isNewer(String remote, String current) {
        int[] remoteParts = parts(remote);
        int[] currentParts = parts(current);
        int length = Math.max(remoteParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            int left = i < remoteParts.length ? remoteParts[i] : 0;
            int right = i < currentParts.length ? currentParts[i] : 0;
            if (left != right) {
                return left > right;
            }
        }
        return false;
    }

    private static int[] parts(String version) {
        String cleaned = normalize(version);
        if (cleaned.isEmpty()) {
            return new int[0];
        }
        String[] split = cleaned.split("\\.");
        int[] result = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            int value = 0;
            for (int j = 0; j < split[i].length(); j++) {
                char c = split[i].charAt(j);
                if (!Character.isDigit(c)) {
                    break;
                }
                value = value * 10 + (c - '0');
            }
            result[i] = value;
        }
        return result;
    }
}
