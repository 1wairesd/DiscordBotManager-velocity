package com.wairesd.discordbotmanager.velocity.model;

import java.util.List;

// Represents a message to register commands.
public record RegisterMessage(String type, String serverName, String pluginName, List<CommandDefinition> commands, String secret) {}