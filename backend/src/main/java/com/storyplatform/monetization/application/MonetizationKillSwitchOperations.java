package com.storyplatform.monetization.application;

import com.storyplatform.monetization.domain.MonetizationKillSwitch;

import java.util.List;

public interface MonetizationKillSwitchOperations {

    List<MonetizationKillSwitch> current();

    MonetizationKillSwitch update(
            String actorId,
            String reauthenticationToken,
            MonetizationKillSwitch.Operation operation,
            long expectedVersion,
            boolean engaged,
            String reason
    );
}
