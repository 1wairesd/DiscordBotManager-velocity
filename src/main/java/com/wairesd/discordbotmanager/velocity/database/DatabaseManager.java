package com.wairesd.discordbotmanager.velocity.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages database operations for IP blocking in Velocity.
 */
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private final String dbUrl;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public DatabaseManager(String dbUrl) {
        this.dbUrl = dbUrl;
        initDatabase();
    }

    /** Initializes the SQLite database and creates the ip_blocks table if it doesn't exist. */
    private void initDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection conn = DriverManager.getConnection(dbUrl);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS ip_blocks (" +
                        "ip TEXT PRIMARY KEY," +
                        "attempts INTEGER DEFAULT 0," +
                        "block_until TIMESTAMP," +
                        "current_block_time INTEGER DEFAULT 0)");
                logger.info("ip_blocks table created or already exists");
            }
        } catch (ClassNotFoundException e) {
            logger.error("SQLite JDBC driver not found", e);
        } catch (SQLException e) {
            logger.error("Error creating ip_blocks table", e);
        }
    }

    /** Checks if an IP is blocked asynchronously. */
    public CompletableFuture<Boolean> isBlocked(String ip) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = DriverManager.getConnection(dbUrl);
                 PreparedStatement stmt = conn.prepareStatement("SELECT block_until FROM ip_blocks WHERE ip = ?")) {
                stmt.setString(1, ip);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    Timestamp blockUntil = rs.getTimestamp("block_until");
                    return blockUntil != null && blockUntil.after(Timestamp.from(Instant.now()));
                }
                return false;
            } catch (SQLException e) {
                logger.error("Error checking blocked IP {}: {}", ip, e.getMessage());
                return false;
            }
        }, executor);
    }

    /** Increments failed attempts for an IP and blocks it if necessary. */
    public CompletableFuture<Void> incrementFailedAttempt(String ip) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = DriverManager.getConnection(dbUrl)) {
                conn.setAutoCommit(false);
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO ip_blocks (ip, attempts, current_block_time) VALUES (?, 1, ?) " +
                                "ON CONFLICT(ip) DO UPDATE SET attempts = attempts + 1")) {
                    stmt.setString(1, ip);
                    stmt.setLong(2, 5 * 60 * 1000);
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT attempts, current_block_time FROM ip_blocks WHERE ip = ?")) {
                    stmt.setString(1, ip);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        int attempts = rs.getInt("attempts");
                        long currentBlockTime = rs.getLong("current_block_time");
                        if (attempts >= 10) {
                            long blockUntil = System.currentTimeMillis() + currentBlockTime;
                            try (PreparedStatement updateStmt = conn.prepareStatement(
                                    "UPDATE ip_blocks SET block_until = ?, attempts = 0, current_block_time = ? WHERE ip = ?")) {
                                updateStmt.setTimestamp(1, new Timestamp(blockUntil));
                                updateStmt.setLong(2, Math.min(currentBlockTime * 2, 60 * 60 * 1000));
                                updateStmt.setString(3, ip);
                                updateStmt.executeUpdate();
                            }
                        }
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                logger.error("Error incrementing failed attempt for IP {}: {}", ip, e.getMessage());
            }
        }, executor);
    }

    /** Resets failed attempts for an IP. */
    public CompletableFuture<Void> resetAttempts(String ip) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = DriverManager.getConnection(dbUrl);
                 PreparedStatement stmt = conn.prepareStatement("DELETE FROM ip_blocks WHERE ip = ?")) {
                stmt.setString(1, ip);
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.error("Error resetting attempts for IP {}: {}", ip, e.getMessage());
            }
        }, executor);
    }

    /** Shuts down the executor service. */
    public void shutdown() {
        executor.shutdown();
        logger.info("Database executor shutdown");
    }
}