package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application
        .MonetizationKillSwitchGuard;
import com.storyplatform.monetization.application.WithdrawalOperations;
import com.storyplatform.monetization.domain.MonetizationKillSwitch;

import java.util.Objects;

public final class GuardedWithdrawalOperations
        implements WithdrawalOperations {

    private final WithdrawalOperations delegate;
    private final MonetizationKillSwitchGuard guard;

    public GuardedWithdrawalOperations(
            WithdrawalOperations delegate,
            MonetizationKillSwitchGuard guard
    ) {
        this.delegate = Objects.requireNonNull(delegate);
        this.guard = Objects.requireNonNull(guard);
    }

    @Override
    public Receipt create(
            String actorId,
            String teamId,
            String idempotencyKey,
            long grossAmountXu,
            String destinationId
    ) {
        guard.requireOpen(
                MonetizationKillSwitch.Operation.WITHDRAWAL_REQUEST
        );
        return delegate.create(
                actorId,
                teamId,
                idempotencyKey,
                grossAmountXu,
                destinationId
        );
    }

    @Override
    public Page list(
            String actorId,
            String teamId,
            String cursor,
            int limit
    ) {
        return delegate.list(actorId, teamId, cursor, limit);
    }
}
