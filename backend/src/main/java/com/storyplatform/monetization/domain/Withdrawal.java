package com.storyplatform.monetization.domain;

import java.time.Instant;
import java.util.UUID;

public record Withdrawal(
        String id,
        String teamId,
        String accountId,
        long grossAmountXu,
        long feeXu,
        long netAmountXu,
        String feeRuleVersion,
        DestinationSnapshot destination,
        State state,
        String requestedBy,
        String reserveTransactionId,
        String idempotencyKeyHash,
        String requestHash,
        Instant createdAt,
        String reviewedBy,
        String reviewReason,
        String reviewRiskLevel,
        String reviewRiskRuleVersion,
        String reviewKeyHash,
        String reviewRequestHash,
        String releaseTransactionId,
        Instant reviewedAt
) {
    public static final long MINIMUM_GROSS_XU = 100_000;
    public static final long MAXIMUM_GROSS_XU = 1_000_000_000;

    public Withdrawal {
        id = uuid(id, "Withdrawal id");
        teamId = uuid(teamId, "Withdrawal team id");
        accountId = uuid(accountId, "Withdrawal account id");
        requestedBy = uuid(requestedBy, "Withdrawal requester id");
        reserveTransactionId = uuid(
                reserveTransactionId,
                "Withdrawal reserve transaction id"
        );
        if (grossAmountXu < MINIMUM_GROSS_XU
                || grossAmountXu > MAXIMUM_GROSS_XU
                || feeXu < 0
                || feeXu > grossAmountXu
                || netAmountXu < 1
                || netAmountXu != grossAmountXu - feeXu
                || feeRuleVersion == null
                || !feeRuleVersion.matches("[a-z0-9][a-z0-9._-]{2,63}")
                || destination == null
                || state == null
                || idempotencyKeyHash == null
                || !idempotencyKeyHash.matches("[0-9a-f]{64}")
                || requestHash == null
                || !requestHash.matches("[0-9a-f]{64}")
                || createdAt == null
                || !validReviewState(
                state,
                reviewedBy,
                reviewReason,
                reviewRiskLevel,
                reviewRiskRuleVersion,
                reviewKeyHash,
                reviewRequestHash,
                releaseTransactionId,
                reviewedAt
        )) {
            throw new IllegalArgumentException("Withdrawal is invalid.");
        }
        if (reviewedBy != null) {
            reviewedBy = uuid(reviewedBy, "Withdrawal reviewer id");
        }
        if (releaseTransactionId != null) {
            releaseTransactionId = uuid(
                    releaseTransactionId,
                    "Withdrawal release transaction id"
            );
        }
    }

    public Withdrawal approved(
            String reviewerId,
            String reason,
            String riskLevel,
            String riskRuleVersion,
            String keyHash,
            String reviewHash,
            Instant at
    ) {
        requirePending(at);
        return reviewed(
                State.APPROVED,
                reviewerId,
                reason,
                riskLevel,
                riskRuleVersion,
                keyHash,
                reviewHash,
                null,
                at
        );
    }

    public Withdrawal rejected(
            String reviewerId,
            String reason,
            String riskLevel,
            String riskRuleVersion,
            String keyHash,
            String reviewHash,
            String releaseId,
            Instant at
    ) {
        requirePending(at);
        return reviewed(
                State.REJECTED,
                reviewerId,
                reason,
                riskLevel,
                riskRuleVersion,
                keyHash,
                reviewHash,
                releaseId,
                at
        );
    }

    private Withdrawal reviewed(
            State newState,
            String reviewerId,
            String reason,
            String riskLevel,
            String riskRuleVersion,
            String keyHash,
            String reviewHash,
            String releaseId,
            Instant at
    ) {
        return new Withdrawal(
                id, teamId, accountId, grossAmountXu, feeXu,
                netAmountXu, feeRuleVersion, destination, newState,
                requestedBy, reserveTransactionId, idempotencyKeyHash,
                requestHash, createdAt, reviewerId, reason, riskLevel,
                riskRuleVersion, keyHash, reviewHash, releaseId, at
        );
    }

    private void requirePending(Instant at) {
        if (state != State.PENDING_REVIEW
                || at == null
                || at.isBefore(createdAt)) {
            throw new IllegalStateException(
                    "Withdrawal is not reviewable."
            );
        }
    }

    private static boolean validReviewState(
            State value,
            String reviewer,
            String reason,
            String riskLevel,
            String riskRuleVersion,
            String keyHash,
            String requestHash,
            String releaseId,
            Instant at
    ) {
        boolean decision = reviewer != null
                && reason != null
                && !reason.isBlank()
                && reason.length() <= 500
                && riskLevel != null
                && riskLevel.matches("[A-Z_]{3,32}")
                && riskRuleVersion != null
                && riskRuleVersion.matches("[a-z0-9][a-z0-9._-]{2,63}")
                && keyHash != null
                && keyHash.matches("[0-9a-f]{64}")
                && requestHash != null
                && requestHash.matches("[0-9a-f]{64}")
                && at != null;
        return switch (value) {
            case PENDING_REVIEW -> !decision
                    && reviewer == null
                    && reason == null
                    && riskLevel == null
                    && riskRuleVersion == null
                    && keyHash == null
                    && requestHash == null
                    && releaseId == null
                    && at == null;
            case APPROVED -> decision && releaseId == null;
            case REJECTED -> decision && releaseId != null;
        };
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

    public enum State {
        PENDING_REVIEW,
        APPROVED,
        REJECTED
    }

    public record DestinationSnapshot(
            String id,
            String maskedLabel,
            String encryptedPayload,
            long version,
            Instant verifiedAt
    ) {
        public DestinationSnapshot {
            id = uuid(id, "Withdrawal destination id");
            if (maskedLabel == null || maskedLabel.isBlank()
                    || maskedLabel.length() > 128
                    || encryptedPayload == null
                    || encryptedPayload.isBlank()
                    || encryptedPayload.length() > 4096
                    || version < 1
                    || verifiedAt == null) {
                throw new IllegalArgumentException(
                        "Withdrawal destination snapshot is invalid."
                );
            }
        }
    }
}
