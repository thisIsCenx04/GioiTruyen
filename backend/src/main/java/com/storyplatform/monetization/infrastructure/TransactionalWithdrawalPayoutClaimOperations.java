package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application
        .WithdrawalPayoutClaimOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

public class TransactionalWithdrawalPayoutClaimOperations
        implements WithdrawalPayoutClaimOperations {

    private final WithdrawalPayoutClaimOperations delegate;

    public TransactionalWithdrawalPayoutClaimOperations(
            WithdrawalPayoutClaimOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    @Transactional
    public Optional<Claim> claim() {
        return delegate.claim();
    }
}
