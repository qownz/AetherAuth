package com.aetherauth.platform;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Reflective boss bar wrapper (BossBar only exists on 1.9+). A failure to find
 * the API is not an error: the plugin simply uses titles and the action bar.
 */
public final class BossBarHandle {

    private final Object bar;
    private final Method setTitle;
    private final Method setProgress;
    private final Method addPlayer;
    private final Method removePlayer;
    private final Method removeAll;

    private BossBarHandle(Object bar) throws Exception {
        this.bar = bar;
        Class<?> type = bar.getClass();
        this.setTitle = type.getMethod("setTitle", String.class);
        this.setProgress = type.getMethod("setProgress", double.class);
        this.addPlayer = type.getMethod("addPlayer", Player.class);
        this.removePlayer = type.getMethod("removePlayer", Player.class);
        this.removeAll = type.getMethod("removeAll");
    }

    public static BossBarHandle create(String title, double progress) {
        try {
            Class<?> colorClass = Class.forName("org.bukkit.boss.BarColor");
            Class<?> styleClass = Class.forName("org.bukkit.boss.BarStyle");
            Object color = Enum.valueOf(colorClass.asSubclass(Enum.class), "BLUE");
            Object style = Enum.valueOf(styleClass.asSubclass(Enum.class), "SOLID");
            Method create = Bukkit.class.getMethod("createBossBar", String.class, colorClass, styleClass);
            Object bar = create.invoke(null, title, color, style);
            BossBarHandle handle = new BossBarHandle(bar);
            handle.progress(progress);
            return handle;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public void add(Player player) {
        try {
            addPlayer.invoke(bar, player);
        } catch (Throwable ignored) {
        }
    }

    public void remove(Player player) {
        try {
            removePlayer.invoke(bar, player);
        } catch (Throwable ignored) {
        }
    }

    public void title(String title) {
        try {
            setTitle.invoke(bar, title);
        } catch (Throwable ignored) {
        }
    }

    public void progress(double progress) {
        try {
            setProgress.invoke(bar, Math.max(0.0D, Math.min(1.0D, progress)));
        } catch (Throwable ignored) {
        }
    }

    public void removeAll() {
        try {
            removeAll.invoke(bar);
        } catch (Throwable ignored) {
        }
    }
}
