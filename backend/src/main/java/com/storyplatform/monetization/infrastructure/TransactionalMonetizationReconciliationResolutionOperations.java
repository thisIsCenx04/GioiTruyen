package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application
        .MonetizationReconciliationResolutionOperations;
import com.storyplatform.monetization.domain
        .MonetizationReconciliationCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalMonetizationReconciliationResolutionOperations
        implements MonetizationReconciliationResolutionOperations {

    private final MonetizationReconciliationResolutionOperations delegate;

    public TransactionalMonetizationReconciliationResolutionOperations(
            MonetizationReconciliationResolutionOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    @Transactional
    public MonetizationReconciliationCase resolve(
            String caseId,
            String actorId,
            String reason,
            MonetizationReconciliationCase.ResolutionAction action,
            String compensationTransactionId
    ) {
        return delegate.resolve(
                caseId,
                actorId,
                reason,
                action,
                compensationTransactionId
        );
    }
}
