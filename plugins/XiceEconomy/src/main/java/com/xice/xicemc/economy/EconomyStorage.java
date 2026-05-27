package com.xice.xicemc.economy;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EconomyStorage extends AutoCloseable {
    void start() throws SQLException;

    EconomyAccount getOrCreateAccount(UUID playerUuid, String playerName) throws SQLException;

    Optional<EconomyAccount> findAccountByName(String playerName) throws SQLException;

    TransferResult transfer(
            UUID senderUuid,
            String senderName,
            UUID receiverUuid,
            String receiverName,
            long amount
    ) throws SQLException, InsufficientFundsException;

    BalanceChange adjust(
            UUID playerUuid,
            String playerName,
            UUID actorUuid,
            String actorName,
            long delta,
            String type,
            String reason
    ) throws SQLException, InsufficientFundsException;

    BalanceChange setBalance(
            UUID playerUuid,
            String playerName,
            UUID actorUuid,
            String actorName,
            long balance,
            String reason
    ) throws SQLException, InsufficientFundsException;

    List<EconomyAccount> topAccounts(int limit) throws SQLException;

    List<EconomyTransaction> history(UUID playerUuid, int limit) throws SQLException;

    @Override
    void close();
}
