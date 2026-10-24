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
 * /register [email] [password]
 */
public final class RegisterCommand implements TabExecutor {

    private final AetherAuthPlugin plugin;

    public RegisterCommand(AetherAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.chat().msg(sender, "common.player-only");
            return true;
        }
        Player player = (Player) sender;
        AuthSession session = plugin.auth().session(player);
        if (args.length == 0) {
            if (session != null && session.state != AuthState.REGISTER) {
                plugin.chat().msg(player, session.state == AuthState.LOADING
                        || session.state == AuthState.PROCESSING
                        ? "login.processing" : "register.already");
                return true;
            }
            plugin.auth().openAuthScreen(player);
            return true;
        }

        String email = "";
        String password;
        if (plugin.config().auth().requireEmail) {
            if (args.length < 2) {
                plugin.chat().msg(player, "register.usage");
                return true;
            }
            email = args[0];
            password = LoginCommand.join(args, 1);
        } else if (args.length >= 2 && args[0].indexOf('@') > 0) {
            email = args[0];
            password = LoginCommand.join(args, 1);
        } else {
            password = LoginCommand.join(args, 0);
        }
        plugin.auth().register(player, email, password, null);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
