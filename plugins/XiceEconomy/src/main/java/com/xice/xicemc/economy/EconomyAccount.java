package com.xice.xicemc.economy;

import java.util.UUID;

public record EconomyAccount(
        String currencyCode,
        UUID playerUuid,
        String playerName,
        long balance,
        long createdAt,
        long updatedAt
) {
}
