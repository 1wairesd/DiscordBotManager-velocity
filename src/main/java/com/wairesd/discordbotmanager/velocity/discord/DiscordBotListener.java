package com.wairesd.discordbotmanager.velocity.discord;

import com.google.gson.Gson;
import com.velocitypowered.api.proxy.ProxyServer;
import com.wairesd.discordbotmanager.velocity.DiscordBotManagerVelocity;
import com.wairesd.discordbotmanager.velocity.config.Messages;
import com.wairesd.discordbotmanager.velocity.config.Settings;
import com.wairesd.discordbotmanager.velocity.model.RequestMessage;
import com.wairesd.discordbotmanager.velocity.websocket.VelocityWebSocketServer;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// This class listens for Discord slash commands and forwards them to the WebSocket server.
public class DiscordBotListener extends ListenerAdapter {
    private final DiscordBotManagerVelocity plugin;
    private final VelocityWebSocketServer wsServer;
    private final Logger logger;
    private final ProxyServer proxy;
    private final Gson gson = new Gson();
    private final ConcurrentHashMap<UUID, SlashCommandInteractionEvent> pendingRequests = new ConcurrentHashMap<>();

    public DiscordBotListener(DiscordBotManagerVelocity plugin, VelocityWebSocketServer wsServer, Logger logger) {
        this.plugin = plugin;
        this.wsServer = wsServer;
        this.logger = logger;
        this.proxy = plugin.getProxy();
    }

    public ConcurrentHashMap<UUID, SlashCommandInteractionEvent> getPendingRequests() {
        return pendingRequests;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String command = event.getName();
        var conn = wsServer.getCommandToConnection().get(command);

        // Check if the WebSocket connection is available
        if (conn == null || !conn.isOpen()) {
            event.reply(Messages.getMessage("command-unavailable")).setEphemeral(true).queue();
            return;
        }

        UUID requestId = UUID.randomUUID();
        pendingRequests.put(requestId, event);
        event.deferReply().queue();

        // Collect command options
        Map<String, String> options = new HashMap<>();
        for (var opt : event.getOptions()) {
            options.put(opt.getName(), opt.getAsString());
        }

        // Send request to WebSocket server
        RequestMessage request = new RequestMessage("request", command, options, requestId.toString());
        String message = gson.toJson(request);
        wsServer.sendRequest(conn, message);

        if (Settings.isDebug()) {
            logger.debug("Sent request: {}", message);
        }

        // Schedule a timeout for the request
        proxy.getScheduler().buildTask(plugin, () -> {
            if (pendingRequests.remove(requestId) != null) {
                event.getHook().sendMessage("Response timeout.").queue();
                if (Settings.isDebug()) {
                    logger.warn("Request timeout: {}", requestId);
                }
            }
        }).delay(10, TimeUnit.SECONDS).schedule();
    }
}