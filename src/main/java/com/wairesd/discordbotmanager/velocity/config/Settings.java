package com.wairesd.discordbotmanager.velocity.config;

import com.wairesd.discordbotmanager.velocity.secret.SecretManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class Settings {
    private static final Logger logger = LoggerFactory.getLogger(Settings.class);
    private static Path dataDirectory;
    private static Map<String, Object> config;
    private static SecretManager secretManager;

    public static void init(Path dataDir) {
        dataDirectory = dataDir;
        load();
        secretManager = new SecretManager(dataDirectory, getForwardingSecretFile());
    }

    public static void load() {
        try {
            Path configPath = dataDirectory.resolve("settings.yml");
            if (!Files.exists(configPath)) {
                Files.createDirectories(dataDirectory);
                try (InputStream in = Settings.class.getClassLoader().getResourceAsStream("settings.yml")) {
                    if (in != null) {
                        Files.copy(in, configPath);
                    } else {
                        logger.error("File settings.yml not found in resources!");
                    }
                }
            }
            config = new Yaml().load(Files.newInputStream(configPath));
            logger.info("Settings loaded successfully from settings.yml");
        } catch (Exception e) {
            logger.error("Error loading settings.yml: {}", e.getMessage(), e);
        }
    }

    public static void reload() {
        load();
        secretManager = new SecretManager(dataDirectory, getForwardingSecretFile());
        Messages.reload();
    }

    public static boolean isDebug() {
        return config != null && (boolean) config.getOrDefault("debug", false);
    }

    public static String getBotToken() {
        if (config == null) return null;
        Map<String, Object> discord = (Map<String, Object>) config.get("Discord");
        return discord != null ? (String) discord.get("Bot-token") : null;
    }

    public static int getWebsocketPort() {
        if (config == null) return 0;
        Map<String, Object> websocket = (Map<String, Object>) config.get("websocket");
        return websocket != null ? (int) websocket.get("port") : 0;
    }

    public static String getForwardingSecretFile() {
        return config != null ? (String) config.getOrDefault("forwarding-secret-file", "secret.complete.code") : "secret.complete.code";
    }

    public static String getSecretCode() {
        return secretManager != null ? secretManager.getSecretCode() : null;
    }

    // "Getting the activity type, by default "playing"
    public static String getActivityType() {
        if (config == null) return "playing";
        Map<String, Object> discord = (Map<String, Object>) config.get("Discord");
        if (discord != null) {
            Map<String, String> activity = (Map<String, String>) discord.get("activity");
            return activity != null ? activity.getOrDefault("type", "playing") : "playing";
        }
        return "playing";
    }

    // Getting the activity message, by default "Velocity Server"
    public static String getActivityMessage() {
        if (config == null) return "Velocity Server";
        Map<String, Object> discord = (Map<String, Object>) config.get("Discord");
        if (discord != null) {
            Map<String, String> activity = (Map<String, String>) discord.get("activity");
            return activity != null ? activity.getOrDefault("message", "Velocity Server") : "Velocity Server";
        }
        return "Velocity Server";
    }
}