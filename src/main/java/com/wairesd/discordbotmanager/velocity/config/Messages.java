package com.wairesd.discordbotmanager.velocity.config;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

// This class manages loading and retrieving messages from messages.yml for the Velocity plugin.
public class Messages {
    private static Path dataDirectory;
    private static Map<String, String> messages;

    // Initialize the data directory and load messages
    public static void init(Path dataDir) {
        dataDirectory = dataDir;
        load();
    }

    // Load messages.yml, creating it from resources if it doesn't exist
    public static void load() {
        try {
            Path messagesPath = dataDirectory.resolve("messages.yml");
            if (!Files.exists(messagesPath)) {
                Files.createDirectories(dataDirectory);
                try (InputStream in = Messages.class.getClassLoader().getResourceAsStream("messages.yml")) {
                    if (in != null) {
                        Files.copy(in, messagesPath);
                    } else {
                        System.err.println("File messages.yml not found in resources!");
                        return;
                    }
                }
            }
            messages = new Yaml().load(Files.newInputStream(messagesPath));
            System.out.println("File messages.yml successfully loaded.");
        } catch (Exception e) {
            System.err.println("Error loading messages.yml: " + e.getMessage());
        }
    }

    // Reload messages by re-loading the file
    public static void reload() {
        load();
    }

    // Get a message by key, with a fallback if not found
    public static String getMessage(String key) {
        return messages.getOrDefault(key, "Message not found.");
    }
}