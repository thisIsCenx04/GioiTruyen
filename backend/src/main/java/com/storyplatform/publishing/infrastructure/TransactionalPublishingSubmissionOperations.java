package com.storyplatform.publishing.infrastructure;

import com.storyplatform.publishing.application
        .PublishingSubmissionOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalPublishingSubmissionOperations
        implements PublishingSubmissionOperations {

    private final PublishingSubmissionOperations delegate;

    public TransactionalPublishingSubmissionOperations(
            PublishingSubmissionOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @Transactional
    public SubmissionView submit(
            String actorId,
            String teamId,
            String storyId,
            String idempotencyKey
    ) {
        return delegate.submit(
                actorId,
                teamId,
                storyId,
                idempotencyKey
        );
    }
}
