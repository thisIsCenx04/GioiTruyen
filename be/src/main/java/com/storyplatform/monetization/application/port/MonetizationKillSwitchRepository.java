package com.storyplatform.monetization.application.port;

import com.storyplatform.monetization.domain.MonetizationKillSwitch;

import java.util.Optional;

public interface MonetizationKillSwitchRepository {

    Optional<MonetizationKillSwitch> find(
            MonetizationKillSwitch.Operation operation
    );

    boolean save(
            MonetizationKillSwitch expected,
            MonetizationKillSwitch replacement,
            String reason
    );
}
