package com.wairesd.discordbotmanager.velocity.discord;

import com.google.gson.Gson;
import com.velocitypowered.api.proxy.ProxyServer;
import com.wairesd.discordbotmanager.velocity.DiscordBotManagerVelocity;
import com.wairesd.discordbotmanager.velocity.config.Messages;
import com.wairesd.discordbotmanager.velocity.config.Settings;
import com.wairesd.discordbotmanager.velocity.model.RequestMessage;
import com.wairesd.discordbotmanager.velocity.network.NettyServer;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// Listens for Discord slash command interactions and forwards them to the Netty server.
public class DiscordBotListener extends ListenerAdapter {
    private final DiscordBotManagerVelocity plugin;
    private final NettyServer nettyServer;
    private final Logger logger;
    private final ProxyServer proxy;
    private final Gson gson = new Gson();
    private final ConcurrentHashMap<UUID, SlashCommandInteractionEvent> pendingRequests = new ConcurrentHashMap<>();

    public DiscordBotListener(DiscordBotManagerVelocity plugin, NettyServer nettyServer, Logger logger) {
        this.plugin = plugin;
        this.nettyServer = nettyServer;
        this.logger = logger;
        this.proxy = plugin.getProxy();
    }

    public ConcurrentHashMap<UUID, SlashCommandInteractionEvent> getPendingRequests() {
        return pendingRequests;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String command = event.getName();
        var channel = nettyServer.getCommandToChannel().get(command);

        if (channel == null || !channel.isActive()) {
            event.reply(Messages.getMessage("command-unavailable")).setEphemeral(true).queue();
            return;
        }

        UUID requestId = UUID.randomUUID();
        pendingRequests.put(requestId, event);
        event.deferReply().queue();

        Map<String, String> options = new HashMap<>();
        event.getOptions().forEach(opt -> options.put(opt.getName(), opt.getAsString()));

        RequestMessage request = new RequestMessage("request", command, options, requestId.toString());
        String message = gson.toJson(request);
        nettyServer.sendMessage(channel, message);

        if (Settings.isDebug()) logger.debug("Sent request: {}", message);

        proxy.getScheduler().buildTask(plugin, () -> {
            if (pendingRequests.remove(requestId) != null) {
                event.getHook().sendMessage("Response timeout.").queue();
                if (Settings.isDebug()) logger.warn("Request timeout: {}", requestId);
            }
        }).delay(10, TimeUnit.SECONDS).schedule();
    }
}