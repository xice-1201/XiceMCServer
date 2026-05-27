package com.xice.xicemc.economy;

import java.util.UUID;

public record EconomyTransaction(
        long id,
        long createdAt,
        String currencyCode,
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
) {
}
