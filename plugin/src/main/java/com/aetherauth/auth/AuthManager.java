package com.aetherauth.auth;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.aetherauth.AetherAuthPlugin;
import com.aetherauth.api.DialogOutcome;
import com.aetherauth.api.DialogRequest;
import com.aetherauth.api.event.AetherAuthLoginEvent;
import com.aetherauth.api.event.AetherAuthLogoutEvent;
import com.aetherauth.api.event.AetherAuthPasswordChangeEvent;
import com.aetherauth.api.event.AetherAuthRegisterEvent;
import com.aetherauth.config.ConfigData;
import com.aetherauth.config.ConfigManager;
import com.aetherauth.crypto.CryptoEngine;
import com.aetherauth.platform.BossBarHandle;
import com.aetherauth.platform.Chat;
import com.aetherauth.storage.AuthCache;
import com.aetherauth.storage.AuthRecord;
import com.aetherauth.storage.DuplicateEntryException;
import com.aetherauth.storage.Storage;
import com.aetherauth.util.TimeUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The authentication state machine.
 *
 * <p>Bukkit objects are only touched on the server thread. Password hashing,
 * e-mail lookups and database writes always run on the worker pool, and the
 * result is handed back with {@link AetherAuthPlugin#runSync(Runnable)}.</p>
 */
public final class AuthManager {

    private final AetherAuthPlugin plugin;
    private final ConfigManager config;
    private final Storage storage;
    private final AuthCache cache;
    private final Chat chat;
    private final RateLimiter rateLimiter = new RateLimiter();
    private final Map<UUID, AuthSession> sessions = new ConcurrentHashMap<UUID, AuthSession>();

    private volatile CryptoEngine crypto;
    private volatile PasswordPolicy policy;
    private volatile boolean warnedAboutSpawn;

    public AuthManager(AetherAuthPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.config();
        this.storage = plugin.storage();
        this.crypto = plugin.crypto();
        this.cache = plugin.cache();
        this.chat = plugin.chat();
        this.policy = new PasswordPolicy(config.auth());
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    public void handleJoin(final Player player) {
        final UUID uuid = player.getUniqueId();
        AuthSession previous = sessions.remove(uuid);
        if (previous != null) {
            cancelTasks(previous);
        }
        final AuthSession session = new AuthSession(uuid, player.getName());
        session.returnLocation = player.getLocation().clone();
        sessions.put(uuid, session);

        applyLockdown(player, session);
        chat.msg(player, "join.checking");
        startTimeout(player, session);
        startHud(player, session);

        final String name = player.getName();
        plugin.async().execute(new Runnable() {
            @Override
            public void run() {
                AuthRecord record = null;
                Throwable failure = null;
                try {
                    record = cache.get(name);
                    if (record == null) {
                        record = storage.findByName(name);
                        if (record != null) {
                            cache.put(record);
                        }
                    }
                } catch (Throwable throwable) {
                    failure = throwable;
                }
                final AuthRecord found = record;
                final Throwable error = failure;
                plugin.runSync(new Runnable() {
                    @Override
                    public void run() {
                        afterLookup(player, session, found, error);
                    }
                });
            }
        });
    }

    private void afterLookup(Player player, AuthSession session, AuthRecord record, Throwable error) {
        if (!isCurrent(player, session)) {
            return;
        }
        if (error != null) {
            plugin.getLogger().log(Level.SEVERE, "Hesap sorgulanamadı: " + player.getName(), error);
            chat.msg(player, "common.internal-error");
            player.kickPlayer(config.message("common.internal-error"));
            return;
        }

        if (record == null) {
            session.state = AuthState.REGISTER;
            chat.msg(player, "join.register-required", "player", player.getName());
            chat.title(player, config.message("title.register"), config.message("subtitle.register"));
            chat.sound(player, "register");
            if (!plugin.dialogs().available() || !config.gui().autoOpen) {
                chat.msg(player, "join.usage.register");
            }
            scheduleDialog(player, session);
            return;
        }

        session.record = record;
        session.attempts = record.failedAttempts;
        session.lockedUntil = record.lockedUntil;
        session.state = AuthState.LOGIN;
        chat.msg(player, "join.login-required", "player", player.getName());
        chat.title(player, config.message("title.login"), config.message("subtitle.login"));
        chat.sound(player, "click");

        long now = System.currentTimeMillis();
        if (record.isLocked(now)) {
            chat.msg(player, "login.locked", "seconds", TimeUtil.secondsUntil(record.lockedUntil));
        } else if (canAutoLogin(player, record, now)) {
            completeLogin(player, session, LoginMode.AUTOMATIC);
            return;
        }

        if (!plugin.dialogs().available() || !config.gui().autoOpen) {
            chat.msg(player, "join.usage.login");
        }
        scheduleDialog(player, session);
    }

    private boolean canAutoLogin(Player player, AuthRecord record, long now) {
        ConfigData.Auth auth = config.auth();
        if (!auth.sessionsEnabled || auth.sessionDurationMinutes <= 0) {
            return false;
        }
        if (record.autoLoginUntil <= now) {
            return false;
        }
        if (auth.sessionBindIp && record.lastIp != null && !record.lastIp.equals(ip(player))) {
            return false;
        }
        return true;
    }

    private void applyLockdown(Player player, AuthSession session) {
        ConfigData.Auth auth = config.auth();

        if (auth.teleportToAuthSpawn) {
            teleportToAuthSpawn(player);
        }
        if (auth.blindness) {
            try {
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                        20 * 60 * 30, 0, true, false), true);
            } catch (Throwable ignored) {
                // Some forks reject very long effects; not critical.
            }
        }
        if (auth.hidePlayers) {
            refreshVisibility(player);
        }
        try {
            player.closeInventory();
        } catch (Throwable ignored) {
        }
        player.setFallDistance(0.0F);
        session.lastActivityAt = System.currentTimeMillis();
    }

    private void teleportToAuthSpawn(Player player) {
        ConfigData.Spawn spawn = config.auth().authSpawn;
        World world = Bukkit.getWorld(spawn.world);
        if (world == null) {
            if (!warnedAboutSpawn) {
                warnedAboutSpawn = true;
                plugin.getLogger().warning("authSpawn.world bulunamadı: " + spawn.world);
            }
            return;
        }
        Location location = new Location(world, spawn.x, spawn.y, spawn.z, spawn.yaw, spawn.pitch);
        player.teleport(location);
    }

    private void startTimeout(final Player player, final AuthSession session) {
        int seconds = config.auth().timeoutSeconds;
        if (seconds <= 0) {
            return;
        }
        session.timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!isCurrent(player, session) || session.isAuthenticated()) {
                    return;
                }
                session.timedOut = true;
                chat.title(player, config.message("title.timeout"),
                        config.message("subtitle.timeout", "seconds", config.auth().timeoutSeconds));
                player.kickPlayer(config.message("login.timeout", "seconds", config.auth().timeoutSeconds));
            }
        }, seconds * 20L);
    }

    private void startHud(final Player player, final AuthSession session) {
        int seconds = config.auth().timeoutSeconds;
        if (seconds > 0) {
            session.bossBar = BossBarHandle.create(
                    config.message("bossbar.waiting", "seconds", seconds), 1.0D);
            if (session.bossBar != null) {
                session.bossBar.add(player);
            }
        }
        final int[] tick = {0};
        session.hudTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (!isCurrent(player, session) || session.isAuthenticated()) {
                    stopHud(session);
                    return;
                }
                tick[0]++;
                long remaining = seconds;
                if (seconds > 0) {
                    long elapsed = (System.currentTimeMillis() - session.joinedAt) / 1000L;
                    remaining = Math.max(0L, seconds - elapsed);
                }
                if (session.lockedUntil > System.currentTimeMillis()) {
                    long locked = TimeUtil.secondsUntil(session.lockedUntil);
                    if (session.bossBar != null) {
                        session.bossBar.title(config.message("actionbar.locked", "seconds", locked));
                        session.bossBar.progress(locked <= 0 ? 0.0D
                                : Math.min(1.0D, (double) locked / Math.max(1, config.auth().lockoutSeconds)));
                    }
                    if (tick[0] % 2 == 0) {
                        chat.actionBar(player, config.message("actionbar.locked", "seconds", locked));
                    }
                    return;
                }
                if (session.bossBar != null) {
                    session.bossBar.title(config.message("bossbar.waiting", "seconds", remaining));
                    session.bossBar.progress(seconds <= 0 ? 1.0D : (double) remaining / (double) seconds);
                }
                if (tick[0] % 3 == 0) {
                    chat.actionBar(player, config.message("actionbar.waiting"));
                }
            }
        }, 20L, 20L);
    }

    private void stopHud(AuthSession session) {
        if (session.hudTask != null) {
            try {
                session.hudTask.cancel();
            } catch (Throwable ignored) {
            }
            session.hudTask = null;
        }
        if (session.bossBar != null) {
            session.bossBar.removeAll();
            session.bossBar = null;
        }
    }

    private void stopTimeout(AuthSession session) {
        if (session.timeoutTask != null) {
            try {
                session.timeoutTask.cancel();
            } catch (Throwable ignored) {
            }
            session.timeoutTask = null;
        }
    }

    private void cancelTasks(AuthSession session) {
        stopTimeout(session);
        stopHud(session);
        if (session.dialogTask != null) {
            try {
                session.dialogTask.cancel();
            } catch (Throwable ignored) {
            }
            session.dialogTask = null;
        }
    }

    // ------------------------------------------------------------------
    // Dialog
    // ------------------------------------------------------------------

    private void scheduleDialog(final Player player, final AuthSession session) {
        if (!plugin.dialogs().available() || !config.gui().autoOpen) {
            return;
        }
        int delay = Math.max(0, config.gui().openDelayTicks);
        session.dialogTask = Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!isCurrent(player, session) || session.processing) {
                    return;
                }
                if (session.state == AuthState.REGISTER || session.state == AuthState.LOGIN) {
                    openAuthScreen(player);
                }
            }
        }, delay);
    }

    /** Opens the correct screen for the current state, or prints the usage hint. */
    public void openAuthScreen(final Player player) {
        final AuthSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.state == AuthState.AUTHENTICATED) {
            chat.msg(player, "login.already");
            return;
        }
        if (session.state == AuthState.PROCESSING || session.state == AuthState.LOADING) {
            chat.msg(player, "login.processing");
            return;
        }
        if (!plugin.dialogs().available()) {
            chat.msg(player, session.state == AuthState.REGISTER ? "join.usage.register" : "join.usage.login");
            return;
        }
        if (session.processing || session.dialogOpen) {
            return;
        }
        final boolean register = session.state == AuthState.REGISTER;
        DialogRequest request = buildRequest(player, session, register);
        session.dialogOpen = true;
        plugin.dialogs().show(player, request, new java.util.function.Consumer<DialogOutcome>() {
            @Override
            public void accept(final DialogOutcome outcome) {
                plugin.runSync(new Runnable() {
                    @Override
                    public void run() {
                        session.dialogOpen = false;
                        if (!isCurrent(player, session)) {
                            return;
                        }
                        if (outcome == null || !outcome.isSubmit()) {
                            chat.msg(player, session.state == AuthState.REGISTER
                                    ? "join.usage.register" : "login.cancelled");
                            return;
                        }
                        String email = outcome.email() == null ? "" : outcome.email().trim();
                        session.lastEmail = email;
                        if (session.state == AuthState.REGISTER) {
                            register(player, outcome.email(), outcome.password(), outcome.confirmPassword());
                        } else if (session.state == AuthState.LOGIN) {
                            login(player, outcome.password());
                        }
                    }
                });
            }
        });
    }

    private DialogRequest buildRequest(Player player, AuthSession session, boolean register) {
        String playerName = player.getName();
        return DialogRequest.builder()
                .title(config.miniMessage(register ? "dialog.register.title" : "dialog.login.title",
                        "player", playerName))
                .bodyLine(config.miniMessage(register ? "dialog.register.body1" : "dialog.login.body1",
                        "player", playerName))
                .bodyLine(config.miniMessage(register ? "dialog.register.body2" : "dialog.login.body2",
                        "player", playerName))
                .emailField(register && config.gui().showEmail && config.auth().requireEmail)
                .passwordField(true)
                .confirmField(register && config.gui().confirmPassword)
                .canClose(config.gui().canClose)
                .emailLabel(config.miniMessage("dialog.email.label"))
                .passwordLabel(config.miniMessage(register
                        ? "dialog.password.label-register" : "dialog.password.label",
                        "min", config.auth().minPasswordLength))
                .confirmLabel(config.miniMessage("dialog.confirm.label"))
                .submitLabel(config.miniMessage(register ? "dialog.submit.register" : "dialog.submit.login"))
                .cancelLabel(config.miniMessage("dialog.cancel"))
                .initialEmail(register ? session.lastEmail : "")
                .width(config.gui().width)
                .buttonWidth(config.gui().buttonWidth)
                .maxPasswordLength(config.auth().maxPasswordLength)
                .build();
    }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    public void register(final Player player, String rawEmail, String password, String confirmPassword) {
        final AuthSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (!config.auth().registerEnabled) {
            chat.msg(player, "register.disabled");
            return;
        }
        if (session.state != AuthState.REGISTER) {
            chat.msg(player, session.state == AuthState.AUTHENTICATED || session.state == AuthState.LOGIN
                    ? "register.already" : "login.processing");
            return;
        }
        if (session.processing) {
            return;
        }
        if (password == null) {
            password = "";
        }
        String email = rawEmail == null ? "" : rawEmail.trim();
        ConfigData.Auth auth = config.auth();
        if (auth.requireEmail) {
            if (!EmailValidator.isValid(email)) {
                chat.msg(player, "register.email-invalid");
                chat.sound(player, "error");
                reopenLater(player, session, true);
                return;
            }
        } else if (!email.isEmpty() && !EmailValidator.isValid(email)) {
            chat.msg(player, "register.email-invalid");
            chat.sound(player, "error");
            reopenLater(player, session, true);
            return;
        }
        if (confirmPassword != null && !confirmPassword.isEmpty() && !password.equals(confirmPassword)) {
            chat.msg(player, "register.password-mismatch");
            chat.sound(player, "error");
            reopenLater(player, session, true);
            return;
        }
        String reason = policy.validate(password, player.getName(), email);
        if (reason != null) {
            chat.msg(player, "register.password-weak", "reason", reason);
            chat.sound(player, "error");
            reopenLater(player, session, true);
            return;
        }
        final String playerIp = ip(player);
        long cooldown = rateLimiter.registerCooldownRemaining(playerIp, auth.registerCooldownSeconds);
        if (cooldown > 0L) {
            chat.msg(player, "register.cooldown", "seconds", (cooldown + 999L) / 1000L);
            reopenLater(player, session, true);
            return;
        }

        AetherAuthRegisterEvent event = new AetherAuthRegisterEvent(player, email);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            chat.msg(player, "register.cancelled");
            reopenLater(player, session, true);
            return;
        }

        session.processing = true;
        session.lastEmail = email;
        final String finalEmail = email;
        final String finalPassword = password;

        plugin.async().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String lookup = null;
                    String stored = null;
                    if (!finalEmail.isEmpty() && (config.auth().requireEmail || config.security().encryptEmail)) {
                        lookup = crypto.emailLookup(finalEmail);
                        if (storage.emailExists(lookup)) {
                            failRegister(player, session, "register.email-taken", finalEmail);
                            return;
                        }
                    }
                    int limit = config.auth().maxAccountsPerIp;
                    if (limit > 0 && playerIp != null && storage.countByRegistrationIp(playerIp) >= limit) {
                        failRegister(player, session, "register.limit", finalEmail);
                        return;
                    }
                    String encoded = crypto.hash(finalPassword);
                    long now = System.currentTimeMillis();
                    AuthRecord record = new AuthRecord();
                    record.uuid = player.getUniqueId();
                    record.name = player.getName();
                    record.nameLower = player.getName().toLowerCase(Locale.ROOT);
                    record.password = encoded;
                    record.algorithm = crypto.variant() + ":" + crypto.iterations() + ":" + crypto.keyBits();
                    record.createdAt = now;
                    record.updatedAt = now;
                    record.lastLoginAt = 0L;
                    record.autoLoginUntil = 0L;
                    record.lastIp = playerIp;
                    record.registrationIp = playerIp;
                    record.failedAttempts = 0;
                    record.lockedUntil = 0L;
                    if (!finalEmail.isEmpty()) {
                        record.email = finalEmail;
                        record.emailLookup = lookup;
                        stored = config.security().encryptEmail
                                ? crypto.encryptEmail(finalEmail) : "p:" + finalEmail;
                        record.emailStored = stored;
                    }
                    storage.insert(record);
                    cache.put(record);
                    rateLimiter.markRegister(playerIp);
                    final AuthRecord created = record;
                    plugin.runSync(new Runnable() {
                        @Override
                        public void run() {
                            if (!isCurrent(player, session)) {
                                return;
                            }
                            session.processing = false;
                            session.record = created;
                            session.attempts = 0;
                            session.lockedUntil = 0L;
                            audit(player, "REGISTER", finalEmail.isEmpty() ? "-" : finalEmail);
                            completeLogin(player, session, LoginMode.REGISTER);
                        }
                    });
                } catch (DuplicateEntryException duplicate) {
                    failRegister(player, session,
                            "email".equals(duplicate.field()) ? "register.email-taken" : "register.already",
                            finalEmail);
                } catch (Throwable throwable) {
                    plugin.getLogger().log(Level.SEVERE, "Kayıt oluşturulamadı: " + player.getName(), throwable);
                    failRegister(player, session, "common.internal-error", finalEmail);
                }
            }

            private void failRegister(Player player, AuthSession session, String key, String email) {
                plugin.runSync(new Runnable() {
                    @Override
                    public void run() {
                        if (!isCurrent(player, session)) {
                            return;
                        }
                        session.processing = false;
                        session.lastEmail = email;
                        chat.msg(player, key, "max", config.auth().maxAccountsPerIp, "email", email);
                        chat.sound(player, "error");
                        reopenLater(player, session, true);
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------------
    // Login
    // ------------------------------------------------------------------

    public void login(final Player player, String password) {
        final AuthSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.state == AuthState.AUTHENTICATED) {
            chat.msg(player, "login.already");
            return;
        }
        if (session.state == AuthState.REGISTER) {
            chat.msg(player, "login.unknown");
            return;
        }
        if (session.state != AuthState.LOGIN) {
            chat.msg(player, "login.processing");
            return;
        }
        if (session.processing) {
            return;
        }
        final AuthRecord record = session.record;
        if (record == null) {
            chat.msg(player, "login.unknown");
            return;
        }
        if (password == null || password.isEmpty()) {
            chat.msg(player, "login.usage");
            reopenLater(player, session, false);
            return;
        }
        long now = System.currentTimeMillis();
        if (record.lockedUntil > now) {
            chat.msg(player, "login.locked", "seconds", TimeUtil.secondsUntil(record.lockedUntil));
            reopenLater(player, session, false);
            return;
        }
        if (!rateLimiter.allowLogin(ip(player), config.auth().loginAttemptsPerIp,
                config.auth().loginWindowSeconds * 1000L)) {
            chat.msg(player, "login.rate-limited");
            return;
        }

        session.processing = true;
        final String finalPassword = password;
        plugin.async().execute(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                boolean needsRehash = false;
                String newHash = null;
                try {
                    CryptoEngine.Result result = crypto.verify(finalPassword, record.password);
                    ok = result.ok();
                    needsRehash = result.needsRehash();
                    if (ok && needsRehash && config.security().rehashOnLogin) {
                        try {
                            newHash = crypto.hash(finalPassword);
                        } catch (Throwable throwable) {
                            plugin.getLogger().log(Level.WARNING, "Şifre yeniden hashlenemedi.", throwable);
                        }
                    }
                } catch (Throwable throwable) {
                    plugin.getLogger().log(Level.WARNING, "Şifre doğrulanamadı.", throwable);
                }
                final boolean success = ok;
                final String rehashed = newHash;
                plugin.runSync(new Runnable() {
                    @Override
                    public void run() {
                        if (!isCurrent(player, session)) {
                            return;
                        }
                        session.processing = false;
                        if (success) {
                            if (rehashed != null) {
                                record.password = rehashed;
                                record.algorithm = crypto.variant() + ":" + crypto.iterations()
                                        + ":" + crypto.keyBits();
                                final String algorithm = record.algorithm;
                                plugin.async().execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        storage.updatePassword(record.uuid, rehashed, algorithm,
                                                System.currentTimeMillis());
                                    }
                                });
                            }
                            session.attempts = 0;
                            session.lockedUntil = 0L;
                            completeLogin(player, session, LoginMode.PASSWORD);
                        } else {
                            handleLoginFailure(player, session, record);
                        }
                    }
                });
            }
        });
    }

    private void handleLoginFailure(final Player player, AuthSession session, AuthRecord record) {
        long now = System.currentTimeMillis();
        if (record.lockedUntil > now) {
            chat.msg(player, "login.locked", "seconds", TimeUtil.secondsUntil(record.lockedUntil));
            reopenLater(player, session, false);
            return;
        }
        int attempts = record.failedAttempts + 1;
        int max = config.auth().maxAttempts;
        record.failedAttempts = attempts;
        session.attempts = attempts;
        chat.sound(player, "error");
        audit(player, "LOGIN_FAIL", "attempt=" + attempts);

        if (attempts >= max) {
            long lockMillis = Math.max(0, config.auth().lockoutSeconds) * 1000L;
            long lockedUntil = now + lockMillis;
            record.lockedUntil = lockedUntil;
            session.lockedUntil = lockedUntil;
            final int failed = attempts;
            final long until = lockedUntil;
            plugin.async().execute(new Runnable() {
                @Override
                public void run() {
                    storage.updateFailures(record.uuid, failed, until);
                }
            });
            cache.put(record);
            chat.msg(player, "login.locked", "seconds", TimeUtil.secondsUntil(lockedUntil));
            if (config.auth().kickOnMaxAttempts && lockMillis > 0L) {
                player.kickPlayer(config.message("login.kicked"));
            } else {
                reopenLater(player, session, false);
            }
            return;
        }

        final int failed = attempts;
        plugin.async().execute(new Runnable() {
            @Override
            public void run() {
                storage.updateFailures(record.uuid, failed, 0L);
            }
        });
        cache.put(record);
        chat.msg(player, "login.failed", "remaining", Math.max(0, max - attempts));
        reopenLater(player, session, false);
    }

    // ------------------------------------------------------------------
    // Completion / logout
    // ------------------------------------------------------------------

    private enum LoginMode {
        PASSWORD, REGISTER, AUTOMATIC
    }

    private void completeLogin(final Player player, final AuthSession session, LoginMode mode) {
        if (!isCurrent(player, session)) {
            return;
        }
        cancelTasks(session);
        session.state = AuthState.AUTHENTICATED;
        session.processing = false;
        session.dialogOpen = false;
        plugin.dialogs().close(player);

        try {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
        } catch (Throwable ignored) {
        }
        refreshVisibility(player);

        if (config.auth().teleportToAuthSpawn && config.auth().returnAfterLogin
                && session.returnLocation != null && session.returnLocation.getWorld() != null) {
            try {
                player.teleport(session.returnLocation);
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.FINE, "Giriş sonrası ışınlanma başarısız.", throwable);
            }
        }

        chat.title(player, config.message("title.success"),
                config.message("subtitle.success", "player", player.getName()));

        switch (mode) {
            case REGISTER:
                chat.msg(player, "register.success");
                chat.sound(player, "register");
                break;
            case AUTOMATIC:
                chat.msg(player, "join.autologin");
                chat.sound(player, "login");
                break;
            case PASSWORD:
            default:
                chat.msg(player, "login.success");
                chat.sound(player, "login");
                break;
        }

        if (mode == LoginMode.REGISTER) {
            runCommands(player, config.auth().registerCommands);
        }
        runCommands(player, config.auth().loginCommands);

        final boolean automatic = mode == LoginMode.AUTOMATIC;
        try {
            plugin.getServer().getPluginManager().callEvent(new AetherAuthLoginEvent(player, automatic));
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "AetherAuthLoginEvent gönderilemedi.", throwable);
        }

        final AuthRecord record = session.record;
        if (record != null) {
            long now = System.currentTimeMillis();
            long autoLoginUntil = 0L;
            if (config.auth().sessionsEnabled && config.auth().sessionDurationMinutes > 0) {
                autoLoginUntil = now + config.auth().sessionDurationMinutes * 60000L;
            }
            record.lastLoginAt = now;
            record.autoLoginUntil = autoLoginUntil;
            record.lastIp = ip(player);
            record.failedAttempts = 0;
            record.lockedUntil = 0L;
            cache.put(record);
            final long until = autoLoginUntil;
            final String address = ip(player);
            plugin.async().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        storage.updateLogin(record.uuid, System.currentTimeMillis(), address, until);
                    } catch (Throwable throwable) {
                        plugin.getLogger().log(Level.WARNING, "Giriş kaydı yazılamadı.", throwable);
                    }
                }
            });
            audit(player, "LOGIN", automatic ? "automatic" : "password");
        }
    }

    public void logout(final Player player) {
        final AuthSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.isAuthenticated()) {
            chat.msg(player, "logout.not-authenticated");
            return;
        }
        session.state = AuthState.LOGIN;
        session.processing = false;
        session.dialogOpen = false;
        session.attempts = 0;
        session.lockedUntil = 0L;
        if (session.record != null) {
            session.record.autoLoginUntil = 0L;
            final UUID uuid = session.record.uuid;
            plugin.async().execute(new Runnable() {
                @Override
                public void run() {
                    storage.clearAutoLogin(uuid);
                }
            });
        }
        applyLockdown(player, session);
        stopTimeout(session);
        stopHud(session);
        startTimeout(player, session);
        startHud(player, session);
        chat.msg(player, "logout.success");
        chat.title(player, config.message("title.login"), config.message("subtitle.login"));
        chat.sound(player, "logout");
        runCommands(player, config.auth().logoutCommands);
        try {
            plugin.getServer().getPluginManager().callEvent(new AetherAuthLogoutEvent(player));
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "AetherAuthLogoutEvent gönderilemedi.", throwable);
        }
        audit(player, "LOGOUT", "-");
        scheduleDialog(player, session);
    }

    // ------------------------------------------------------------------
    // Password / e-mail changes
    // ------------------------------------------------------------------

    public void changePassword(final Player player, String oldPassword, String newPassword) {
        final AuthSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.isAuthenticated()) {
            chat.msg(player, "password.not-authenticated");
            return;
        }
        if (oldPassword == null || newPassword == null || oldPassword.isEmpty() || newPassword.isEmpty()) {
            chat.msg(player, "password.usage");
            return;
        }
        if (oldPassword.equals(newPassword)) {
            chat.msg(player, "password.same");
            return;
        }
        String reason = policy.validate(newPassword, player.getName(),
                session.record == null ? "" : session.record.email);
        if (reason != null) {
            chat.msg(player, "register.password-weak", "reason", reason);
            return;
        }
        if (session.processing) {
            chat.msg(player, "login.processing");
            return;
        }
        session.processing = true;
        final AuthRecord record = session.record;
        if (record == null) {
            session.processing = false;
            chat.msg(player, "common.internal-error");
            return;
        }
        final String finalOld = oldPassword;
        final String finalNew = newPassword;
        plugin.async().execute(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    ok = crypto.verify(finalOld, record.password).ok();
                } catch (Throwable throwable) {
                    plugin.getLogger().log(Level.WARNING, "Mevcut şifre doğrulanamadı.", throwable);
                }
                final boolean valid = ok;
                plugin.runSync(new Runnable() {
                    @Override
                    public void run() {
                        if (!isCurrent(player, session)) {
                            return;
                        }
                        session.processing = false;
                        if (!valid) {
                            chat.msg(player, "password.old-wrong");
                            return;
                        }
                        if (config.gui().confirmChanges && plugin.dialogs().available()) {
                            openChangeDialog(player, session, false, finalOld, finalNew);
                        } else {
                            applyNewPassword(player, session, finalOld, finalNew, false);
                        }
                    }
                });
            }
        });
    }

    private void openChangeDialog(final Player player, final AuthSession session, final boolean email,
                                  String currentPassword, String initialValue) {
        if (session.processing || session.dialogOpen) {
            return;
        }
        final boolean useEmail = email;
        DialogRequest.Builder builder = DialogRequest.builder()
                .title(config.miniMessage("dialog.change.title"))
                .bodyLine(config.miniMessage(useEmail
                        ? "dialog.change.email-body1" : "dialog.change.password-body1"))
                .bodyLine(config.miniMessage(useEmail
                        ? "dialog.change.email-body2" : "dialog.change.password-body2"))
                .emailField(false)
                .passwordField(true)
                .passwordLabel(config.miniMessage("dialog.change.current-label"))
                .initialPassword(currentPassword == null ? "" : currentPassword)
                .confirmField(false)
                .valueField(true)
                .valueLabel(config.miniMessage(useEmail
                        ? "dialog.change.new-email" : "dialog.change.new-password"))
                .initialValue(initialValue == null ? "" : initialValue)
                .maxValueLength(useEmail ? 254 : config.auth().maxPasswordLength)
                .submitLabel(config.miniMessage("dialog.change.submit"))
                .cancelLabel(config.miniMessage("dialog.change.cancel"))
                .canClose(false)
                .width(config.gui().width)
                .buttonWidth(config.gui().buttonWidth);
        session.dialogOpen = true;
        plugin.dialogs().show(player, builder.build(), new java.util.function.Consumer<DialogOutcome>() {
            @Override
            public void accept(final DialogOutcome outcome) {
                plugin.runSync(new Runnable() {
                    @Override
                    public void run() {
                        session.dialogOpen = false;
                        if (!isCurrent(player, session)) {
                            return;
                        }
                        if (outcome == null || !outcome.isSubmit()) {
                            chat.msg(player, "change.cancelled");
                            return;
                        }
                        if (useEmail) {
                            applyNewEmail(player, session, outcome.password(), outcome.value(), true);
                        } else {
                            applyNewPassword(player, session, outcome.password(), outcome.value(), true);
                        }
                    }
                });
            }
        });
    }

    private void applyNewPassword(final Player player, final AuthSession session,
                                  String rawCurrent, String rawPassword, final boolean verifyCurrent) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            chat.msg(player, "password.usage");
            return;
        }
        final String newPassword = rawPassword;
        String reason = policy.validate(newPassword, player.getName(),
                session.record == null ? "" : session.record.email);
        if (reason != null) {
            chat.msg(player, "register.password-weak", "reason", reason);
            return;
        }
        if (session.processing) {
            chat.msg(player, "login.processing");
            return;
        }
        final AuthRecord record = session.record;
        if (record == null) {
            chat.msg(player, "common.internal-error");
            return;
        }
        final String currentPassword = rawCurrent == null ? "" : rawCurrent;
        session.processing = true;
        plugin.async().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (verifyCurrent && !crypto.verify(currentPassword, record.password).ok()) {
                        plugin.runSync(new Runnable() {
                            @Override
                            public void run() {
                                if (!isCurrent(player, session)) {
                                    return;
                                }
                                session.processing = false;
                                chat.msg(player, "password.old-wrong");
                            }
                        });
                        return;
                    }
                    if (crypto.verify(newPassword, record.password).ok()) {
                        plugin.runSync(new Runnable() {
                            @Override
                            public void run() {
                                if (!isCurrent(player, session)) {
                                    return;
                                }
                                session.processing = false;
                                chat.msg(player, "password.same");
                            }
                        });
                        return;
                    }
                    String encoded = crypto.hash(newPassword);
                    final String algorithm = crypto.variant() + ":" + crypto.iterations() + ":" + crypto.keyBits();
                    storage.updatePassword(record.uuid, encoded, algorithm, System.currentTimeMillis());
                    plugin.runSync(new Runnable() {
                        @Override
                        public void run() {
                            if (!isCurrent(player, session)) {
                                return;
                            }
                            session.processing = false;
                            record.password = encoded;
                            record.algorithm = algorithm;
                            cache.put(record);
                            chat.msg(player, "password.changed");
                            audit(player, "PASSWORD_CHANGE", "-");
                            try {
                                plugin.getServer().getPluginManager()
                                        .callEvent(new AetherAuthPasswordChangeEvent(player));
                            } catch (Throwable ignored) {
                            }
                        }
                    });
                } catch (Throwable throwable) {
                    plugin.getLogger().log(Level.SEVERE, "Şifre değiştirilemedi.", throwable);
                    plugin.runSync(new Runnable() {
                        @Override
                        public void run() {
                            if (isCurrent(player, session)) {
                                session.processing = false;
                                chat.msg(player, "common.internal-error");
                            }
                        }
                    });
                }
            }
        });
    }

    public void changeEmail(final Player player, String password, String newEmailRaw) {
        final AuthSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.isAuthenticated()) {
            chat.msg(player, "password.not-authenticated");
            return;
        }
        if (password == null || password.isEmpty() || newEmailRaw == null || newEmailRaw.trim().isEmpty()) {
            chat.msg(player, "email.usage");
            return;
        }
        final String newEmail = newEmailRaw.trim();
        if (!EmailValidator.isValid(newEmail)) {
            chat.msg(player, "email.invalid");
            return;
        }
        if (session.processing) {
            chat.msg(player, "login.processing");
            return;
        }
        final AuthRecord record = session.record;
        if (record == null) {
            chat.msg(player, "common.internal-error");
            return;
        }
        session.processing = true;
        plugin.async().execute(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    ok = crypto.verify(password, record.password).ok();
                } catch (Throwable throwable) {
                    plugin.getLogger().log(Level.WARNING, "Şifre doğrulanamadı.", throwable);
                }
                final boolean valid = ok;
                plugin.runSync(new Runnable() {
                    @Override
                    public void run() {
                        if (!isCurrent(player, session)) {
                            return;
                        }
                        session.processing = false;
                        if (!valid) {
                            chat.msg(player, "email.wrong-password");
                            return;
                        }
                        if (config.gui().confirmChanges && plugin.dialogs().available()) {
                            openChangeDialog(player, session, true, password, newEmail);
                        } else {
                            applyNewEmail(player, session, password, newEmail, false);
                        }
                    }
                });
            }
        });
    }

    private void applyNewEmail(final Player player, final AuthSession session,
                               String rawCurrent, String rawEmail, final boolean verifyCurrent) {
        if (rawEmail == null || rawEmail.trim().isEmpty()) {
            chat.msg(player, "email.usage");
            return;
        }
        final String newEmail = rawEmail.trim();
        if (!EmailValidator.isValid(newEmail)) {
            chat.msg(player, "email.invalid");
            return;
        }
        if (session.processing) {
            chat.msg(player, "login.processing");
            return;
        }
        final AuthRecord record = session.record;
        if (record == null) {
            chat.msg(player, "common.internal-error");
            return;
        }
        final String currentPassword = rawCurrent == null ? "" : rawCurrent;
        session.processing = true;
        plugin.async().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (verifyCurrent && !crypto.verify(currentPassword, record.password).ok()) {
                        plugin.runSync(new Runnable() {
                            @Override
                            public void run() {
                                if (!isCurrent(player, session)) {
                                    return;
                                }
                                session.processing = false;
                                chat.msg(player, "email.wrong-password");
                            }
                        });
                        return;
                    }
                    String lookup = crypto.emailLookup(newEmail);
                    if (lookup.equals(record.emailLookup)) {
                        plugin.runSync(new Runnable() {
                            @Override
                            public void run() {
                                if (!isCurrent(player, session)) {
                                    return;
                                }
                                session.processing = false;
                                chat.msg(player, "email.same");
                            }
                        });
                        return;
                    }
                    if (storage.emailExists(lookup)) {
                        plugin.runSync(new Runnable() {
                            @Override
                            public void run() {
                                if (!isCurrent(player, session)) {
                                    return;
                                }
                                session.processing = false;
                                chat.msg(player, "email.taken");
                            }
                        });
                        return;
                    }
                    final String stored = config.security().encryptEmail
                            ? crypto.encryptEmail(newEmail) : "p:" + newEmail;
                    storage.updateEmail(record.uuid, stored, lookup, System.currentTimeMillis());
                    plugin.runSync(new Runnable() {
                        @Override
                        public void run() {
                            if (!isCurrent(player, session)) {
                                return;
                            }
                            session.processing = false;
                            record.email = newEmail;
                            record.emailStored = stored;
                            record.emailLookup = lookup;
                            cache.put(record);
                            chat.msg(player, "email.changed");
                            audit(player, "EMAIL_CHANGE", "-");
                        }
                    });
                } catch (Throwable throwable) {
                    plugin.getLogger().log(Level.SEVERE, "E-posta değiştirilemedi.", throwable);
                    plugin.runSync(new Runnable() {
                        @Override
                        public void run() {
                            if (isCurrent(player, session)) {
                                session.processing = false;
                                chat.msg(player, "common.internal-error");
                            }
                        }
                    });
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Helpers used by listeners, commands and the public API
    // ------------------------------------------------------------------

    public void handleQuit(Player player) {
        AuthSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            cancelTasks(session);
        }
    }

    public void shutdown() {
        for (AuthSession session : sessions.values()) {
            cancelTasks(session);
        }
        sessions.clear();
    }

    public boolean isAuthenticated(Player player) {
        if (player == null) {
            return false;
        }
        AuthSession session = sessions.get(player.getUniqueId());
        return session != null && session.isAuthenticated();
    }

    public boolean isAuthenticated(UUID uuid) {
        AuthSession session = sessions.get(uuid);
        return session != null && session.isAuthenticated();
    }

    public AuthSession session(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    public int authenticatedCount() {
        int count = 0;
        for (AuthSession session : sessions.values()) {
            if (session.isAuthenticated()) {
                count++;
            }
        }
        return count;
    }

    public int pendingCount() {
        int count = 0;
        for (AuthSession session : sessions.values()) {
            if (!session.isAuthenticated()) {
                count++;
            }
        }
        return count;
    }

    public String email(Player player) {
        AuthSession session = sessions.get(player.getUniqueId());
        if (session == null || session.record == null) {
            return "";
        }
        return session.record.email == null ? "" : session.record.email;
    }

    public void forceLogin(final Player player) {
        AuthSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.isAuthenticated()) {
            return;
        }
        completeLogin(player, session, LoginMode.PASSWORD);
    }

    public void forceLogout(Player player) {
        logout(player);
    }

    public void setPolicy(PasswordPolicy policy) {
        this.policy = policy;
    }

    public void setCrypto(CryptoEngine crypto) {
        if (crypto != null) {
            this.crypto = crypto;
        }
    }

    public RateLimiter rateLimiter() {
        return rateLimiter;
    }

    private boolean isCurrent(Player player, AuthSession session) {
        return player != null && player.isOnline()
                && sessions.get(player.getUniqueId()) == session;
    }

    private void refreshVisibility(Player player) {
        try {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(player)) {
                    continue;
                }
                if (isAuthenticated(player)) {
                    player.showPlayer(other);
                } else if (config.auth().hidePlayers) {
                    player.hidePlayer(other);
                }
                if (isAuthenticated(other)) {
                    other.showPlayer(player);
                } else if (config.auth().hidePlayers) {
                    other.hidePlayer(player);
                }
            }
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.FINE, "Oyuncu görünürlüğü güncellenemedi.", throwable);
        }
    }

    private void reopenLater(final Player player, final AuthSession session, final boolean register) {
        if (!plugin.dialogs().available() || !config.gui().autoOpen) {
            return;
        }
        if (session.dialogTask != null) {
            try {
                session.dialogTask.cancel();
            } catch (Throwable ignored) {
            }
        }
        int delay = Math.max(1, config.gui().openDelayTicks);
        session.dialogTask = Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!isCurrent(player, session) || session.processing) {
                    return;
                }
                if (register && session.state == AuthState.REGISTER) {
                    openAuthScreen(player);
                } else if (!register && session.state == AuthState.LOGIN) {
                    openAuthScreen(player);
                }
            }
        }, delay);
    }

    private void runCommands(Player player, List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (String command : commands) {
            if (command == null || command.trim().isEmpty()) {
                continue;
            }
            String resolved = command.replace("{player}", player.getName()).trim();
            while (resolved.startsWith("/")) {
                resolved = resolved.substring(1);
            }
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.WARNING, "Komut çalıştırılamadı: " + resolved, throwable);
            }
        }
    }

    private void audit(final Player player, final String action, final String detail) {
        final UUID uuid = player.getUniqueId();
        final String name = player.getName();
        final String address = ip(player);
        plugin.async().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    storage.audit(uuid, name, action, address, detail);
                } catch (Throwable throwable) {
                    plugin.getLogger().log(Level.FINE, "Audit kaydı yazılamadı.", throwable);
                }
            }
        });
    }

    private static String ip(Player player) {
        try {
            if (player.getAddress() == null || player.getAddress().getAddress() == null) {
                return null;
            }
            return player.getAddress().getAddress().getHostAddress();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
