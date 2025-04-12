package com.wairesd.discordbotmanager.velocity.config;

import com.wairesd.discordbotmanager.velocity.secret.SecretManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

// This class loads and provides access to configuration settings from settings.yml.
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

    // Check if debug mode is enabled
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

    // Get the name of the secret file from settings.yml, defaulting to "secret.complete.code"
    public static String getForwardingSecretFile() {
        return config != null ? (String) config.getOrDefault("forwarding-secret-file", "secret.complete.code") : "secret.complete.code";
    }

    // Get the secret code from SecretManager
    public static String getSecretCode() {
        return secretManager != null ? secretManager.getSecretCode() : null;
    }
}