package com.wairesd.discordbotmanager.velocity.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.wairesd.discordbotmanager.velocity.config.Settings;
import com.wairesd.discordbotmanager.velocity.database.DatabaseManager;
import com.wairesd.discordbotmanager.velocity.discord.ResponseHandler;
import com.wairesd.discordbotmanager.velocity.model.RegisterMessage;
import com.wairesd.discordbotmanager.velocity.model.ResponseMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;

import java.net.InetSocketAddress;

/**
 * Handles incoming messages and events for the Netty server.
 */
public class NettyServerHandler extends SimpleChannelInboundHandler<String> {
    private final Gson gson = new Gson();
    private final Logger logger;
    private final Object jda;
    private final DatabaseManager dbManager;
    private final NettyServer nettyServer;
    private boolean authenticated = false;

    public NettyServerHandler(NettyServer nettyServer, Logger logger, Object jda, DatabaseManager dbManager) {
        this.nettyServer = nettyServer;
        this.logger = logger;
        this.jda = jda;
        this.dbManager = dbManager;
    }

    /**
     * Called when a client connects to the server.
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String ip = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
        if (Settings.isDebugConnections()) {
            logger.info("Client connected: {}", ctx.channel().remoteAddress());
        }
        dbManager.isBlocked(ip).thenAcceptAsync(isBlocked -> {
            if (isBlocked) {
                if (Settings.isViewConnectedBannedIp()) {
                    logger.warn("Blocked connection attempt from {}", ip);
                }
                ctx.writeAndFlush("Error: IP blocked due to multiple failed attempts");
                ctx.close();
            } else {
                ctx.executor().schedule(() -> {
                    if (!authenticated) {
                        if (Settings.isDebugAuthentication()) {
                            logger.warn("Client {} did not authenticate in time. Closing connection.", ip);
                        }
                        ctx.writeAndFlush("Error: Authentication timeout");
                        dbManager.incrementFailedAttempt(ip);
                        ctx.close();
                    }
                }, 30, java.util.concurrent.TimeUnit.SECONDS);
            }
        }, ctx.executor());
    }

    /**
     * Processes incoming messages from clients.
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        if (Settings.isDebugClientResponses()) {
            logger.info("Received message from client: {}", msg);
        }

        JsonObject json = gson.fromJson(msg, JsonObject.class);
        RegisterMessage regMsg = gson.fromJson(json, RegisterMessage.class);

        if ("register".equals(regMsg.type())) {
            InetSocketAddress remoteAddress = (InetSocketAddress) ctx.channel().remoteAddress();
            String ip = remoteAddress.getAddress().getHostAddress();
            int port = remoteAddress.getPort();
            handleRegister(ctx, regMsg, ip, port);
        } else if ("response".equals(regMsg.type())) {
            handleResponse(json);
        }
    }

    private void handleRegister(ChannelHandlerContext ctx, RegisterMessage regMsg, String ip, int port) {
        if (regMsg.secret() == null || !regMsg.secret().equals(Settings.getSecretCode())) {
            if (Settings.isDebugAuthentication()) {
                logger.warn("Invalid secret from {}:{}: {}", ip, port, regMsg.secret());
            }
            ctx.writeAndFlush("Error: Invalid secret code");
            dbManager.incrementFailedAttempt(ip);
            ctx.close();
            return;
        }

        if (!authenticated) {
            authenticated = true;
            dbManager.resetAttempts(ip);
            if (Settings.isDebugAuthentication()) {
                logger.info("Client {} IP - {} Port - {} authenticated successfully", regMsg.serverName(), ip, port);
            }
        }

        if (regMsg.commands() != null && !regMsg.commands().isEmpty()) {
            if (Settings.isDebugPluginConnections()) {
                logger.info("Plugin {} connected to server {}", regMsg.pluginName(), regMsg.serverName());
            }
            nettyServer.setServerName(ctx.channel(), regMsg.serverName());
            nettyServer.registerCommands(regMsg.serverName(), regMsg.commands(), ctx.channel());
        }
    }

    private void handleResponse(JsonObject json) {
        if (!authenticated) return;
        ResponseMessage respMsg = gson.fromJson(json, ResponseMessage.class);
        ResponseHandler.handleResponse(respMsg.requestId(), respMsg.response());
    }

    /**
     * Called when a client disconnects.
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        String serverName = nettyServer.getServerName(ctx.channel());
        nettyServer.removeServer(ctx.channel());
        if (Settings.isDebugConnections()) {
            if (serverName != null) {
                logger.info("Connection closed: {} ({})", serverName, ctx.channel().remoteAddress());
            } else {
                logger.info("Connection closed: {}", ctx.channel().remoteAddress());
            }
        }
    }

    /**
     * Handles exceptions in the channel.
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (Settings.isDebugErrors()) {
            logger.error("Exception in Netty channel: {}", ctx.channel().remoteAddress(), cause);
        }
        ctx.close();
    }
}