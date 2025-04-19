package com.wairesd.discordbm.velocity.model;

import java.util.Map;

// Represents a request message sent to the Netty server.
public record RequestMessage(String type, String command, Map<String, String> options, String requestId) {}