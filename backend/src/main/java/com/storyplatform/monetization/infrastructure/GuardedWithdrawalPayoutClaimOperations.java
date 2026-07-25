package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application
        .MonetizationKillSwitchGuard;
import com.storyplatform.monetization.application
        .WithdrawalPayoutClaimOperations;
import com.storyplatform.monetization.domain.MonetizationKillSwitch;

import java.util.Objects;
import java.util.Optional;

public final class GuardedWithdrawalPayoutClaimOperations
        implements WithdrawalPayoutClaimOperations {

    private final WithdrawalPayoutClaimOperations delegate;
    private final MonetizationKillSwitchGuard guard;

    public GuardedWithdrawalPayoutClaimOperations(
            WithdrawalPayoutClaimOperations delegate,
            MonetizationKillSwitchGuard guard
    ) {
        this.delegate = Objects.requireNonNull(delegate);
        this.guard = Objects.requireNonNull(guard);
    }

    @Override
    public Optional<Claim> claim() {
        if (guard.engaged(
                MonetizationKillSwitch.Operation.WITHDRAWAL_PAYOUT
        )) {
            return Optional.empty();
        }
        return delegate.claim();
    }
}
