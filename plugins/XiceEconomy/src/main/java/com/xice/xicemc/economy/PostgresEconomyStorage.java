package com.xice.xicemc.economy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class PostgresEconomyStorage implements EconomyStorage {
    private final Logger logger;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final CurrencyConfig currency;

    public PostgresEconomyStorage(
            Logger logger,
            String jdbcUrl,
            String username,
            String password,
            CurrencyConfig currency
    ) {
        this.logger = logger;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.currency = currency;
    }

    @Override
    public void start() throws SQLException {
        try (Connection connection = connect()) {
            initialize(connection);
        }
        logger.info("XiceEconomy storage initialized for currency: " + currency.code());
    }

    @Override
    public EconomyAccount getOrCreateAccount(UUID playerUuid, String playerName) throws SQLException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                EconomyAccount account = ensureAccount(connection, playerUuid, playerName);
                connection.commit();
                return account;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    @Override
    public Optional<EconomyAccount> findAccountByName(String playerName) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT currency_code, player_uuid, player_name, balance, created_at, updated_at
                     FROM xice_economy_accounts
                     WHERE currency_code = ? AND lower(player_name) = lower(?)
                     ORDER BY updated_at DESC
                     LIMIT 1
                     """)) {
            statement.setString(1, currency.code());
            statement.setString(2, playerName);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    return Optional.of(readAccount(rows));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public TransferResult transfer(
            UUID senderUuid,
            String senderName,
            UUID receiverUuid,
            String receiverName,
            long amount
    ) throws SQLException, InsufficientFundsException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                ensureAccount(connection, senderUuid, senderName);
                ensureAccount(connection, receiverUuid, receiverName);

                lockAccountsInStableOrder(connection, senderUuid, receiverUuid);
                EconomyAccount sender = requireLockedAccount(connection, senderUuid);
                EconomyAccount receiver = requireLockedAccount(connection, receiverUuid);
                if (!currency.allowNegative() && sender.balance() < amount) {
                    throw new InsufficientFundsException(sender.balance());
                }

                long now = System.currentTimeMillis();
                long senderBalance = sender.balance() - amount;
                long receiverBalance = receiver.balance() + amount;
                EconomyAccount updatedSender = updateBalance(connection, senderUuid, senderName, senderBalance, now);
                EconomyAccount updatedReceiver = updateBalance(connection, receiverUuid, receiverName, receiverBalance, now);
                insertTransaction(connection, now, "PAY_OUT", senderUuid, senderName, receiverUuid, receiverName,
                        -amount, senderBalance, senderUuid, senderName, "pay");
                insertTransaction(connection, now, "PAY_IN", receiverUuid, receiverName, senderUuid, senderName,
                        amount, receiverBalance, senderUuid, senderName, "pay");
                connection.commit();
                return new TransferResult(updatedSender, updatedReceiver, amount);
            } catch (SQLException | InsufficientFundsException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    @Override
    public BalanceChange adjust(
            UUID playerUuid,
            String playerName,
            UUID actorUuid,
            String actorName,
            long delta,
            String type,
            String reason
    ) throws SQLException, InsufficientFundsException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                ensureAccount(connection, playerUuid, playerName);
                EconomyAccount account = requireLockedAccount(connection, playerUuid);
                long newBalance = account.balance() + delta;
                if (!currency.allowNegative() && newBalance < 0) {
                    throw new InsufficientFundsException(account.balance());
                }
                long now = System.currentTimeMillis();
                EconomyAccount updated = updateBalance(connection, playerUuid, playerName, newBalance, now);
                insertTransaction(connection, now, type, playerUuid, playerName, null, null, delta, newBalance,
                        actorUuid, actorName, reason);
                connection.commit();
                return new BalanceChange(updated, delta);
            } catch (SQLException | InsufficientFundsException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    @Override
    public BalanceChange setBalance(
            UUID playerUuid,
            String playerName,
            UUID actorUuid,
            String actorName,
            long balance,
            String reason
    ) throws SQLException, InsufficientFundsException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                ensureAccount(connection, playerUuid, playerName);
                EconomyAccount account = requireLockedAccount(connection, playerUuid);
                if (!currency.allowNegative() && balance < 0) {
                    throw new InsufficientFundsException(account.balance());
                }
                long delta = balance - account.balance();
                long now = System.currentTimeMillis();
                EconomyAccount updated = updateBalance(connection, playerUuid, playerName, balance, now);
                insertTransaction(connection, now, "ADMIN_SET", playerUuid, playerName, null, null, delta, balance,
                        actorUuid, actorName, reason);
                connection.commit();
                return new BalanceChange(updated, delta);
            } catch (SQLException | InsufficientFundsException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    @Override
    public List<EconomyAccount> topAccounts(int limit) throws SQLException {
        List<EconomyAccount> accounts = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT currency_code, player_uuid, player_name, balance, created_at, updated_at
                     FROM xice_economy_accounts
                     WHERE currency_code = ?
                     ORDER BY balance DESC, updated_at ASC
                     LIMIT ?
                     """)) {
            statement.setString(1, currency.code());
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    accounts.add(readAccount(rows));
                }
            }
        }
        return accounts;
    }

    @Override
    public List<EconomyTransaction> history(UUID playerUuid, int limit) throws SQLException {
        List<EconomyTransaction> transactions = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, created_at, currency_code, tx_type, player_uuid, player_name,
                            counterparty_uuid, counterparty_name, amount, balance_after,
                            actor_uuid, actor_name, reason
                     FROM xice_economy_transactions
                     WHERE currency_code = ? AND player_uuid = ?
                     ORDER BY id DESC
                     LIMIT ?
                     """)) {
            statement.setString(1, currency.code());
            statement.setString(2, playerUuid.toString());
            statement.setInt(3, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    transactions.add(readTransaction(rows));
                }
            }
        }
        return transactions;
    }

    @Override
    public void close() {
        // Connections are short-lived for this small first version.
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
                    CREATE TABLE IF NOT EXISTS xice_economy_accounts (
                      currency_code TEXT NOT NULL,
                      player_uuid TEXT NOT NULL,
                      player_name TEXT NOT NULL,
                      balance BIGINT NOT NULL,
                      created_at BIGINT NOT NULL,
                      updated_at BIGINT NOT NULL,
                      PRIMARY KEY (currency_code, player_uuid)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS xice_economy_transactions (
                      id BIGSERIAL PRIMARY KEY,
                      created_at BIGINT NOT NULL,
                      currency_code TEXT NOT NULL,
                      tx_type TEXT NOT NULL,
                      player_uuid TEXT NOT NULL,
                      player_name TEXT NOT NULL,
                      counterparty_uuid TEXT,
                      counterparty_name TEXT,
                      amount BIGINT NOT NULL,
                      balance_after BIGINT NOT NULL,
                      actor_uuid TEXT,
                      actor_name TEXT,
                      reason TEXT
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_xice_economy_accounts_name ON xice_economy_accounts(currency_code, lower(player_name))");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_xice_economy_accounts_balance ON xice_economy_accounts(currency_code, balance DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_xice_economy_transactions_player ON xice_economy_transactions(currency_code, player_uuid, id DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_xice_economy_transactions_time ON xice_economy_transactions(currency_code, created_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_xice_economy_transactions_type ON xice_economy_transactions(currency_code, tx_type, created_at)");
        }
    }

    private EconomyAccount ensureAccount(Connection connection, UUID playerUuid, String playerName) throws SQLException {
        EconomyAccount existing = selectAccount(connection, playerUuid, false);
        if (existing != null) {
            if (!existing.playerName().equals(playerName)) {
                long now = System.currentTimeMillis();
                updateAccountName(connection, playerUuid, playerName, now);
                return new EconomyAccount(existing.currencyCode(), existing.playerUuid(), playerName,
                        existing.balance(), existing.createdAt(), now);
            }
            return existing;
        }

        long now = System.currentTimeMillis();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO xice_economy_accounts
                  (currency_code, player_uuid, player_name, balance, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, currency.code());
            statement.setString(2, playerUuid.toString());
            statement.setString(3, playerName);
            statement.setLong(4, currency.initialBalance());
            statement.setLong(5, now);
            statement.setLong(6, now);
            statement.executeUpdate();
        }
        if (currency.initialBalance() != 0) {
            insertTransaction(connection, now, "INITIAL_BALANCE", playerUuid, playerName, null, null,
                    currency.initialBalance(), currency.initialBalance(), null, "SYSTEM", "account-created");
        }
        return new EconomyAccount(currency.code(), playerUuid, playerName, currency.initialBalance(), now, now);
    }

    private void lockAccountsInStableOrder(Connection connection, UUID first, UUID second) throws SQLException {
        if (first.toString().compareTo(second.toString()) <= 0) {
            requireLockedAccount(connection, first);
            requireLockedAccount(connection, second);
        } else {
            requireLockedAccount(connection, second);
            requireLockedAccount(connection, first);
        }
    }

    private EconomyAccount requireLockedAccount(Connection connection, UUID playerUuid) throws SQLException {
        EconomyAccount account = selectAccount(connection, playerUuid, true);
        if (account == null) {
            throw new SQLException("Economy account disappeared while locking: " + playerUuid);
        }
        return account;
    }

    private EconomyAccount selectAccount(Connection connection, UUID playerUuid, boolean forUpdate) throws SQLException {
        String sql = """
                SELECT currency_code, player_uuid, player_name, balance, created_at, updated_at
                FROM xice_economy_accounts
                WHERE currency_code = ? AND player_uuid = ?
                """ + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, currency.code());
            statement.setString(2, playerUuid.toString());
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    return readAccount(rows);
                }
            }
        }
        return null;
    }

    private EconomyAccount updateBalance(
            Connection connection,
            UUID playerUuid,
            String playerName,
            long balance,
            long now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE xice_economy_accounts
                SET player_name = ?, balance = ?, updated_at = ?
                WHERE currency_code = ? AND player_uuid = ?
                """)) {
            statement.setString(1, playerName);
            statement.setLong(2, balance);
            statement.setLong(3, now);
            statement.setString(4, currency.code());
            statement.setString(5, playerUuid.toString());
            statement.executeUpdate();
        }
        EconomyAccount account = selectAccount(connection, playerUuid, false);
        if (account == null) {
            throw new SQLException("Economy account missing after update: " + playerUuid);
        }
        return account;
    }

    private void updateAccountName(Connection connection, UUID playerUuid, String playerName, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE xice_economy_accounts
                SET player_name = ?, updated_at = ?
                WHERE currency_code = ? AND player_uuid = ?
                """)) {
            statement.setString(1, playerName);
            statement.setLong(2, now);
            statement.setString(3, currency.code());
            statement.setString(4, playerUuid.toString());
            statement.executeUpdate();
        }
    }

    private void insertTransaction(
            Connection connection,
            long createdAt,
            String type,
            UUID playerUuid,
            String playerName,
            UUID counterpartyUuid,
            String counterpartyName,
            long amount,
            long balanceAfter,
            UUID actorUuid,
            String actorName,
            String reason
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO xice_economy_transactions (
                  created_at, currency_code, tx_type, player_uuid, player_name,
                  counterparty_uuid, counterparty_name, amount, balance_after,
                  actor_uuid, actor_name, reason
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, createdAt);
            statement.setString(2, currency.code());
            statement.setString(3, type);
            statement.setString(4, playerUuid.toString());
            statement.setString(5, playerName);
            statement.setString(6, counterpartyUuid == null ? null : counterpartyUuid.toString());
            statement.setString(7, counterpartyName);
            statement.setLong(8, amount);
            statement.setLong(9, balanceAfter);
            statement.setString(10, actorUuid == null ? null : actorUuid.toString());
            statement.setString(11, actorName);
            statement.setString(12, reason);
            statement.executeUpdate();
        }
    }

    private EconomyAccount readAccount(ResultSet rows) throws SQLException {
        return new EconomyAccount(
                rows.getString("currency_code"),
                UUID.fromString(rows.getString("player_uuid")),
                rows.getString("player_name"),
                rows.getLong("balance"),
                rows.getLong("created_at"),
                rows.getLong("updated_at"));
    }

    private EconomyTransaction readTransaction(ResultSet rows) throws SQLException {
        String counterpartyUuid = rows.getString("counterparty_uuid");
        String actorUuid = rows.getString("actor_uuid");
        return new EconomyTransaction(
                rows.getLong("id"),
                rows.getLong("created_at"),
                rows.getString("currency_code"),
                rows.getString("tx_type"),
                UUID.fromString(rows.getString("player_uuid")),
                rows.getString("player_name"),
                counterpartyUuid == null ? null : UUID.fromString(counterpartyUuid),
                rows.getString("counterparty_name"),
                rows.getLong("amount"),
                rows.getLong("balance_after"),
                actorUuid == null ? null : UUID.fromString(actorUuid),
                rows.getString("actor_name"),
                rows.getString("reason"));
    }
}
