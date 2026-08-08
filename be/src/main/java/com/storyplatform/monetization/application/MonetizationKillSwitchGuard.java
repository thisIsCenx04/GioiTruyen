package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.port
        .MonetizationKillSwitchRepository;
import com.storyplatform.monetization.domain.MonetizationKillSwitch;

import java.util.Objects;

public final class MonetizationKillSwitchGuard {

    private final MonetizationKillSwitchRepository repository;

    public MonetizationKillSwitchGuard(
            MonetizationKillSwitchRepository repository
    ) {
        this.repository = Objects.requireNonNull(repository);
    }

    public boolean engaged(MonetizationKillSwitch.Operation operation) {
        return repository.find(operation)
                .map(MonetizationKillSwitch::engaged)
                .orElse(false);
    }

    public void requireOpen(MonetizationKillSwitch.Operation operation) {
        if (engaged(operation)) {
            throw new MonetizationSuspendedException(operation);
        }
    }
}
