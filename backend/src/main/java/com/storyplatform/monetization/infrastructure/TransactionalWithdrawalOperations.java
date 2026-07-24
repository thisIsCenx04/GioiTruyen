package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.WithdrawalOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalWithdrawalOperations
        implements WithdrawalOperations {

    private final WithdrawalOperations delegate;

    public TransactionalWithdrawalOperations(WithdrawalOperations delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    @Transactional
    public Receipt create(
            String actorId,
            String teamId,
            String idempotencyKey,
            long grossAmountXu,
            String destinationId
    ) {
        return delegate.create(
                actorId,
                teamId,
                idempotencyKey,
                grossAmountXu,
                destinationId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page list(
            String actorId,
            String teamId,
            String cursor,
            int limit
    ) {
        return delegate.list(actorId, teamId, cursor, limit);
    }
}
