package com.wairesd.discordbm.velocity.model;

// Represents a response message received from the Netty server.
public record ResponseMessage(String type, String requestId, String response) {}