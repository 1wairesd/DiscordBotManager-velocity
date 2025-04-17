package com.wairesd.discordbotmanager.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.wairesd.discordbotmanager.velocity.DiscordBotManagerVelocity;
import com.wairesd.discordbotmanager.velocity.config.Messages;
import com.wairesd.discordbotmanager.velocity.config.Settings;
import com.wairesd.discordbotmanager.velocity.util.Color;

import java.util.stream.Collectors;

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

        if (args.length == 0) {
            source.sendMessage(Color.parse(Messages.getMessage("usage-admin-command")));
            return;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!source.hasPermission("discordbotmanager.reload")) {
                    source.sendMessage(Color.parse(Messages.getMessage("no-permission")));
                    return;
                }
                Settings.reload();
                plugin.updateActivity();
                source.sendMessage(Color.parse(Messages.getMessage("reload-success")));
                break;
            case "commands":
                if (!source.hasPermission("discordbotmanager.commands")) {
                    source.sendMessage(Color.parse(Messages.getMessage("no-permission")));
                    return;
                }
                var commandToServers = plugin.getNettyServer().getCommandToServers();
                if (commandToServers.isEmpty()) {
                    source.sendMessage(Color.parse("No registered commands."));
                    return;
                }
                for (var entry : commandToServers.entrySet()) {
                    String command = entry.getKey();
                    String serverList = entry.getValue().stream()
                            .map(server -> server.serverName())
                            .collect(Collectors.joining(", "));
                    source.sendMessage(Color.parse("&e" + command + ": &f" + serverList));
                }
                break;
            default:
                source.sendMessage(Color.parse(Messages.getMessage("usage-admin-command")));
        }
    }
}