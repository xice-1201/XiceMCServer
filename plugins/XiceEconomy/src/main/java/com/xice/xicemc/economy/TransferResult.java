package com.xice.xicemc.economy;

public record TransferResult(
        EconomyAccount sender,
        EconomyAccount receiver,
        long amount
) {
}
