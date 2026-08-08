package com.storyplatform.monetization.application.port;

import com.storyplatform.monetization.application
        .MonetizationReconciliationOperations;
import com.storyplatform.monetization.domain
        .MonetizationReconciliationCase;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MonetizationReconciliationRepository {

    Optional<Run> findRun(String provider, Instant from, Instant to);

    void insertRun(Run run);

    List<LocalEntry> findLocalEntries(
            String provider,
            Instant from,
            Instant to
    );

    void insertCase(MonetizationReconciliationCase mismatch);

    Optional<MonetizationReconciliationCase> findCase(String caseId);

    boolean hasPostedLedgerTransaction(String transactionId);

    boolean resolveCase(
            MonetizationReconciliationCase expected,
            MonetizationReconciliationCase resolved
    );

    void complete(
            String runId,
            MonetizationReconciliationOperations.Summary summary,
            Instant completedAt
    );

    record Run(
            String id,
            String provider,
            Instant from,
            Instant to,
            String statementHash,
            State state,
            MonetizationReconciliationOperations.Summary summary,
            Instant startedAt,
            Instant completedAt
    ) {
    }

    record LocalEntry(
            MonetizationReconciliationCase.SubjectType subjectType,
            String subjectId,
            String providerReference,
            long amount,
            MonetizationReconciliationGateway.Status status,
            String ledgerTransactionId,
            Instant occurredAt
    ) {
    }

    enum State {
        RUNNING,
        COMPLETED
    }
}
