package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.TopupRequestOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

public class TransactionalTopupRequestOperations
        implements TopupRequestOperations {

    private final TopupRequestOperations delegate;

    public TransactionalTopupRequestOperations(
            TopupRequestOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    @Transactional
    public TopupView create(
            String userId,
            String idempotencyKey,
            long amountVnd
    ) {
        return delegate.create(userId, idempotencyKey, amountVnd);
    }

    @Override
    @Transactional(readOnly = true)
    public TopupView get(String userId, String requestId) {
        return delegate.get(userId, requestId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopupView> recent(String userId) {
        return delegate.recent(userId);
    }
}
