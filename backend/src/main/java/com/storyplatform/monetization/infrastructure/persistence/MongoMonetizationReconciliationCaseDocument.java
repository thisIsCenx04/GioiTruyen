package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.domain
        .MonetizationReconciliationCase;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection =
        MongoMonetizationReconciliationCaseDocument.COLLECTION)
public record MongoMonetizationReconciliationCaseDocument(
        @Id String id,
        String runId,
        String subjectType,
        String subjectId,
        String providerReference,
        String mismatch,
        long localAmount,
        long providerAmount,
        String localStatus,
        String providerStatus,
        String recommendedAction,
        String evidenceHash,
        String status,
        Instant createdAt,
        String resolvedBy,
        String resolutionReason,
        String resolutionAction,
        String compensationTransactionId,
        Instant resolvedAt
) {
    public static final String COLLECTION =
            "monetization_reconciliation_cases";

    static MongoMonetizationReconciliationCaseDocument from(
            MonetizationReconciliationCase value
    ) {
        return new MongoMonetizationReconciliationCaseDocument(
                value.id(),
                value.runId(),
                value.subjectType().name(),
                value.subjectId(),
                value.providerReference(),
                value.mismatch().name(),
                value.localAmount(),
                value.providerAmount(),
                value.localStatus(),
                value.providerStatus(),
                value.recommendedAction().name(),
                value.evidenceHash(),
                value.status().name(),
                value.createdAt(),
                value.resolvedBy(),
                value.resolutionReason(),
                value.resolutionAction() == null
                        ? null
                        : value.resolutionAction().name(),
                value.compensationTransactionId(),
                value.resolvedAt()
        );
    }

    MonetizationReconciliationCase toDomain() {
        return new MonetizationReconciliationCase(
                id,
                runId,
                MonetizationReconciliationCase.SubjectType.valueOf(
                        subjectType
                ),
                subjectId,
                providerReference,
                MonetizationReconciliationCase.Mismatch.valueOf(mismatch),
                localAmount,
                providerAmount,
                localStatus,
                providerStatus,
                MonetizationReconciliationCase.RecommendedAction.valueOf(
                        recommendedAction
                ),
                evidenceHash,
                MonetizationReconciliationCase.Status.valueOf(status),
                createdAt,
                resolvedBy,
                resolutionReason,
                resolutionAction == null
                        ? null
                        : MonetizationReconciliationCase.ResolutionAction
                                .valueOf(resolutionAction),
                compensationTransactionId,
                resolvedAt
        );
    }
}
