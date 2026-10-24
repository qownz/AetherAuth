package com.aetherauth.platform;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.aetherauth.config.ConfigData;
import com.aetherauth.config.ConfigManager;
import com.aetherauth.util.ServerVersion;
import com.aetherauth.util.Text;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * All chat/title/action-bar/sound output goes through here so colour handling
 * and version differences stay in one place.
 */
public final class Chat {

    private static final Map<String, String> LEGACY_SOUNDS = new ConcurrentHashMap<String, String>();

    static {
        LEGACY_SOUNDS.put("entity.player.levelup", "random.levelup");
        LEGACY_SOUNDS.put("block.note_block.pling", "note.pling");
        LEGACY_SOUNDS.put("entity.villager.no", "mob.villager.no");
        LEGACY_SOUNDS.put("ui.button.click", "random.click");
        LEGACY_SOUNDS.put("block.chest.close", "random.chestclosed");
        LEGACY_SOUNDS.put("block.chest.open", "random.chestopen");
        LEGACY_SOUNDS.put("entity.experience_orb.pickup", "random.orb");
        LEGACY_SOUNDS.put("block.beacon.activate", "random.levelup");
    }

    private final ConfigManager config;
    private final ServerVersion version;

    public Chat(ConfigManager config, ServerVersion version) {
        this.config = config;
        this.version = version;
    }

    public ConfigManager config() {
        return config;
    }

    public void send(CommandSender sender, String legacy) {
        if (sender == null || legacy == null || legacy.isEmpty()) {
            return;
        }
        String[] lines = legacy.split("\n", -1);
        for (String line : lines) {
            if (!line.isEmpty()) {
                sender.sendMessage(line);
            }
        }
    }

    public void msg(CommandSender sender, String key, Object... placeholders) {
        send(sender, config.message(key, placeholders));
    }

    public void raw(CommandSender sender, String template, Object... placeholders) {
        send(sender, Text.legacy(template, placeholders));
    }

    public void empty(CommandSender sender) {
        sender.sendMessage(" ");
    }

    public void title(Player player, String title, String subtitle) {
        title(player, title, subtitle, 10, 70, 20);
    }

    @SuppressWarnings("deprecation")
    public void title(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        String safeTitle = title == null ? "" : title;
        String safeSubtitle = subtitle == null ? "" : subtitle;
        try {
            java.lang.reflect.Method method = Player.class.getMethod("sendTitle", String.class, String.class,
                    int.class, int.class, int.class);
            method.invoke(player, safeTitle, safeSubtitle,
                    Math.max(0, fadeIn), Math.max(1, stay), Math.max(0, fadeOut));
            return;
        } catch (Throwable ignored) {
            // 1.8.8 only exposes the two argument variant.
        }
        try {
            java.lang.reflect.Method method = Player.class.getMethod("sendTitle", String.class, String.class);
            method.invoke(player, safeTitle, safeSubtitle);
            return;
        } catch (Throwable ignored) {
            // No title support at all.
        }
        send(player, safeTitle);
    }

    public void actionBar(Player player, String legacy) {
        if (legacy == null || legacy.isEmpty()) {
            return;
        }
        try {
            java.lang.reflect.Method method = Player.class.getMethod("sendActionBar", String.class);
            method.invoke(player, legacy);
            return;
        } catch (Throwable ignored) {
            // Spigot does not expose sendActionBar directly.
        }
        try {
            Class<?> messageType = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Class<?> textComponent = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Object actionBar = Enum.valueOf(messageType.asSubclass(Enum.class), "ACTION_BAR");
            Object components = textComponent.getMethod("fromLegacyText", String.class)
                    .invoke(null, legacy);
            Object spigot = Player.class.getMethod("spigot").invoke(player);
            java.lang.reflect.Method send = spigot.getClass().getMethod("sendMessage",
                    messageType, components.getClass());
            send.invoke(spigot, actionBar, components);
        } catch (Throwable ignored) {
            // Last resort: no action bar support, do not spam chat.
        }
    }

    public void sound(Player player, String key) {
        ConfigData.Sounds sounds = config.sounds();
        if (!sounds.enabled) {
            return;
        }
        String name;
        if ("login".equals(key)) {
            name = sounds.login;
        } else if ("register".equals(key)) {
            name = sounds.register;
        } else if ("error".equals(key)) {
            name = sounds.error;
        } else if ("logout".equals(key)) {
            name = sounds.logout;
        } else {
            name = sounds.click;
        }
        play(player, name, sounds.volume, sounds.pitch);
    }

    public void play(Player player, String name, float volume, float pitch) {
        if (player == null || name == null || name.isEmpty()) {
            return;
        }
        String resolved = name;
        if (!version.atLeast(1, 9, 0)) {
            String legacy = LEGACY_SOUNDS.get(name.toLowerCase(Locale.ROOT));
            if (legacy != null) {
                resolved = legacy;
            }
        }
        try {
            player.playSound(player.getLocation(), resolved, volume, pitch);
        } catch (Throwable ignored) {
            // Unknown sound on this version — never break authentication for it.
        }
    }
}
