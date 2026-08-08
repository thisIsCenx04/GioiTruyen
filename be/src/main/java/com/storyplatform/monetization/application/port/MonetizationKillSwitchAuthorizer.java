package com.storyplatform.monetization.application.port;

import com.storyplatform.monetization.domain.MonetizationKillSwitch;

public interface MonetizationKillSwitchAuthorizer {

    boolean consume(
            String actorId,
            String rawToken,
            MonetizationKillSwitch.Operation operation
    );
}
