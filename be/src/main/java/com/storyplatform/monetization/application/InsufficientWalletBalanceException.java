package com.storyplatform.monetization.application;

public final class InsufficientWalletBalanceException
        extends RuntimeException {

    public InsufficientWalletBalanceException() {
        super("A wallet has insufficient funds for this posting.");
    }
}
