package com.wairesd.discordbotmanager.velocity.model;

// Represents a response message received from the WebSocket server.
public record ResponseMessage(String type, String requestId, String response) {}