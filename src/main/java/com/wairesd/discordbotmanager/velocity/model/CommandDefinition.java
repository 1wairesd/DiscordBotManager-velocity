package com.wairesd.discordbotmanager.velocity.model;

import java.util.List;

// Represents a command definition with name, description, and options.
public record CommandDefinition(String name, String description, String context, List<OptionDefinition> options) {}