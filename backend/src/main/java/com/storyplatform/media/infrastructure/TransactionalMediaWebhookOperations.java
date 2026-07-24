package com.storyplatform.media.infrastructure;

import com.storyplatform.media.application.MediaWebhookOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalMediaWebhookOperations
        implements MediaWebhookOperations {

    private final MediaWebhookOperations delegate;

    public TransactionalMediaWebhookOperations(
            MediaWebhookOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @Transactional
    public Result accept(
            byte[] body,
            String timestamp,
            String signature
    ) {
        return delegate.accept(body, timestamp, signature);
    }
}
