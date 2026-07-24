package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.ManualTopupOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalManualTopupOperations
        implements ManualTopupOperations {

    private final ManualTopupOperations delegate;

    public TransactionalManualTopupOperations(
            ManualTopupOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    @Transactional
    public Approval approve(
            String actorId,
            String topupId,
            String reauthenticationToken,
            String reason,
            String evidenceReference
    ) {
        return delegate.approve(
                actorId,
                topupId,
                reauthenticationToken,
                reason,
                evidenceReference
        );
    }
}
