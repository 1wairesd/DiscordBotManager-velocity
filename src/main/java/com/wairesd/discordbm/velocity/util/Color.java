package com.wairesd.discordbm.velocity.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

// Utility class for parsing color codes in messages using Velocity's Adventure API.
public class Color {
    private static final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    public static Component parse(String message) {
        return legacySerializer.deserialize(message);
    }
}