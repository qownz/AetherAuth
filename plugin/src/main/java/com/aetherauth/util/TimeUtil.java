package com.aetherauth.util;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Date/duration formatting used by admin output.
 */
public final class TimeUtil {

    private TimeUtil() {
    }

    public static String formatDate(long millis) {
        if (millis <= 0L) {
            return "-";
        }
        try {
            SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm");
            return format.format(new Date(millis));
        } catch (Exception ignored) {
            return "-";
        }
    }

    public static String humanBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int index = -1;
        while (value >= 1024.0D && index < units.length - 1) {
            value /= 1024.0D;
            index++;
        }
        return String.format(java.util.Locale.ROOT, "%.1f %s", value, units[index]);
    }

    public static long secondsUntil(long timestamp) {
        long remaining = timestamp - System.currentTimeMillis();
        return remaining <= 0L ? 0L : (remaining + 999L) / 1000L;
    }
}
