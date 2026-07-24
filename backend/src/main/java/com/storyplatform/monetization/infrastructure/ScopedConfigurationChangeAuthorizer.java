package com.storyplatform.monetization.infrastructure;

import com.storyplatform.identity.application.contract
        .ReauthenticationVerifier;
import com.storyplatform.monetization.application.port
        .ConfigurationChangeAuthorizer;

import java.util.Objects;

public final class ScopedConfigurationChangeAuthorizer
        implements ConfigurationChangeAuthorizer {

    private final ReauthenticationVerifier reauthentication;

    public ScopedConfigurationChangeAuthorizer(
            ReauthenticationVerifier reauthentication
    ) {
        this.reauthentication = Objects.requireNonNull(reauthentication);
    }

    @Override
    public boolean consume(String actorId, String rawToken) {
        return reauthentication.consume(
                rawToken,
                actorId,
                "SYSTEM_CONFIG_CHANGE",
                "configuration",
                "topup-discount"
        );
    }
}
