package com.storyplatform.monetization.infrastructure;

import com.storyplatform.identity.application.contract
        .ReauthenticationVerifier;
import com.storyplatform.monetization.application.port
        .WithdrawalReviewAuthorizer;

import java.util.Objects;

public final class ScopedWithdrawalReviewAuthorizer
        implements WithdrawalReviewAuthorizer {

    private final ReauthenticationVerifier reauthentication;

    public ScopedWithdrawalReviewAuthorizer(
            ReauthenticationVerifier reauthentication
    ) {
        this.reauthentication = Objects.requireNonNull(reauthentication);
    }

    @Override
    public boolean consume(
            String actorId,
            String rawToken,
            String withdrawalId
    ) {
        return reauthentication.consume(
                rawToken,
                actorId,
                "WITHDRAWAL_APPROVAL",
                "withdrawal",
                withdrawalId
        );
    }
}
