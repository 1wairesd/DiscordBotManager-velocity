package com.wairesd.discordbm.velocity.config.configurators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Manages loading and retrieving messages from messages.yml for Velocity.
 */
public class Messages {
    private static final Logger logger = LoggerFactory.getLogger(Messages.class);
    private static Path dataDirectory;
    private static Map<String, String> messages;

    /**
     * Initializes the data directory and loads messages asynchronously.
     * @param dataDir the plugin data directory
     */
    public static void init(Path dataDir) {
        dataDirectory = dataDir;
        load();
    }

    /**
     * Loads messages.yml asynchronously, creating it from resources if absent.
     */
    public static void load() {
        CompletableFuture.runAsync(() -> {
            try {
                Path messagesPath = dataDirectory.resolve("messages.yml");
                if (!Files.exists(messagesPath)) {
                    Files.createDirectories(dataDirectory);
                    try (InputStream in = Messages.class.getClassLoader().getResourceAsStream("messages.yml")) {
                        if (in != null) {
                            Files.copy(in, messagesPath);
                        } else {
                            logger.error("messages.yml not found in resources!");
                            return;
                        }
                    }
                }
                messages = new Yaml().load(Files.newInputStream(messagesPath));
                logger.info("messages.yml loaded successfully");
            } catch (Exception e) {
                logger.error("Error loading messages.yml: {}", e.getMessage(), e);
            }
        });
    }

    /** Reloads messages by re-loading the file asynchronously. */
    public static void reload() {
        load();
    }

    /**
     * Retrieves a message by key with a fallback.
     * @param key the message key
     * @return the message or fallback
     */
    public static String getMessage(String key) {
        return messages != null ? messages.getOrDefault(key, "Message not found.") : "Message not found.";
    }
}