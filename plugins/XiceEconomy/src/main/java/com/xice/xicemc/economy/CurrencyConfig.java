package com.xice.xicemc.economy;

public record CurrencyConfig(
        String code,
        String displayName,
        String symbol,
        long initialBalance,
        boolean allowNegative
) {
}
