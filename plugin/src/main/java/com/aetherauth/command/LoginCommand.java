package com.aetherauth.command;

import java.util.Collections;
import java.util.List;

import com.aetherauth.AetherAuthPlugin;
import com.aetherauth.auth.AuthSession;
import com.aetherauth.auth.AuthState;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * /login [password] — without arguments it opens the Paper dialog (1.21.6+)
 * or prints the command usage on older servers.
 */
public final class LoginCommand implements TabExecutor {

    private final AetherAuthPlugin plugin;

    public LoginCommand(AetherAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.chat().msg(sender, "common.player-only");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            AuthSession session = plugin.auth().session(player);
            if (session != null && session.state == AuthState.REGISTER) {
                plugin.chat().msg(player, "login.unknown");
                plugin.chat().msg(player, "join.usage.register");
                return true;
            }
            plugin.auth().openAuthScreen(player);
            return true;
        }
        plugin.auth().login(player, join(args, 0));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }

    static String join(String[] args, int from) {
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }
}
