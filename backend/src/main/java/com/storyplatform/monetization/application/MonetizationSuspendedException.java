package com.storyplatform.monetization.application;

import com.storyplatform.monetization.domain.MonetizationKillSwitch;

public final class MonetizationSuspendedException
        extends RuntimeException {

    private final MonetizationKillSwitch.Operation operation;

    public MonetizationSuspendedException(
            MonetizationKillSwitch.Operation operation
    ) {
        super("Monetization operation is temporarily suspended.");
        this.operation = operation;
    }

    public MonetizationKillSwitch.Operation operation() {
        return operation;
    }
}
