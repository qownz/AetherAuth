package com.aetherauth.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;

/**
 * Detected server version plus small capability checks.
 */
public final class ServerVersion {

    private final String raw;
    private final int major;
    private final int minor;
    private final int patch;
    private final boolean paper;
    private final String brand;

    private ServerVersion(String raw, int major, int minor, int patch, boolean paper, String brand) {
        this.raw = raw;
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.paper = paper;
        this.brand = brand;
    }

    public static ServerVersion detect() {
        String raw = Bukkit.getBukkitVersion();
        if (raw == null || raw.isEmpty()) {
            raw = "1.8.8-R0.1-SNAPSHOT";
        }
        String brand = Bukkit.getName();
        boolean paper = brand != null && brand.toLowerCase(Locale.ROOT).contains("paper");
        if (!paper) {
            paper = classExists("com.destroystokyo.paper.PaperConfig") || classExists("io.papermc.paper.configuration.Configuration");
        }
        String numeric = raw;
        int dash = numeric.indexOf('-');
        if (dash > 0) {
            numeric = numeric.substring(0, dash);
        }
        String[] parts = numeric.split("\\.");
        int major = parseInt(parts, 0, 1);
        int minor = parseInt(parts, 1, 0);
        int patch = parseInt(parts, 2, 0);
        return new ServerVersion(raw, major, minor, patch, paper, brand);
    }

    private static int parseInt(String[] parts, int index, int fallback) {
        if (parts == null || parts.length <= index) {
            return fallback;
        }
        try {
            String value = parts[index];
            StringBuilder digits = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (Character.isDigit(c)) {
                    digits.append(c);
                } else {
                    break;
                }
            }
            return digits.length() == 0 ? fallback : Integer.parseInt(digits.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public String raw() {
        return raw;
    }

    public String brand() {
        return brand == null ? "Unknown" : brand;
    }

    public boolean isPaper() {
        return paper;
    }

    public int major() { return major; }

    public int minor() { return minor; }

    public int patch() { return patch; }

    public boolean atLeast(int major, int minor, int patch) {
        if (this.major != major) {
            return this.major > major;
        }
        if (this.minor != minor) {
            return this.minor > minor;
        }
        return this.patch >= patch;
    }

    public boolean atLeast(int minor, int patch) {
        return atLeast(1, minor, patch);
    }

    public boolean hasHexColours() {
        return atLeast(1, 16, 0);
    }

    public boolean hasBossBar() {
        return atLeast(1, 9, 0);
    }

    public boolean hasDialogApi() {
        return atLeast(1, 21, 6) && classExists("io.papermc.paper.dialog.Dialog");
    }

    public boolean hasActionBar() {
        return atLeast(1, 8, 0) && classExists("net.md_5.bungee.api.ChatMessageType");
    }

    /** Kısa sürüm metni, örneğin {@code 1.16.5} veya {@code 1.21.8}. */
    public String shortVersion() {
        return major + "." + minor + "." + patch;
    }

    public String describe() {
        List<String> parts = new ArrayList<String>();
        parts.add(brand());
        parts.add(raw);
        parts.add("dialog=" + hasDialogApi());
        parts.add("hex=" + hasHexColours());
        return String.join(", ", parts);
    }

    public static boolean classExists(String name) {
        try {
            Class.forName(name, false, ServerVersion.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
