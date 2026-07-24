package com.storyplatform.moderation.infrastructure;

import com.storyplatform.moderation.application.ModerationAppealOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalModerationAppealOperations
        implements ModerationAppealOperations {

    private final ModerationAppealOperations delegate;

    public TransactionalModerationAppealOperations(
            ModerationAppealOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @Transactional
    public AppealView create(
            String actorId,
            String reviewId,
            String statement
    ) {
        return delegate.create(actorId, reviewId, statement);
    }

    @Override
    @Transactional
    public AppealView decide(
            String reviewerId,
            String reviewId,
            String appealId,
            AppealDecision decision,
            String reasonCode,
            String note
    ) {
        return delegate.decide(
                reviewerId,
                reviewId,
                appealId,
                decision,
                reasonCode,
                note
        );
    }
}
