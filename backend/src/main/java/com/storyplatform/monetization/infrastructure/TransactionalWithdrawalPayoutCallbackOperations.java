package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application
        .WithdrawalPayoutCallbackOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalWithdrawalPayoutCallbackOperations
        implements WithdrawalPayoutCallbackOperations {

    private final WithdrawalPayoutCallbackOperations delegate;

    public TransactionalWithdrawalPayoutCallbackOperations(
            WithdrawalPayoutCallbackOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    @Transactional
    public void accept(
            String provider,
            byte[] rawBody,
            String timestamp,
            String signature
    ) {
        delegate.accept(provider, rawBody, timestamp, signature);
    }
}
