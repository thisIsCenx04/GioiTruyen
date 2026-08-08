package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.port
        .LedgerBalanceProjector;
import com.storyplatform.monetization.application.port.WalletRepository;
import com.storyplatform.monetization.domain.LedgerTransaction;

import java.time.Clock;
import java.util.Objects;

public final class WalletBalanceProjector
        implements LedgerBalanceProjector {

    private final WalletRepository repository;
    private final Clock clock;

    public WalletBalanceProjector(
            WalletRepository repository,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void project(LedgerTransaction transaction) {
        repository.project(transaction, clock.instant());
    }
}
