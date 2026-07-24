package com.storyplatform.publishing.infrastructure;

import com.storyplatform.publishing.application.PublishingDueOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalPublishingDueOperations
        implements PublishingDueOperations {

    private final PublishingDueOperations delegate;

    public TransactionalPublishingDueOperations(
            PublishingDueOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @Transactional
    public boolean processNext(String workerId) {
        return delegate.processNext(workerId);
    }
}
