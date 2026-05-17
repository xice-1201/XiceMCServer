package com.xice.xicemc.auditlog;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PostgresAuditStorage implements AuditStorage {
    private static final String INSERT_SQL = """
            INSERT INTO audit_log (
              created_at, action, player_uuid, player_name, world, x, y, z,
              target_type, item_type, item_amount, details
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final Logger logger;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int batchSize;
    private final BlockingQueue<AuditRecord> queue;
    private final AtomicLong droppedRecords = new AtomicLong();
    private volatile boolean running;
    private Thread worker;

    public PostgresAuditStorage(
            Logger logger,
            String jdbcUrl,
            String username,
            String password,
            int batchSize,
            int queueCapacity
    ) {
        this.logger = logger;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.batchSize = Math.max(1, batchSize);
        this.queue = new ArrayBlockingQueue<>(Math.max(1, queueCapacity));
    }

    @Override
    public void start() throws SQLException {
        try (Connection connection = connect()) {
            initialize(connection);
        }

        running = true;
        worker = new Thread(this::runWorker, "XiceAuditLog-PostgresWriter");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void enqueue(AuditRecord record) {
        if (!running) {
            return;
        }
        if (!queue.offer(record)) {
            long dropped = droppedRecords.incrementAndGet();
            if (dropped == 1 || dropped % 1000 == 0) {
                logger.warning("Audit queue is full; dropped audit records: " + dropped);
            }
        }
    }

    @Override
    public void stop() {
        running = false;
        if (worker == null) {
            return;
        }
        try {
            worker.join(10_000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        long dropped = droppedRecords.get();
        if (dropped > 0) {
            logger.warning("XiceAuditLog stopped after dropping " + dropped + " audit records.");
        }
    }

    private void runWorker() {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            while (running || !queue.isEmpty()) {
                List<AuditRecord> records = pollBatch();
                if (!records.isEmpty()) {
                    writeBatch(connection, statement, records);
                }
            }
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Audit writer stopped because of a database error.", exception);
        }
    }

    private List<AuditRecord> pollBatch() {
        List<AuditRecord> records = new ArrayList<>(batchSize);
        try {
            AuditRecord first = queue.poll(1, TimeUnit.SECONDS);
            if (first != null) {
                records.add(first);
                queue.drainTo(records, batchSize - 1);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        return records;
    }

    private void writeBatch(Connection connection, PreparedStatement statement, List<AuditRecord> records) {
        try {
            connection.setAutoCommit(false);
            for (AuditRecord record : records) {
                bind(statement, record);
                statement.addBatch();
            }
            statement.executeBatch();
            connection.commit();
        } catch (SQLException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            logger.log(Level.WARNING, "Failed to write audit batch.", exception);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Failed to restore audit connection autocommit.", exception);
            }
        }
    }

    private void bind(PreparedStatement statement, AuditRecord record) throws SQLException {
        statement.setLong(1, record.createdAt());
        statement.setString(2, record.action());
        statement.setString(3, record.playerUuid());
        statement.setString(4, record.playerName());
        statement.setString(5, record.world());
        statement.setInt(6, record.x());
        statement.setInt(7, record.y());
        statement.setInt(8, record.z());
        statement.setString(9, record.targetType());
        statement.setString(10, record.itemType());
        statement.setInt(11, record.itemAmount());
        statement.setString(12, record.details());
    }

    private Connection connect() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("PostgreSQL JDBC driver is not available.", exception);
        }
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private void initialize(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS audit_log (
                      id BIGSERIAL PRIMARY KEY,
                      created_at BIGINT NOT NULL,
                      action TEXT NOT NULL,
                      player_uuid TEXT NOT NULL,
                      player_name TEXT NOT NULL,
                      world TEXT NOT NULL,
                      x INTEGER NOT NULL,
                      y INTEGER NOT NULL,
                      z INTEGER NOT NULL,
                      target_type TEXT NOT NULL,
                      item_type TEXT,
                      item_amount INTEGER NOT NULL DEFAULT 0,
                      details TEXT
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_log_time ON audit_log(created_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_log_player_time ON audit_log(player_uuid, created_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_log_player_action_time ON audit_log(player_uuid, action, created_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_log_player_name_time ON audit_log(player_name, created_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_log_location_time ON audit_log(world, x, y, z, created_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_log_action_time ON audit_log(action, created_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_log_target_time ON audit_log(target_type, created_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_log_item_time ON audit_log(item_type, created_at)");
        }
    }
}
