package com.wairesd.discordbotmanager.velocity.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.wairesd.discordbotmanager.velocity.config.Settings;
import com.wairesd.discordbotmanager.velocity.database.DatabaseManager;
import com.wairesd.discordbotmanager.velocity.discord.ResponseHandler;
import com.wairesd.discordbotmanager.velocity.model.RegisterMessage;
import com.wairesd.discordbotmanager.velocity.model.ResponseMessage;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the Netty server for handling WebSocket connections in Velocity.
 */
public class NettyServer {
    private final Logger logger;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private final Gson gson = new Gson();
    private final ConcurrentHashMap<String, Channel> commandToChannel = new ConcurrentHashMap<>();
    private volatile Object jda;
    private final int port = Settings.getNettyPort();
    private final DatabaseManager dbManager;

    public NettyServer(Logger logger, DatabaseManager dbManager) {
        this.logger = logger;
        this.dbManager = dbManager;
    }

    public void setJda(Object jda) { this.jda = jda; }
    public ConcurrentHashMap<String, Channel> getCommandToChannel() { return commandToChannel; }

    /** Starts the Netty server on the configured port. */
    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast("frameDecoder", new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));
                            ch.pipeline().addLast("stringDecoder", new StringDecoder(StandardCharsets.UTF_8));
                            ch.pipeline().addLast("frameEncoder", new LengthFieldPrepender(2));
                            ch.pipeline().addLast("stringEncoder", new StringEncoder(StandardCharsets.UTF_8));
                            ch.pipeline().addLast("handler", new NettyServerHandler(commandToChannel, logger, jda, dbManager));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();
            logger.info("Netty server started on port {}", port);
            serverChannel.closeFuture().sync();
        } catch (InterruptedException e) {
            logger.error("Netty server interrupted", e);
            Thread.currentThread().interrupt();
        } finally {
            shutdown();
        }
    }

    /** Shuts down the Netty server gracefully. */
    public void shutdown() {
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        logger.info("Netty server shutdown complete");
    }

    /** Sends a message to a specific channel if active. */
    public void sendMessage(Channel channel, String message) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(message);
            if (Settings.isDebug()) logger.debug("Sent message: {} to {}", message, channel.remoteAddress());
        } else {
            logger.warn("Attempt to send message to inactive channel: {}", channel != null ? channel.remoteAddress() : "null");
        }
    }

    /**
     * Handles incoming connections and messages for the Netty server.
     */
    private class NettyServerHandler extends SimpleChannelInboundHandler<String> {
        private final Gson gson = new Gson();
        private final ConcurrentHashMap<String, Channel> commandToChannel;
        private final Logger logger;
        private final Object jda;
        private final DatabaseManager dbManager;
        private boolean authenticated = false;

        public NettyServerHandler(ConcurrentHashMap<String, Channel> commandToChannel, Logger logger, Object jda, DatabaseManager dbManager) {
            this.commandToChannel = commandToChannel;
            this.logger = logger;
            this.jda = jda;
            this.dbManager = dbManager;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            String ip = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
            dbManager.isBlocked(ip).thenAcceptAsync(isBlocked -> {
                if (isBlocked) {
                    if (Settings.isViewConnectedBannedIp()) {
                        logger.warn("Blocked connection attempt from {}", ip);
                    }
                    ctx.writeAndFlush("Error: IP blocked due to multiple failed attempts");
                    ctx.close();
                } else {
                    logger.info("New connection from {}", ip);
                    ctx.executor().schedule(() -> {
                        if (!authenticated) {
                            logger.warn("Client {} did not authenticate in time. Closing connection.", ip);
                            ctx.writeAndFlush("Error: Authentication timeout");
                            dbManager.incrementFailedAttempt(ip);
                            ctx.close();
                        }
                    }, 10, java.util.concurrent.TimeUnit.SECONDS);
                }
            }, ctx.executor());
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            String ip = ctx.channel().remoteAddress().toString();
            if (Settings.isDebug()) logger.debug("Received message from {}: {}", ip, msg);
            try {
                JsonObject json = gson.fromJson(msg, JsonObject.class);
                String type = json.get("type").getAsString();
                if ("register".equals(type)) {
                    handleRegister(ctx, json, ip);
                } else if ("response".equals(type)) {
                    handleResponse(json);
                } else {
                    logger.warn("Unknown message type: {}", type);
                }
            } catch (Exception e) {
                logger.error("Error processing message: {}", msg, e);
            }
        }

        private void handleRegister(ChannelHandlerContext ctx, JsonObject json, String ip) {
            if (!json.has("secret")) {
                logger.warn("No secret code provided in register message from {}", ip);
                ctx.writeAndFlush("Error: No secret code provided");
                dbManager.incrementFailedAttempt(ip);
                ctx.close();
                return;
            }
            String receivedSecret = json.get("secret").getAsString();
            String expectedSecret = Settings.getSecretCode();
            if (expectedSecret == null || !expectedSecret.equals(receivedSecret)) {
                logger.warn("Unknown plugin attempted to connect from {} with invalid secret code", ip);
                ctx.writeAndFlush("Error: Invalid secret code");
                dbManager.incrementFailedAttempt(ip);
                ctx.close();
                return;
            }
            authenticated = true;
            dbManager.resetAttempts(ip);
            logger.info("Client {} authenticated successfully", ip);

            RegisterMessage regMsg = gson.fromJson(json, RegisterMessage.class);
            for (var cmd : regMsg.commands()) {
                commandToChannel.put(cmd.name(), ctx.channel());
                if (jda != null) {
                    var cmdData = net.dv8tion.jda.api.interactions.commands.build.Commands.slash(cmd.name(), cmd.description());
                    for (var opt : cmd.options()) {
                        cmdData.addOption(
                                net.dv8tion.jda.api.interactions.commands.OptionType.valueOf(opt.type()),
                                opt.name(),
                                opt.description(),
                                opt.required()
                        );
                    }
                    ((net.dv8tion.jda.api.JDA) jda).upsertCommand(cmdData).queue();
                    logger.info("Registered command: {}", cmd.name());
                } else {
                    logger.warn("JDA not set, cannot register command: {}", cmd.name());
                }
            }
        }

        private void handleResponse(JsonObject json) {
            if (!authenticated) {
                logger.warn("Unauthenticated client sent response");
                return;
            }
            ResponseMessage respMsg = gson.fromJson(json, ResponseMessage.class);
            ResponseHandler.handleResponse(respMsg.requestId(), respMsg.response());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("Exception in Netty channel: {}", ctx.channel().remoteAddress(), cause);
            ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            logger.info("Connection closed: {}", ctx.channel().remoteAddress());
        }
    }
}