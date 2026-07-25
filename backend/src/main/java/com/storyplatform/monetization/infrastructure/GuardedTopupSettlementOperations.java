package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application
        .MonetizationKillSwitchGuard;
import com.storyplatform.monetization.application
        .TopupSettlementOperations;
import com.storyplatform.monetization.domain.MonetizationKillSwitch;

import java.util.Objects;

public final class GuardedTopupSettlementOperations
        implements TopupSettlementOperations {

    private final TopupSettlementOperations delegate;
    private final MonetizationKillSwitchGuard guard;

    public GuardedTopupSettlementOperations(
            TopupSettlementOperations delegate,
            MonetizationKillSwitchGuard guard
    ) {
        this.delegate = Objects.requireNonNull(delegate);
        this.guard = Objects.requireNonNull(guard);
    }

    @Override
    public Result settle(String provider, String providerEventId) {
        if (guard.engaged(
                MonetizationKillSwitch.Operation.TOPUP_CREDIT
        )) {
            return Result.SUSPENDED;
        }
        return delegate.settle(provider, providerEventId);
    }
}
