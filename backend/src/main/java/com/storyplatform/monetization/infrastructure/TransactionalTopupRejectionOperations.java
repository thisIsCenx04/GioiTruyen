package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.TopupRejectionOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalTopupRejectionOperations
        implements TopupRejectionOperations {

    private final TopupRejectionOperations delegate;

    public TransactionalTopupRejectionOperations(
            TopupRejectionOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    @Transactional
    public Rejection reject(
            String actorId,
            String topupId,
            ReasonCode reasonCode,
            String reason,
            String evidenceReference
    ) {
        return delegate.reject(
                actorId,
                topupId,
                reasonCode,
                reason,
                evidenceReference
        );
    }
}
