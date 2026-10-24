package com.aetherauth.listener;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.aetherauth.AetherAuthPlugin;
import com.aetherauth.auth.AuthManager;
import com.aetherauth.config.ConfigManager;
import com.aetherauth.platform.Chat;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Locks unauthenticated players down without relying on NMS: movement, chat,
 * commands, damage, interactions, pickups, portals and the inventory are all
 * blocked through stable Bukkit events.
 */
public final class AuthListener implements Listener {

    private final AetherAuthPlugin plugin;
    private final AuthManager auth;
    private final Chat chat;
    private final ConfigManager config;
    private final Map<String, Long> notices = new ConcurrentHashMap<String, Long>();

    public AuthListener(AetherAuthPlugin plugin) {
        this.plugin = plugin;
        this.auth = plugin.auth();
        this.chat = plugin.chat();
        this.config = plugin.config();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (config.auth().hideJoinMessage) {
            event.setJoinMessage(null);
        }
        try {
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
        } catch (Throwable ignored) {
        }
        auth.handleJoin(player);
        if (plugin.updateChecker() != null) {
            plugin.updateChecker().onJoin(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (config.auth().hideJoinMessage && !auth.isAuthenticated(player)) {
            event.setQuitMessage(null);
        }
        notices.keySet().removeIf(key -> key.startsWith(player.getUniqueId().toString()));
        if (plugin.updateChecker() != null) {
            plugin.updateChecker().onQuit(player);
        }
        auth.handleQuit(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (auth.isAuthenticated(player) || !config.auth().freeze) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }
        Location fixed = from.clone();
        fixed.setYaw(to.getYaw());
        fixed.setPitch(to.getPitch());
        event.setTo(fixed);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("deprecation")
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (auth.isAuthenticated(player) || !config.auth().blockChat) {
            return;
        }
        event.setCancelled(true);
        notice(player, "chat.blocked");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (auth.isAuthenticated(player)) {
            return;
        }
        String message = event.getMessage();
        if (message == null || message.isEmpty()) {
            return;
        }
        String command = message;
        while (command.startsWith("/")) {
            command = command.substring(1);
        }
        int space = command.indexOf(' ');
        if (space >= 0) {
            command = command.substring(0, space);
        }
        int colon = command.indexOf(':');
        if (colon > 0) {
            command = command.substring(colon + 1);
        }
        command = command.toLowerCase(Locale.ROOT);
        if (config.auth().allowedCommands.contains(command)) {
            return;
        }
        event.setCancelled(true);
        notice(player, "command.blocked");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onPickup(PlayerPickupItemEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player && locked((Player) event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player && locked((Player) event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player && locked((Player) event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && locked((Player) event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && locked((Player) event.getDamager())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBed(PlayerBedEnterEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        if (auth.isAuthenticated(event.getPlayer()) || !config.auth().teleportToAuthSpawn) {
            return;
        }
        final Player player = event.getPlayer();
        final Location target = authSpawn();
        if (target != null) {
            plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    if (player.isOnline() && !auth.isAuthenticated(player)) {
                        player.teleport(target);
                    }
                }
            });
        }
    }

    private Location authSpawn() {
        com.aetherauth.config.ConfigData.Spawn spawn = config.auth().authSpawn;
        org.bukkit.World world = plugin.getServer().getWorld(spawn.world);
        return world == null ? null : new Location(world, spawn.x, spawn.y, spawn.z, spawn.yaw, spawn.pitch);
    }

    private boolean locked(Player player) {
        return !auth.isAuthenticated(player);
    }

    private void notice(final Player player, final String key) {
        final String noticeKey = player.getUniqueId().toString() + "|" + key;
        long now = System.currentTimeMillis();
        Long previous = notices.get(noticeKey);
        if (previous != null && now - previous < 1500L) {
            return;
        }
        notices.put(noticeKey, now);
        plugin.runSync(new Runnable() {
            @Override
            public void run() {
                if (player.isOnline() && !auth.isAuthenticated(player)) {
                    chat.msg(player, key);
                }
            }
        });
    }
}
