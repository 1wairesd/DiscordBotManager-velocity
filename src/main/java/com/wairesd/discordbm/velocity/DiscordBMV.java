package com.wairesd.discordbm.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.wairesd.discordbm.velocity.command.AdminCommand;
import com.wairesd.discordbm.velocity.config.Messages;
import com.wairesd.discordbm.velocity.config.Settings;
import com.wairesd.discordbm.velocity.database.DatabaseManager;
import com.wairesd.discordbm.velocity.discord.DiscordBotListener;
import com.wairesd.discordbm.velocity.discord.ResponseHandler;
import com.wairesd.discordbm.velocity.network.NettyServer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.EnumSet;

@Plugin(id = "discordbmv", name = "DiscordBMV", version = "1.0", authors = {"wairesd"})
public class DiscordBMV {
    private final Logger logger;
    private final Path dataDirectory;
    private final ProxyServer proxy;
    private JDA jda;
    private NettyServer nettyServer;
    private DiscordBotListener discordBotListener;
    private DatabaseManager dbManager;

    @Inject
    public DiscordBMV(Logger logger, @DataDirectory Path dataDirectory, ProxyServer proxy) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.proxy = proxy;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        Settings.init(dataDirectory);
        Messages.init(dataDirectory);

        String dbPath = "jdbc:sqlite:" + dataDirectory.resolve("DiscordBMV.db").toString();
        dbManager = new DatabaseManager(dbPath);

        nettyServer = new NettyServer(logger, dbManager);
        new Thread(nettyServer::start, "Netty-Server-Thread").start();

        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("discordBMV").build(),
                new AdminCommand(this)
        );

        String token = Settings.getBotToken();
        if (token == null || token.isEmpty()) {
            logger.error("Bot token is not specified in settings.yml!");
            return;
        }
        try {
            discordBotListener = new DiscordBotListener(this, nettyServer, logger);
            ResponseHandler.init(discordBotListener, logger);

            Activity activity = createActivity();
            jda = JDABuilder.createDefault(token)
                    .enableIntents(EnumSet.of(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.DIRECT_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT
                    ))
                    .setActivity(activity)
                    .addEventListeners(discordBotListener)
                    .build()
                    .awaitReady();

            nettyServer.setJda(jda);
            logger.info("Discord bot successfully started.");
        } catch (Exception e) {
            logger.error("Error initializing JDA: {}", e.getMessage(), e);
        }
    }

    private Activity createActivity() {
        String activityType = Settings.getActivityType().toLowerCase();
        String activityMessage = Settings.getActivityMessage();
        switch (activityType) {
            case "playing": return Activity.playing(activityMessage);
            case "watching": return Activity.watching(activityMessage);
            case "listening": return Activity.listening(activityMessage);
            default: return Activity.playing(activityMessage);
        }
    }

    public void updateActivity() {
        if (jda != null) {
            jda.getPresence().setActivity(createActivity());
            logger.info("Bot activity updated to: {} {}", Settings.getActivityType(), Settings.getActivityMessage());
        }
    }

    public ProxyServer getProxy() { return proxy; }
    public NettyServer getNettyServer() { return nettyServer; }
}