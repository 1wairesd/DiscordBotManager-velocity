package com.wairesd.discordbotmanager.velocity.discord;

import com.google.gson.Gson;
import com.wairesd.discordbotmanager.velocity.DiscordBotManagerVelocity;
import com.wairesd.discordbotmanager.velocity.config.Settings;
import com.wairesd.discordbotmanager.velocity.model.CommandDefinition;
import com.wairesd.discordbotmanager.velocity.model.RequestMessage;
import com.wairesd.discordbotmanager.velocity.network.NettyServer;
import io.netty.channel.Channel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Listens for Discord slash command interactions and forwards them to the Netty server.
public class DiscordBotListener extends ListenerAdapter {
    private final NettyServer nettyServer;
    private final Gson gson = new Gson();
    private final ConcurrentHashMap<UUID, SlashCommandInteractionEvent> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, SelectionInfo> pendingSelections = new ConcurrentHashMap<>();
    private final Logger logger;

    public DiscordBotListener(DiscordBotManagerVelocity plugin, NettyServer nettyServer, Logger logger) {
        this.nettyServer = nettyServer;
        this.logger = logger;
    }

    public ConcurrentHashMap<UUID, SlashCommandInteractionEvent> getPendingRequests() { return pendingRequests; }
    public Map<String, SelectionInfo> getPendingSelections() { return pendingSelections; }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String command = event.getName();
        List<NettyServer.ServerInfo> servers = nettyServer.getServersForCommand(command);

        if (servers.isEmpty()) {
            event.reply("Command unavailable.").setEphemeral(true).queue();
            return;
        }

        CommandDefinition cmdDef = nettyServer.getCommandDefinitions().get(command);
        if ("dm".equals(cmdDef.context()) && event.getGuild() != null) {
            event.reply("This command is only available in direct messages.").setEphemeral(true).queue();
            return;
        }

        if (servers.size() == 1) {
            Channel channel = servers.get(0).channel();
            UUID requestId = UUID.randomUUID();
            pendingRequests.put(requestId, event);
            event.deferReply().queue();

            Map<String, String> options = new HashMap<>();
            event.getOptions().forEach(opt -> options.put(opt.getName(), opt.getAsString()));

            RequestMessage request = new RequestMessage("request", command, options, requestId.toString());
            String message = gson.toJson(request);
            if (Settings.isDebugClientResponses()) {
                logger.info("Sending request to server: {}", message);
            }
            nettyServer.sendMessage(channel, message);
        } else {
            String selectMenuId = "select_server_" + UUID.randomUUID().toString();
            pendingSelections.put(selectMenuId, new SelectionInfo(event, servers));

            StringSelectMenu menu = StringSelectMenu.create(selectMenuId)
                    .setPlaceholder("Select a server")
                    .setRequiredRange(1, 1)
                    .addOptions(servers.stream()
                            .map(server -> SelectOption.of(server.serverName(), server.serverName()))
                            .toList())
                    .build();

            event.reply("Command registered on multiple servers. Select one:")
                    .addActionRow(menu)
                    .setEphemeral(true)
                    .queue();
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String customId = event.getComponentId();
        if (customId.startsWith("select_server_")) {
            SelectionInfo selectionInfo = pendingSelections.remove(customId);
            if (selectionInfo == null) {
                event.reply("Selection timeout expired.").setEphemeral(true).queue();
                return;
            }

            List<String> selectedValues = event.getValues();
            if (selectedValues.isEmpty()) {
                event.reply("No server selected.").setEphemeral(true).queue();
                return;
            }

            String chosenServerName = selectedValues.get(0);
            NettyServer.ServerInfo targetServer = selectionInfo.servers.stream()
                    .filter(server -> server.serverName().equals(chosenServerName))
                    .findFirst()
                    .orElse(null);
            if (targetServer == null) {
                event.reply("Selected server not found.").setEphemeral(true).queue();
                return;
            }

            UUID requestId = UUID.randomUUID();
            pendingRequests.put(requestId, selectionInfo.event);
            event.deferEdit().queue();

            Map<String, String> options = new HashMap<>();
            selectionInfo.event.getOptions().forEach(opt -> options.put(opt.getName(), opt.getAsString()));

            RequestMessage request = new RequestMessage("request", selectionInfo.event.getName(), options, requestId.toString());
            String json = gson.toJson(request);
            if (Settings.isDebugClientResponses()) {
                logger.info("Sending request to selected server {}: {}", chosenServerName, json);
            }
            nettyServer.sendMessage(targetServer.channel(), json);
        }
    }

    public static record SelectionInfo(SlashCommandInteractionEvent event, List<NettyServer.ServerInfo> servers) {}
}