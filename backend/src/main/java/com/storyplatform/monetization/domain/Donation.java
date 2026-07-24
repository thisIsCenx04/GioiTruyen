package com.storyplatform.monetization.domain;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

public record Donation(
        String id,
        String donorId,
        String teamId,
        String donorAccountId,
        String teamAccountId,
        long amountXu,
        String message,
        String ledgerTransactionId,
        String idempotencyKeyHash,
        String requestHash,
        Status status,
        Instant createdAt
) {
    public static final long MAXIMUM_AMOUNT_XU = 1_000_000_000;
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    public Donation {
        id = uuid(id, "Donation id");
        donorAccountId = uuid(donorAccountId, "Donor account id");
        teamAccountId = uuid(teamAccountId, "Team account id");
        ledgerTransactionId = uuid(
                ledgerTransactionId,
                "Ledger transaction id"
        );
        donorId = text(donorId, 128, "donorId");
        teamId = text(teamId, 128, "teamId");
        if (amountXu < 1 || amountXu > MAXIMUM_AMOUNT_XU) {
            throw new IllegalArgumentException(
                    "Donation amount must be 1 to 1000000000 XU."
            );
        }
        if (message != null && message.length() > 500) {
            throw new IllegalArgumentException(
                    "Donation message cannot exceed 500 characters."
            );
        }
        hash(idempotencyKeyHash, "idempotencyKeyHash");
        hash(requestHash, "requestHash");
        if (status == null || createdAt == null) {
            throw new IllegalArgumentException(
                    "Donation status and creation time are required."
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

    private static String text(
            String value,
            int maximum,
            String field
    ) {
        if (value == null
                || value.isBlank()
                || value.length() > maximum) {
            throw new IllegalArgumentException(field + " is invalid.");
        }
        return value;
    }

    private static void hash(String value, String field) {
        if (value == null || !HASH.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is invalid.");
        }
    }

    public enum Status {
        POSTED
    }
}
