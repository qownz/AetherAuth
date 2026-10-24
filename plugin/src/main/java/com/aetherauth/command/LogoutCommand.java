package com.aetherauth.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.aetherauth.AetherAuthPlugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * /logout [player] — staff only. Regular players cannot drop their own
 * session; an administrator can end someone else's session.
 */
public final class LogoutCommand implements TabExecutor {

    private final AetherAuthPlugin plugin;

    public LogoutCommand(AetherAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aetherauth.admin.logout")) {
            plugin.chat().msg(sender, "logout.no-permission");
            return true;
        }
        if (args.length >= 1) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getName().equalsIgnoreCase(args[0])) {
                        target = online;
                        break;
                    }
                }
            }
            if (target == null) {
                plugin.chat().msg(sender, "logout.target-not-found", "player", args[0]);
                return true;
            }
            plugin.auth().logout(target);
            plugin.chat().msg(sender, "logout.target", "player", target.getName());
            return true;
        }
        if (!(sender instanceof Player)) {
            plugin.chat().msg(sender, "common.player-only");
            return true;
        }
        plugin.auth().logout((Player) sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission("aetherauth.admin.logout")) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<String>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                names.add(player.getName());
            }
        }
        return names;
    }
}
