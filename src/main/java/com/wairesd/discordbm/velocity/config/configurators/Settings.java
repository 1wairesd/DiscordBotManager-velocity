package com.wairesd.discordbm.velocity.config.configurators;

import com.wairesd.discordbm.velocity.util.SecretManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Manages loading and retrieving settings from settings.yml for Velocity.
 */
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
        CompletableFuture.runAsync(() -> {
            try {
                Path configPath = dataDirectory.resolve("settings.yml");
                if (!Files.exists(configPath)) {
                    Files.createDirectories(dataDirectory);
                    try (InputStream in = Settings.class.getClassLoader().getResourceAsStream("settings.yml")) {
                        if (in != null) Files.copy(in, configPath);
                        else logger.error("settings.yml not found in resources!");
                    }
                }
                config = new Yaml().load(Files.newInputStream(configPath));
                validateConfig();
                logger.info("Settings loaded from settings.yml");
            } catch (Exception e) {
                logger.error("Error loading settings.yml: {}", e.getMessage(), e);
            }
        });
    }

    public static void reload() {
        load();
        secretManager = new SecretManager(dataDirectory, getForwardingSecretFile());
        Messages.reload();
    }

    private static void validateConfig() {
        if (config == null || !config.containsKey("Discord") || getBotToken() == null) {
            logger.warn("Bot-token missing in settings.yml, using default behavior");
        }
    }

    // Debug options
    public static boolean isDebugConnections() {
        Map<String, Object> debug = config != null ? (Map<String, Object>) config.get("debug") : null;
        return debug != null && (boolean) debug.getOrDefault("debug-connections", true);
    }

    public static boolean isDebugClientResponses() {
        Map<String, Object> debug = config != null ? (Map<String, Object>) config.get("debug") : null;
        return debug != null && (boolean) debug.getOrDefault("debug-client-responses", false);
    }

    public static boolean isDebugPluginConnections() {
        Map<String, Object> debug = config != null ? (Map<String, Object>) config.get("debug") : null;
        return debug != null && (boolean) debug.getOrDefault("debug-plugin-connections", false);
    }

    public static boolean isDebugCommandRegistrations() {
        Map<String, Object> debug = config != null ? (Map<String, Object>) config.get("debug") : null;
        return debug != null && (boolean) debug.getOrDefault("debug-command-registrations", false);
    }

    public static boolean isDebugAuthentication() {
        Map<String, Object> debug = config != null ? (Map<String, Object>) config.get("debug") : null;
        return debug != null && (boolean) debug.getOrDefault("debug-authentication", true);
    }

    public static boolean isDebugErrors() {
        Map<String, Object> debug = config != null ? (Map<String, Object>) config.get("debug") : null;
        return debug != null && (boolean) debug.getOrDefault("debug-errors", true);
    }

    // Configuration getters
    public static String getBotToken() {
        Map<String, Object> discord = config != null ? (Map<String, Object>) config.get("Discord") : null;
        return discord != null ? (String) discord.get("Bot-token") : null;
    }

    public static int getNettyPort() {
        Map<String, Object> netty = config != null ? (Map<String, Object>) config.get("netty") : null;
        return netty != null ? (int) netty.get("port") : 0;
    }

    public static String getForwardingSecretFile() {
        return config != null ? (String) config.getOrDefault("forwarding-secret-file", "secret.complete.code") : "secret.complete.code";
    }

    public static String getSecretCode() { return secretManager != null ? secretManager.getSecretCode() : null; }

    public static String getActivityType() {
        Map<String, Object> discord = config != null ? (Map<String, Object>) config.get("Discord") : null;
        Map<String, String> activity = discord != null ? (Map<String, String>) discord.get("activity") : null;
        return activity != null ? activity.getOrDefault("type", "playing") : "playing";
    }

    public static String getActivityMessage() {
        Map<String, Object> discord = config != null ? (Map<String, Object>) config.get("Discord") : null;
        Map<String, String> activity = discord != null ? (Map<String, String>) discord.get("activity") : null;
        return activity != null ? activity.getOrDefault("message", "Velocity Server") : "Velocity Server";
    }

    public static boolean isViewConnectedBannedIp() {
        return config != null && (boolean) config.getOrDefault("view_connected_banned_ip", false);
    }
}