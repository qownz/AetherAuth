package com.aetherauth.command;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.aetherauth.AetherAuthPlugin;
import com.aetherauth.auth.EmailValidator;
import com.aetherauth.crypto.CryptoEngine;
import com.aetherauth.storage.AuthRecord;
import com.aetherauth.util.TimeUtil;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * /aetherauth (alias /aa) — reload, info, unregister, resetpassword, resetmail,
 * backup, stats.
 */
public final class AdminCommand implements TabExecutor {

    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "reload", "info", "unregister", "resetpassword", "resetmail", "backup", "stats", "help");

    private final AetherAuthPlugin plugin;
    private final SecureRandom random = new SecureRandom();

    public AdminCommand(AetherAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aetherauth.admin")) {
            plugin.chat().msg(sender, "common.no-permission");
            return true;
        }
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            plugin.chat().msg(sender, "admin.usage");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("reload".equals(sub)) {
            if (!require(sender, "aetherauth.admin.reload")) {
                return true;
            }
            long start = System.currentTimeMillis();
            plugin.reloadEverything();
            plugin.chat().msg(sender, "admin.reloaded", "time", System.currentTimeMillis() - start);
            return true;
        }
        if ("info".equals(sub)) {
            if (!require(sender, "aetherauth.admin.info")) {
                return true;
            }
            if (args.length < 2) {
                plugin.chat().msg(sender, "admin.invalid-usage", "usage", "/aetherauth info <oyuncu>");
                return true;
            }
            info(sender, args[1]);
            return true;
        }
        if ("unregister".equals(sub)) {
            if (!require(sender, "aetherauth.admin.unregister")) {
                return true;
            }
            if (args.length < 2) {
                plugin.chat().msg(sender, "admin.invalid-usage", "usage", "/aetherauth unregister <oyuncu>");
                return true;
            }
            unregister(sender, args[1]);
            return true;
        }
        if ("resetpassword".equals(sub) || "reset".equals(sub)) {
            if (!require(sender, "aetherauth.admin.reset")) {
                return true;
            }
            if (args.length < 2) {
                plugin.chat().msg(sender, "admin.invalid-usage", "usage", "/aetherauth resetpassword <oyuncu> [yeni]");
                return true;
            }
            String password = args.length >= 3 ? args[2] : generatePassword();
            if (password.length() < plugin.config().auth().minPasswordLength
                    || password.length() > plugin.config().auth().maxPasswordLength) {
                plugin.chat().msg(sender, "register.password-weak", "reason",
                        "en az " + plugin.config().auth().minPasswordLength + " karakter olmalı");
                return true;
            }
            resetPassword(sender, args[1], password);
            return true;
        }
        if ("resetmail".equals(sub)) {
            if (!require(sender, "aetherauth.admin.reset")) {
                return true;
            }
            if (args.length < 3) {
                plugin.chat().msg(sender, "admin.invalid-usage", "usage",
                        "/aetherauth resetmail <oyuncu> <yeni-mail>");
                return true;
            }
            String email = args[2].trim();
            if (!EmailValidator.isValid(email)) {
                plugin.chat().msg(sender, "admin.resetmail-invalid");
                return true;
            }
            resetEmail(sender, args[1], email);
            return true;
        }
        if ("backup".equals(sub)) {
            if (!require(sender, "aetherauth.admin.backup")) {
                return true;
            }
            backup(sender);
            return true;
        }
        if ("stats".equals(sub)) {
            if (!require(sender, "aetherauth.admin.stats")) {
                return true;
            }
            stats(sender);
            return true;
        }

        plugin.chat().msg(sender, "common.unknown-subcommand");
        return true;
    }

    private boolean require(CommandSender sender, String node) {
        if (sender.hasPermission("aetherauth.admin") || sender.hasPermission(node)) {
            return true;
        }
        plugin.chat().msg(sender, "common.no-permission");
        return false;
    }

    private void info(final CommandSender sender, final String name) {
        plugin.async().execute(new Runnable() {
            @Override
            public void run() {
                final AuthRecord record = plugin.storage().findByName(name);
                int altAccounts = 0;
                if (record != null) {
                    String ip = record.registrationIp;
                    if (ip == null || ip.trim().isEmpty()) {
                        ip = record.lastIp;
                    }
                    if (ip != null && !ip.trim().isEmpty()) {
                        try {
                            altAccounts = Math.max(0,
                                    plugin.storage().countByRegistrationIp(ip) - 1);
                        } catch (Throwable throwable) {
                            plugin.getLogger().warning(
                                    "Yan hesap sayısı hesaplanamadı: " + throwable.getMessage());
                        }
                    }
                }
                final int alt = altAccounts;
                plugin.runSync(new Runnable() {
                    @Override
                    public void run() {
                        if (record == null) {
                            plugin.chat().msg(sender, "admin.not-found", "player", name);
                            return;
                        }
                        plugin.chat().msg(sender, "admin.info",
                                "player", record.name,
                                "uuid", record.uuid.toString(),
                                "email", CryptoEngine.maskEmail(record.email),
                                "created", TimeUtil.formatDate(record.createdAt),
                                "last_login", TimeUtil.formatDate(record.lastLoginAt),
                                "ip", record.lastIp == null ? "-" : record.lastIp,
                                "alt_accounts", alt);
                    }
                });
            }
        });
    }

    private void unregister(final CommandSender sender, final String name) {
        plugin.async().execute(new Runnable() {
            @Override
            public void run() {
                final AuthRecord record = plugin.storage().findByName(name);
                if (record == null) {
                    plugin.runSync(new Runnable() {
                        @Override
                        public void run() {
                            plugin.chat().msg(sender, "admin.not-found", "player", name);
                        }
                    });
                    return;
                }
                try {
                    plugin.storage().delete(record.uuid);
                    plugin.cache().remove(record.uuid);
                    plugin.runSync(new Runnable() {
                        @Override
                        public void run() {
                            plugin.chat().msg(sender, "admin.unregistered", "player", record.name);
                            Player online = Bukkit.getPlayerExact(record.name);
                            if (online != null) {
                                online.kickPlayer(plugin.config().message("admin.unregistered-kick"));
                            }
                        }
                    });
                } catch (final Throwable throwable) {
                    plugin.getLogger().severe("Hesap silinemedi: " + throwable.getMessage());
                    plugin.runSync(new Runnable() {
                        @Override
                        public void run() {
                            plugin.chat().msg(sender, "common.internal-error");
                        }
                    });
                }
            }
        });
    }

    private void resetPassword(final CommandSender sender, final String name, final String password) {
        plugin.async().execute(new Runnable() {
            @Override
            public void run() {
                final AuthRecord record = plugin.storage().findByName(name);
                if (record == null) {
                    plugin.runSync(new Runnable() {
                        @Override
                        public void run() {
                            plugin.chat().msg(sender, "admin.not-found", "player", name);
                        }
                    });
                    return;
                }
                try {
                    final String encoded = plugin.crypto().hash(password);
                    final String algorithm = plugin.crypto().variant() + ":"
                            + plugin.crypto().iterations() + ":" + plugin.crypto().keyBits();
                    plugin.storage().updatePassword(record.uuid, encoded, algorithm, System.currentTimeMillis());
                    record.password = encoded;
                    record.algorithm = algorithm;
                    plugin.cache().put(record);
                    plugin.runSync(new Runnable() {
                        @Override
                        public void run() {
                            Player online = Bukkit.getPlayerExact(record.name);
                            if (online != null) {
                                com.aetherauth.auth.AuthSession session = plugin.auth().session(online);
                                if (session != null && session.record != null) {
                                    session.record.password = encoded;
                                    session.record.algorithm = algorithm;
                                }
                                plugin.chat().msg(online, "admin.reset-target");
                            }
                            plugin.chat().msg(sender, "admin.reset",
                                    "player", record.name, "password", password);
                        }
                    });
                } catch (Throwable throwable) {
                    plugin.getLogger().severe("Şifre sıfırlanamadı: " + throwable.getMessage());
                    plugin.runSync(new Runnable() {
                        @Override
                        public void run() {
                            plugin.chat().msg(sender, "common.internal-error");
                        }
                    });
                }
            }
        });
    }

    private void resetEmail(final CommandSender sender, final String name, final String email) {
        plugin.async().execute(new Runnable() {
            @Override
            public void run() {
                final AuthRecord record = plugin.storage().findByName(name);
                if (record == null) {
                    plugin.runSync(new Runnable() {
                        @Override
                        public void run() {
                            plugin.chat().msg(sender, "admin.not-found", "player", name);
                        }
                    });
                    return;
                }
                try {
                    final String lookup = plugin.crypto().emailLookup(email);
                    if (lookup.equals(record.emailLookup)) {
                        plugin.runSync(new Runnable() {
                            @Override
                            public void run() {
                                plugin.chat().msg(sender, "admin.resetmail-same");
                            }
                        });
                        return;
                    }
                    if (plugin.storage().emailExists(lookup)) {
                        plugin.runSync(new Runnable() {
                            @Override
                            public void run() {
                                plugin.chat().msg(sender, "admin.resetmail-taken");
                            }
                        });
                        return;
                    }
                    final String stored = plugin.config().security().encryptEmail
                            ? plugin.crypto().encryptEmail(email) : "p:" + email;
                    plugin.storage().updateEmail(record.uuid, stored, lookup,
                            System.currentTimeMillis());
                    record.email = email;
                    record.emailStored = stored;
                    record.emailLookup = lookup;
                    plugin.cache().put(record);
                    plugin.runSync(new Runnable() {
                        @Override
                        public void run() {
                            Player online = Bukkit.getPlayerExact(record.name);
                            if (online != null) {
                                com.aetherauth.auth.AuthSession session = plugin.auth().session(online);
                                if (session != null && session.record != null) {
                                    session.record.email = email;
                                    session.record.emailStored = stored;
                                    session.record.emailLookup = lookup;
                                }
                                plugin.chat().msg(online, "admin.resetmail-target");
                            }
                            plugin.chat().msg(sender, "admin.resetmail",
                                    "player", record.name, "email", email);
                        }
                    });
                } catch (Throwable throwable) {
                    plugin.getLogger().severe("E-posta sıfırlanamadı: " + throwable.getMessage());
                    plugin.runSync(new Runnable() {
                        @Override
                        public void run() {
                            plugin.chat().msg(sender, "common.internal-error");
                        }
                    });
                }
            }
        });
    }

    private void backup(final CommandSender sender) {
        plugin.async().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final java.nio.file.Path path = plugin.backups().createBackup();
                    plugin.runSync(new Runnable() {
                        @Override
                        public void run() {
                            String relative = plugin.getDataFolder().toPath()
                                    .relativize(path).toString().replace('\\', '/');
                            plugin.chat().msg(sender, "admin.backup", "file", relative);
                        }
                    });
                } catch (final Throwable throwable) {
                    plugin.getLogger().severe("Yedek alınamadı: " + throwable.getMessage());
                    plugin.runSync(new Runnable() {
                        @Override
                        public void run() {
                            plugin.chat().msg(sender, "admin.backup-failed",
                                    "reason", throwable.getMessage() == null ? "?" : throwable.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void stats(final CommandSender sender) {
        final int online = Bukkit.getOnlinePlayers().size();
        final int pending = plugin.auth().pendingCount();
        final String size = TimeUtil.humanBytes(plugin.storage().databaseSizeBytes());
        plugin.async().execute(new Runnable() {
            @Override
            public void run() {
                final int registered;
                final int altAccounts;
                try {
                    registered = plugin.storage().count();
                    altAccounts = plugin.storage().countAltAccounts();
                } catch (Throwable throwable) {
                    plugin.runSync(new Runnable() {
                        @Override
                        public void run() {
                            plugin.chat().msg(sender, "common.internal-error");
                        }
                    });
                    return;
                }
                plugin.runSync(new Runnable() {
                    @Override
                    public void run() {
                        plugin.chat().msg(sender, "admin.stats",
                                "registered", registered,
                                "alt_accounts", altAccounts,
                                "online", online,
                                "pending", pending,
                                "size", size,
                                "version", plugin.version().describe(),
                                "dialog", plugin.dialogs().status());
                    }
                });
            }
        });
    }

    private String generatePassword() {
        StringBuilder builder = new StringBuilder(14);
        for (int i = 0; i < 14; i++) {
            builder.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return builder.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("aetherauth.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> result = new ArrayList<String>();
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 2 && ("info".equalsIgnoreCase(args[0])
                || "unregister".equalsIgnoreCase(args[0])
                || "resetpassword".equalsIgnoreCase(args[0])
                || "resetmail".equalsIgnoreCase(args[0])
                || "reset".equalsIgnoreCase(args[0]))) {
            List<String> names = new ArrayList<String>();
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(player.getName());
                }
            }
            return names;
        }
        return Collections.emptyList();
    }
}
