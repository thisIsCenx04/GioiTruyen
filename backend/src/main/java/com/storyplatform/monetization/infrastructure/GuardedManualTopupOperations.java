package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.ManualTopupOperations;
import com.storyplatform.monetization.application
        .MonetizationKillSwitchGuard;
import com.storyplatform.monetization.domain.MonetizationKillSwitch;

import java.util.Objects;

public final class GuardedManualTopupOperations
        implements ManualTopupOperations {

    private final ManualTopupOperations delegate;
    private final MonetizationKillSwitchGuard guard;

    public GuardedManualTopupOperations(
            ManualTopupOperations delegate,
            MonetizationKillSwitchGuard guard
    ) {
        this.delegate = Objects.requireNonNull(delegate);
        this.guard = Objects.requireNonNull(guard);
    }

    @Override
    public Approval approve(
            String actorId,
            String topupId,
            String reauthenticationToken,
            String reason,
            String evidenceReference
    ) {
        guard.requireOpen(MonetizationKillSwitch.Operation.TOPUP_CREDIT);
        return delegate.approve(
                actorId,
                topupId,
                reauthenticationToken,
                reason,
                evidenceReference
        );
    }
}
