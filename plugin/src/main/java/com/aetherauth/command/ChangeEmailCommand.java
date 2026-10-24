package com.aetherauth.command;

import java.util.Collections;
import java.util.List;

import com.aetherauth.AetherAuthPlugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class ChangeEmailCommand implements TabExecutor {

    private final AetherAuthPlugin plugin;

    public ChangeEmailCommand(AetherAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.chat().msg(sender, "common.player-only");
            return true;
        }
        if (args.length < 2) {
            plugin.chat().msg(sender, "email.usage");
            return true;
        }
        plugin.auth().changeEmail((Player) sender, args[0], args[1]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
