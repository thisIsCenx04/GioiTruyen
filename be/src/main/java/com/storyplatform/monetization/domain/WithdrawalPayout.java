package com.storyplatform.monetization.domain;

import java.time.Instant;
import java.util.UUID;

public record WithdrawalPayout(
        String withdrawalId,
        String provider,
        String providerIdempotencyKey,
        State state,
        int attempt,
        String providerReference,
        String lastErrorCode,
        Instant nextAttemptAt,
        Instant leaseUntil,
        Instant startedAt,
        Instant completedAt,
        String settlementTransactionId,
        String releaseTransactionId
) {
    public WithdrawalPayout {
        withdrawalId = uuid(withdrawalId, "Payout withdrawal id");
        if (provider == null
                || !provider.matches("[a-z0-9][a-z0-9-]{1,31}")
                || providerIdempotencyKey == null
                || !providerIdempotencyKey.matches(
                "[A-Za-z0-9._:-]{16,128}"
        )
                || state == null
                || attempt < 1
                || attempt > 10_000
                || startedAt == null
                || !validState(
                state,
                providerReference,
                lastErrorCode,
                nextAttemptAt,
                leaseUntil,
                completedAt,
                settlementTransactionId,
                releaseTransactionId
        )) {
            throw new IllegalArgumentException(
                    "Withdrawal payout is invalid."
            );
        }
        if (settlementTransactionId != null) {
            settlementTransactionId = uuid(
                    settlementTransactionId,
                    "Payout settlement transaction id"
            );
        }
        if (releaseTransactionId != null) {
            releaseTransactionId = uuid(
                    releaseTransactionId,
                    "Payout release transaction id"
            );
        }
    }

    public WithdrawalPayout retry(
            String reference,
            String errorCode,
            Instant nextAttempt
    ) {
        requireProcessing();
        return new WithdrawalPayout(
                withdrawalId,
                provider,
                providerIdempotencyKey,
                State.PROCESSING,
                attempt,
                safeReference(reference),
                safeError(errorCode),
                nextAttempt,
                null,
                startedAt,
                null,
                null,
                null
        );
    }

    public WithdrawalPayout paid(
            String reference,
            String settlementId,
            Instant at
    ) {
        requireProcessing();
        return new WithdrawalPayout(
                withdrawalId,
                provider,
                providerIdempotencyKey,
                State.PAID,
                attempt,
                requiredReference(reference),
                null,
                null,
                null,
                startedAt,
                at,
                settlementId,
                null
        );
    }

    public WithdrawalPayout failed(
            String reference,
            String errorCode,
            String releaseId,
            Instant at
    ) {
        requireProcessing();
        return new WithdrawalPayout(
                withdrawalId,
                provider,
                providerIdempotencyKey,
                State.FAILED,
                attempt,
                safeReference(reference),
                requiredError(errorCode),
                null,
                null,
                startedAt,
                at,
                null,
                releaseId
        );
    }

    private void requireProcessing() {
        if (state != State.PROCESSING) {
            throw new IllegalStateException(
                    "Withdrawal payout is already terminal."
            );
        }
    }

    private static boolean validState(
            State state,
            String reference,
            String error,
            Instant next,
            Instant lease,
            Instant completed,
            String settlement,
            String release
    ) {
        boolean safeReference = reference == null
                || reference.matches("[A-Za-z0-9][A-Za-z0-9._:-]{2,127}");
        boolean safeError = error == null
                || error.matches("[A-Z0-9_]{3,64}");
        if (!safeReference || !safeError) {
            return false;
        }
        return switch (state) {
            case PROCESSING -> completed == null
                    && settlement == null
                    && release == null
                    && (lease != null || next != null);
            case PAID -> reference != null
                    && completed != null
                    && settlement != null
                    && release == null
                    && next == null
                    && lease == null;
            case FAILED -> error != null
                    && completed != null
                    && settlement == null
                    && release != null
                    && next == null
                    && lease == null;
        };
    }

    private static String safeReference(String value) {
        return value == null ? null : requiredReference(value);
    }

    private static String requiredReference(String value) {
        if (value == null
                || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{2,127}")) {
            throw new IllegalArgumentException(
                    "Provider reference is invalid."
            );
        }
        return value;
    }

    private static String safeError(String value) {
        return value == null ? null : requiredError(value);
    }

    private static String requiredError(String value) {
        if (value == null || !value.matches("[A-Z0-9_]{3,64}")) {
            throw new IllegalArgumentException(
                    "Provider error code is invalid."
            );
        }
        return value;
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
        PROCESSING,
        PAID,
        FAILED
    }
}
