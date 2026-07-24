package com.storyplatform.moderation.infrastructure;

import com.storyplatform.moderation.application
        .ModerationDecisionOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalModerationDecisionOperations
        implements ModerationDecisionOperations {

    private final ModerationDecisionOperations delegate;

    public TransactionalModerationDecisionOperations(
            ModerationDecisionOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @Transactional
    public DecisionView decide(
            String reviewerId,
            String reviewId,
            long expectedVersion,
            DecisionCommand command
    ) {
        return delegate.decide(
                reviewerId,
                reviewId,
                expectedVersion,
                command
        );
    }
}
