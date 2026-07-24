package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application
        .WithdrawalReviewOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalWithdrawalReviewOperations
        implements WithdrawalReviewOperations {

    private final WithdrawalReviewOperations delegate;

    public TransactionalWithdrawalReviewOperations(
            WithdrawalReviewOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    @Transactional
    public Decision approve(
            String actorId,
            String withdrawalId,
            String reauthenticationToken,
            String idempotencyKey,
            String reason
    ) {
        return delegate.approve(
                actorId,
                withdrawalId,
                reauthenticationToken,
                idempotencyKey,
                reason
        );
    }

    @Override
    @Transactional
    public Decision reject(
            String actorId,
            String withdrawalId,
            String reauthenticationToken,
            String idempotencyKey,
            String reason
    ) {
        return delegate.reject(
                actorId,
                withdrawalId,
                reauthenticationToken,
                idempotencyKey,
                reason
        );
    }
}
