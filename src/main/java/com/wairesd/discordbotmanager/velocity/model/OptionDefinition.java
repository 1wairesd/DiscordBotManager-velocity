package com.wairesd.discordbotmanager.velocity.model;

// Represents an option for a command.
public record OptionDefinition(String name, String type, String description, boolean required) {}