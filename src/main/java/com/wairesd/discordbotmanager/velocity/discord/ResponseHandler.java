package com.wairesd.discordbotmanager.velocity.discord;

import com.wairesd.discordbotmanager.velocity.config.Settings;
import net.dv8tion.jda.api.EmbedBuilder;
import org.slf4j.Logger;

import java.awt.Color;
import java.util.UUID;

/**
 * Handles responses from the Netty server and updates Discord interactions.
 */
public class ResponseHandler {
    private static DiscordBotListener listener;
    private static Logger logger;

    public static void init(DiscordBotListener discordBotListener, Logger log) {
        listener = discordBotListener;
        logger = log;
    }

    public static void handleResponse(String requestIdStr, String response) {
        try {
            UUID requestId = UUID.fromString(requestIdStr);
            var event = listener.getPendingRequests().remove(requestId);
            if (event == null) {
                if (Settings.isDebugErrors()) {
                    logger.warn("Request with ID {} not found.", requestIdStr);
                }
                return;
            }
            if (Settings.isDebugClientResponses()) {
                logger.info("Received response for request {}: {}", requestIdStr, response);
            }
            var embed = new EmbedBuilder()
                    .setColor(Color.GREEN)
                    .setDescription(response)
                    .build();
            event.getHook().sendMessageEmbeds(embed).queue();
        } catch (IllegalArgumentException e) {
            if (Settings.isDebugErrors()) {
                logger.error("Invalid UUID in response: {}", requestIdStr, e);
            }
        }
    }
}