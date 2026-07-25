package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.application
        .MonetizationReconciliationOperations;
import com.storyplatform.monetization.application.port
        .MonetizationReconciliationRepository;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection =
        MongoMonetizationReconciliationRunDocument.COLLECTION)
public record MongoMonetizationReconciliationRunDocument(
        @Id String id,
        String provider,
        Instant from,
        Instant to,
        String statementHash,
        String state,
        SummaryDocument summary,
        Instant startedAt,
        Instant completedAt
) {
    public static final String COLLECTION =
            "monetization_reconciliation_runs";

    static MongoMonetizationReconciliationRunDocument from(
            MonetizationReconciliationRepository.Run value
    ) {
        return new MongoMonetizationReconciliationRunDocument(
                value.id(),
                value.provider(),
                value.from(),
                value.to(),
                value.statementHash(),
                value.state().name(),
                value.summary() == null
                        ? null
                        : SummaryDocument.from(value.summary()),
                value.startedAt(),
                value.completedAt()
        );
    }

    MonetizationReconciliationRepository.Run toDomain() {
        return new MonetizationReconciliationRepository.Run(
                id,
                provider,
                from,
                to,
                statementHash,
                MonetizationReconciliationRepository.State.valueOf(state),
                summary == null ? null : summary.toDomain(),
                startedAt,
                completedAt
        );
    }

    public record SummaryDocument(
            String runId,
            int matched,
            int mismatched,
            boolean replayed
    ) {
        static SummaryDocument from(
                MonetizationReconciliationOperations.Summary value
        ) {
            return new SummaryDocument(
                    value.runId(),
                    value.matched(),
                    value.mismatched(),
                    value.replayed()
            );
        }

        MonetizationReconciliationOperations.Summary toDomain() {
            return new MonetizationReconciliationOperations.Summary(
                    runId,
                    matched,
                    mismatched,
                    replayed
            );
        }
    }
}
