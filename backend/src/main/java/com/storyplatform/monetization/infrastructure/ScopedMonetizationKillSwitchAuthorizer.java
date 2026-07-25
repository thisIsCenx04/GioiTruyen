package com.storyplatform.monetization.infrastructure;

import com.storyplatform.identity.application.contract
        .ReauthenticationVerifier;
import com.storyplatform.monetization.application.port
        .MonetizationKillSwitchAuthorizer;
import com.storyplatform.monetization.domain.MonetizationKillSwitch;

import java.util.Objects;

public final class ScopedMonetizationKillSwitchAuthorizer
        implements MonetizationKillSwitchAuthorizer {

    private final ReauthenticationVerifier reauthentication;

    public ScopedMonetizationKillSwitchAuthorizer(
            ReauthenticationVerifier reauthentication
    ) {
        this.reauthentication = Objects.requireNonNull(reauthentication);
    }

    @Override
    public boolean consume(
            String actorId,
            String rawToken,
            MonetizationKillSwitch.Operation operation
    ) {
        return reauthentication.consume(
                rawToken,
                actorId,
                "MONETIZATION_KILL_SWITCH",
                "monetization-operation",
                operation.name().toLowerCase()
        );
    }
}
