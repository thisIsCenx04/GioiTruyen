package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.TopupSettlementOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalTopupSettlementOperations
        implements TopupSettlementOperations {

    private final TopupSettlementOperations delegate;

    public TransactionalTopupSettlementOperations(
            TopupSettlementOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    @Transactional
    public Result settle(String provider, String providerEventId) {
        return delegate.settle(provider, providerEventId);
    }
}
