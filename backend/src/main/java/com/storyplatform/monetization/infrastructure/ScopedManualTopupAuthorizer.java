package com.storyplatform.monetization.infrastructure;

import com.storyplatform.identity.application.contract
        .ReauthenticationVerifier;
import com.storyplatform.monetization.application.port.ManualTopupAuthorizer;

import java.util.Objects;

public final class ScopedManualTopupAuthorizer
        implements ManualTopupAuthorizer {

    private final ReauthenticationVerifier reauthentication;

    public ScopedManualTopupAuthorizer(
            ReauthenticationVerifier reauthentication
    ) {
        this.reauthentication = Objects.requireNonNull(reauthentication);
    }

    @Override
    public boolean consume(
            String actorId,
            String rawToken,
            String topupRequestId
    ) {
        return reauthentication.consume(
                rawToken,
                actorId,
                "TOPUP_MANUAL_APPROVAL",
                "topup_request",
                topupRequestId
        );
    }
}
