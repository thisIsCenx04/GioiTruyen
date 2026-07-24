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
        Instant createdAt
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
                || state != State.PENDING_REVIEW
                || idempotencyKeyHash == null
                || !idempotencyKeyHash.matches("[0-9a-f]{64}")
                || requestHash == null
                || !requestHash.matches("[0-9a-f]{64}")
                || createdAt == null) {
            throw new IllegalArgumentException("Withdrawal is invalid.");
        }
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
        PENDING_REVIEW
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
