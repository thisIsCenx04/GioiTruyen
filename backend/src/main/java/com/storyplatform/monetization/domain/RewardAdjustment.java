package com.storyplatform.monetization.domain;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

public record RewardAdjustment(
        String id,
        String settlementId,
        long correctedValidViews,
        long previousAmountXu,
        long correctedAmountXu,
        long deltaXu,
        String reasonCode,
        String idempotencyKeyHash,
        String ledgerTransactionId,
        Instant createdAt
) {
    private static final Pattern CODE =
            Pattern.compile("[A-Z][A-Z0-9_]{2,63}");
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    public RewardAdjustment {
        id = uuid(id, "Reward adjustment id");
        settlementId = uuid(settlementId, "Reward settlement id");
        ledgerTransactionId = uuid(
                ledgerTransactionId,
                "Ledger transaction id"
        );
        if (correctedValidViews < 0 || previousAmountXu < 0
                || correctedAmountXu < 0 || deltaXu == 0
                || correctedAmountXu - previousAmountXu != deltaXu
                || !CODE.matcher(reasonCode).matches()
                || !HASH.matcher(idempotencyKeyHash).matches()
                || createdAt == null) {
            throw new IllegalArgumentException(
                    "Reward adjustment is invalid."
            );
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
}
