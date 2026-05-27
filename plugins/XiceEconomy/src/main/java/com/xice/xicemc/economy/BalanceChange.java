package com.xice.xicemc.economy;

public record BalanceChange(
        EconomyAccount account,
        long amount
) {
}
