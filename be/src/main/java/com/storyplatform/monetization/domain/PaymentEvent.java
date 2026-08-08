package com.storyplatform.monetization.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record PaymentEvent(
        String id,
        String provider,
        String providerEventId,
        String bankReference,
        long amountVnd,
        String transferReference,
        Instant occurredAt,
        Instant receivedAt,
        String payloadHash,
        String rawPayload,
        Status status
) {
    private static final Pattern SAFE = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"
    );
    private static final Pattern REFERENCE = Pattern.compile(
            "GT[0-9A-Z]{12,24}"
    );

    public PaymentEvent {
        id = UUID.fromString(id).toString();
        requireSafe(provider, "provider");
        requireSafe(providerEventId, "providerEventId");
        requireSafe(bankReference, "bankReference");
        if (amountVnd <= 0 || amountVnd > 1_000_000_000) {
            throw new IllegalArgumentException(
                    "Payment amount is outside the supported range."
            );
        }
        if (transferReference == null
                || !REFERENCE.matcher(transferReference).matches()) {
            throw new IllegalArgumentException(
                    "Transfer reference is invalid."
            );
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
        if (occurredAt.isAfter(receivedAt.plusSeconds(300))) {
            throw new IllegalArgumentException(
                    "Payment occurrence time is in the future."
            );
        }
        if (payloadHash == null
                || !payloadHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Payload hash is invalid.");
        }
        if (rawPayload == null
                || rawPayload.isBlank()
                || rawPayload.length() > 16_384) {
            throw new IllegalArgumentException("Raw payload is invalid.");
        }
        Objects.requireNonNull(status, "status");
    }

    private static void requireSafe(String value, String field) {
        if (value == null || !SAFE.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is invalid.");
        }
    }

    public enum Status {
        RECEIVED,
        MATCHED,
        PENDING_REVIEW,
        REJECTED
    }
}
