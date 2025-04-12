package com.wairesd.discordbotmanager.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.wairesd.discordbotmanager.velocity.config.Messages;
import com.wairesd.discordbotmanager.velocity.config.Settings;
import com.wairesd.discordbotmanager.velocity.util.Color;

// This class handles the /discordbotmanager command on Velocity, allowing admins to reload settings.
public class AdminCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        // Check if the command syntax is correct
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            source.sendMessage(Color.parse(Messages.getMessage("usage-admin-command")));
            return;
        }

        // Check permission
        if (!source.hasPermission("discordbotmanager.reload")) {
            source.sendMessage(Color.parse(Messages.getMessage("no-permission")));
            return;
        }

        // Reload settings and notify the user
        Settings.reload();
        source.sendMessage(Color.parse(Messages.getMessage("reload-success")));
    }
}