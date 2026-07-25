package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.port
        .MonetizationReconciliationGateway;

import java.time.Instant;

public interface MonetizationReconciliationOperations {

    Summary reconcile(
            Instant from,
            Instant to,
            MonetizationReconciliationGateway.Statement statement
    );

    record Summary(
            String runId,
            int matched,
            int mismatched,
            boolean replayed
    ) {
    }
}
