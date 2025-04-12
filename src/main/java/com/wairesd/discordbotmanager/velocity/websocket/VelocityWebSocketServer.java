package com.wairesd.discordbotmanager.velocity.websocket;

import com.google.gson.Gson;
import com.wairesd.discordbotmanager.velocity.config.Settings;
import com.wairesd.discordbotmanager.velocity.discord.ResponseHandler;
import com.wairesd.discordbotmanager.velocity.model.RegisterMessage;
import com.wairesd.discordbotmanager.velocity.model.ResponseMessage;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

// This class manages the WebSocket server for communication between Velocity and Bukkit plugins.
public class VelocityWebSocketServer {
    private final Logger logger;
    private WebSocketServerImpl wsServer;
    private JDA jda;
    private final Gson gson = new Gson();
    private final ConcurrentHashMap<String, WebSocket> commandToConnection = new ConcurrentHashMap<>();

    public VelocityWebSocketServer(Logger logger) {
        this.logger = logger;
    }

    public void initialize() {
        wsServer = new WebSocketServerImpl(new InetSocketAddress(Settings.getWebsocketPort()), logger);
        wsServer.start();
        logger.info("WebSocket server started on port {}", Settings.getWebsocketPort());
    }

    public void setJda(JDA jda) {
        this.jda = jda;
    }

    public ConcurrentHashMap<String, WebSocket> getCommandToConnection() {
        return commandToConnection;
    }

    public void sendRequest(WebSocket conn, String message) {
        if (conn.isOpen()) {
            conn.send(message);
            if (Settings.isDebug()) {
                logger.debug("Sent message {} to {}", message, conn.getRemoteSocketAddress());
            }
        } else {
            logger.warn("Attempt to send message to closed connection: {}", conn.getRemoteSocketAddress());
        }
    }

    private class WebSocketServerImpl extends WebSocketServer {
        private final Logger logger;

        public WebSocketServerImpl(InetSocketAddress address, Logger logger) {
            super(address);
            this.logger = logger;
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            String providedSecret = handshake.getFieldValue("Secret");
            String expectedSecret = Settings.getSecretCode();
            if (expectedSecret == null || !expectedSecret.equals(providedSecret)) {
                logger.warn("Invalid secret from {}", conn.getRemoteSocketAddress());
                conn.close(1008, "Invalid secret code");
                return;
            }
            if (Settings.isDebug()) {
                logger.info("Authenticated connection from {}", conn.getRemoteSocketAddress());
            }
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            logger.info("Connection closed: {} | Code: {} | Reason: {}", conn.getRemoteSocketAddress(), code, reason);
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            if (Settings.isDebug()) {
                logger.debug("Received message from {}: {}", conn.getRemoteSocketAddress(), message);
            }
            try {
                var json = gson.fromJson(message, com.google.gson.JsonObject.class);
                String type = json.get("type").getAsString();
                switch (type) {
                    case "register":
                        RegisterMessage regMsg = gson.fromJson(message, RegisterMessage.class);
                        for (var cmd : regMsg.commands()) {
                            commandToConnection.put(cmd.name(), conn);
                            var cmdData = Commands.slash(cmd.name(), cmd.description());
                            for (var opt : cmd.options()) {
                                cmdData.addOption(
                                        OptionType.valueOf(opt.type()),
                                        opt.name(),
                                        opt.description(),
                                        opt.required()
                                );
                            }
                            jda.upsertCommand(cmdData).queue();
                            if (Settings.isDebug()) {
                                logger.info("Registered command: {}", cmd.name());
                            }
                        }
                        break;
                    case "response":
                        ResponseMessage respMsg = gson.fromJson(message, ResponseMessage.class);
                        ResponseHandler.handleResponse(respMsg.requestId(), respMsg.response());
                        break;
                    default:
                        logger.warn("Unknown message type: {}", type);
                }
            } catch (Exception e) {
                logger.error("Error processing message: {}", message, e);
            }
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            logger.error("WebSocket error: {}", conn != null ? conn.getRemoteSocketAddress() : "server", ex);
        }

        @Override
        public void onStart() {
            logger.info("WebSocket server started successfully!");
        }
    }
}