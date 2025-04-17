package com.wairesd.discordbotmanager.velocity.handle;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.wairesd.discordbotmanager.velocity.config.Settings;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;

/**
 * Handles plugin messages received from Bukkit plugins via Velocity.
 */
public class DiscordPluginMessageHandler {
    private final Logger logger;

    public DiscordPluginMessageHandler(Logger logger) {
        this.logger = logger;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if ("discord:message".equals(event.getIdentifier().getId())) {
            String message = new String(event.getData(), StandardCharsets.UTF_8);
            if (Settings.isDebugPluginConnections()) {
                logger.info("Received message from Bukkit plugin: {}", message);
            }
            event.setResult(PluginMessageEvent.ForwardResult.handled());
        }
    }
}