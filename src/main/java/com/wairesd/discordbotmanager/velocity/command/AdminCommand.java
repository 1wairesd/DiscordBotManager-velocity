package com.wairesd.discordbotmanager.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.wairesd.discordbotmanager.velocity.DiscordBotManagerVelocity;
import com.wairesd.discordbotmanager.velocity.config.Messages;
import com.wairesd.discordbotmanager.velocity.config.Settings;
import com.wairesd.discordbotmanager.velocity.util.Color;

/**
 * Handles the /discordbotmanager command on Velocity for reloading settings.
 */
public class AdminCommand implements SimpleCommand {
    private final DiscordBotManagerVelocity plugin;

    public AdminCommand(DiscordBotManagerVelocity plugin) {
        this.plugin = plugin;
    }

    /**
     * Executes the reload command if permissions are met.
     * @param invocation the command invocation
     */
    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!source.hasPermission("discordbotmanager.reload")) {
                source.sendMessage(Color.parse(Messages.getMessage("no-permission")));
                return;
            }
            Settings.reload();
            plugin.updateActivity();
            source.sendMessage(Color.parse(Messages.getMessage("reload-success")));
        } else {
            source.sendMessage(Color.parse(Messages.getMessage("usage-admin-command")));
        }
    }
}