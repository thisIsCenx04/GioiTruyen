package com.storyplatform.monetization.domain;

import java.time.Instant;
import java.util.UUID;

public record MonetizationReconciliationCase(
        String id,
        String runId,
        SubjectType subjectType,
        String subjectId,
        String providerReference,
        Mismatch mismatch,
        long localAmount,
        long providerAmount,
        String localStatus,
        String providerStatus,
        RecommendedAction recommendedAction,
        String evidenceHash,
        Status status,
        Instant createdAt,
        String resolvedBy,
        String resolutionReason,
        ResolutionAction resolutionAction,
        String compensationTransactionId,
        Instant resolvedAt
) {
    public MonetizationReconciliationCase {
        id = uuid(id, "Reconciliation case id");
        runId = uuid(runId, "Reconciliation run id");
        subjectId = optionalUuid(subjectId);
        if (subjectType == null
                || providerReference == null
                || !providerReference.matches(
                "[A-Za-z0-9][A-Za-z0-9._:-]{2,127}"
        )
                || mismatch == null
                || localAmount < 0
                || providerAmount < 0
                || !safeStatus(localStatus)
                || !safeStatus(providerStatus)
                || recommendedAction == null
                || evidenceHash == null
                || !evidenceHash.matches("[0-9a-f]{64}")
                || status == null
                || createdAt == null
                || !validResolution(
                status,
                resolvedBy,
                resolutionReason,
                resolutionAction,
                compensationTransactionId,
                resolvedAt
        )) {
            throw new IllegalArgumentException(
                    "Monetization reconciliation case is invalid."
            );
        }
        resolvedBy = optionalUuid(resolvedBy);
        compensationTransactionId = optionalUuid(
                compensationTransactionId
        );
    }

    public MonetizationReconciliationCase resolve(
            String actorId,
            String reason,
            ResolutionAction action,
            String compensationId,
            Instant at
    ) {
        if (status != Status.OPEN) {
            throw new IllegalStateException(
                    "Reconciliation case is already resolved."
            );
        }
        return new MonetizationReconciliationCase(
                id,
                runId,
                subjectType,
                subjectId,
                providerReference,
                mismatch,
                localAmount,
                providerAmount,
                localStatus,
                providerStatus,
                recommendedAction,
                evidenceHash,
                Status.RESOLVED,
                createdAt,
                actorId,
                reason,
                action,
                compensationId,
                at
        );
    }

    private static boolean validResolution(
            Status status,
            String actor,
            String reason,
            ResolutionAction action,
            String compensation,
            Instant at
    ) {
        if (status == Status.OPEN) {
            return actor == null
                    && reason == null
                    && action == null
                    && compensation == null
                    && at == null;
        }
        boolean resolved = actor != null
                && reason != null
                && !reason.isBlank()
                && reason.length() >= 10
                && reason.length() <= 500
                && action != null
                && at != null;
        return resolved
                && (action == ResolutionAction.COMPENSATING_LEDGER_POSTED
                ? compensation != null
                : compensation == null);
    }

    private static boolean safeStatus(String value) {
        return value == null || value.matches("[A-Z_]{3,32}");
    }

    private static String optionalUuid(String value) {
        return value == null ? null : uuid(value, "Reconciliation subject id");
    }

    private static String uuid(String value, String field) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    field + " must be a UUID.",
                    exception
            );
        }
    }

    public enum SubjectType {
        TOPUP,
        WITHDRAWAL
    }

    public enum Mismatch {
        MISSING_LOCAL_EVIDENCE,
        MISSING_PROVIDER_EVIDENCE,
        AMOUNT_MISMATCH,
        STATUS_MISMATCH,
        MISSING_LEDGER_POSTING
    }

    public enum RecommendedAction {
        INVESTIGATE_PROVIDER_EVENT,
        REPLAY_VERIFIED_EVENT,
        VERIFY_LEDGER_AND_COMPENSATE,
        CONTACT_PROVIDER
    }

    public enum Status {
        OPEN,
        RESOLVED
    }

    public enum ResolutionAction {
        PROVIDER_CORRECTED,
        COMPENSATING_LEDGER_POSTED,
        NO_FINANCIAL_CHANGE
    }
}
