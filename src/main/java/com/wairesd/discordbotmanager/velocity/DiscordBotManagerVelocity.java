package com.wairesd.discordbotmanager.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.wairesd.discordbotmanager.velocity.command.AdminCommand;
import com.wairesd.discordbotmanager.velocity.config.Messages;
import com.wairesd.discordbotmanager.velocity.config.Settings;
import com.wairesd.discordbotmanager.velocity.discord.DiscordBotListener;
import com.wairesd.discordbotmanager.velocity.discord.ResponseHandler;
import com.wairesd.discordbotmanager.velocity.websocket.VelocityWebSocketServer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(id = "discordbotmanager", name = "DiscordBotManager", version = "1.0", authors = {"wairesd"})
public class DiscordBotManagerVelocity {
    private final Logger logger;
    private final Path dataDirectory;
    private final ProxyServer proxy;
    private JDA jda;
    private VelocityWebSocketServer wsServer;
    private DiscordBotListener discordBotListener;

    @Inject
    public DiscordBotManagerVelocity(Logger logger, @DataDirectory Path dataDirectory, ProxyServer proxy) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.proxy = proxy;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        Settings.init(dataDirectory);
        Messages.init(dataDirectory);
        wsServer = new VelocityWebSocketServer(logger);
        wsServer.initialize();

        // Passing the plugin instance to the command
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("discordbotmanager").build(),
                new AdminCommand(this)
        );

        String token = Settings.getBotToken();
        if (token == null || token.isEmpty()) {
            logger.error("Bot token is not specified in settings.yml!");
            return;
        }
        try {
            discordBotListener = new DiscordBotListener(this, wsServer, logger);
            ResponseHandler.init(discordBotListener, logger);

            // Установка активности из конфигурации
            Activity activity = createActivity();
            jda = JDABuilder.createDefault(token)
                    .setActivity(activity)
                    .addEventListeners(discordBotListener)
                    .build()
                    .awaitReady();
            wsServer.setJda(jda);
            logger.info("Discord bot successfully started.");
        } catch (Exception e) {
            logger.error("Error initializing JDA: {}", e.getMessage(), e);
        }
    }

    // Создание объекта Activity на основе настроек
    private Activity createActivity() {
        String activityType = Settings.getActivityType().toLowerCase();
        String activityMessage = Settings.getActivityMessage();
        switch (activityType) {
            case "playing":
                return Activity.playing(activityMessage);
            case "watching":
                return Activity.watching(activityMessage);
            case "listening":
                return Activity.listening(activityMessage);
            default:
                return Activity.playing(activityMessage); // default
        }
    }

    // Updating the bot activity
    public void updateActivity() {
        if (jda != null) {
            Activity activity = createActivity();
            jda.getPresence().setActivity(activity);
            logger.info("Bot activity updated to: {} {}", Settings.getActivityType(), Settings.getActivityMessage());
        }
    }

    public ProxyServer getProxy() {
        return proxy;
    }
}