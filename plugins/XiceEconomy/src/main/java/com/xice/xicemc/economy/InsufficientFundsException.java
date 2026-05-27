package com.xice.xicemc.economy;

public final class InsufficientFundsException extends Exception {
    private final long balance;

    public InsufficientFundsException(long balance) {
        super("Insufficient funds: " + balance);
        this.balance = balance;
    }

    public long balance() {
        return balance;
    }
}
