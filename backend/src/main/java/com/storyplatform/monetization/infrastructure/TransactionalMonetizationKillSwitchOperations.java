package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application
        .MonetizationKillSwitchOperations;
import com.storyplatform.monetization.domain.MonetizationKillSwitch;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

public class TransactionalMonetizationKillSwitchOperations
        implements MonetizationKillSwitchOperations {

    private final MonetizationKillSwitchOperations delegate;

    public TransactionalMonetizationKillSwitchOperations(
            MonetizationKillSwitchOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MonetizationKillSwitch> current() {
        return delegate.current();
    }

    @Override
    @Transactional
    public MonetizationKillSwitch update(
            String actorId,
            String reauthenticationToken,
            MonetizationKillSwitch.Operation operation,
            long expectedVersion,
            boolean engaged,
            String reason
    ) {
        return delegate.update(
                actorId,
                reauthenticationToken,
                operation,
                expectedVersion,
                engaged,
                reason
        );
    }
}
