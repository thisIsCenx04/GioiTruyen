package com.storyplatform.monetization.application.port;

import com.storyplatform.monetization.domain
        .MonetizationReconciliationCase;

import java.time.Instant;
import java.util.List;

public interface MonetizationReconciliationGateway {

    Statement fetch(Instant from, Instant to);

    record Statement(
            String provider,
            List<Entry> entries
    ) {
        public Statement {
            entries = List.copyOf(entries);
        }
    }

    record Entry(
            MonetizationReconciliationCase.SubjectType subjectType,
            String providerReference,
            long amount,
            Status status,
            Instant occurredAt
    ) {
    }

    enum Status {
        PENDING,
        PAID,
        FAILED,
        REVERSED
    }
}
