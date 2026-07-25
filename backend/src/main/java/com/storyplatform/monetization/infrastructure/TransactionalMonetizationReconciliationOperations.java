package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application
        .MonetizationReconciliationOperations;
import com.storyplatform.monetization.application.port
        .MonetizationReconciliationGateway;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

public class TransactionalMonetizationReconciliationOperations
        implements MonetizationReconciliationOperations {

    private final MonetizationReconciliationOperations delegate;

    public TransactionalMonetizationReconciliationOperations(
            MonetizationReconciliationOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    @Transactional
    public Summary reconcile(
            Instant from,
            Instant to,
            MonetizationReconciliationGateway.Statement statement
    ) {
        return delegate.reconcile(from, to, statement);
    }
}
