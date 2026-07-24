package com.storyplatform.monetization.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record TopupRequest(
        String id,
        String userId,
        long amountVnd,
        long creditedXu,
        BigDecimal discountPercent,
        long discountVersion,
        String transferReference,
        String qrPayload,
        Status status,
        Instant expiresAt,
        Instant createdAt,
        String idempotencyKeyHash,
        String requestHash
) {
    public static final long MINIMUM_AMOUNT_VND = 10_000;
    public static final long MAXIMUM_AMOUNT_VND = 1_000_000_000;
    private static final Pattern REFERENCE = Pattern.compile(
            "GT[0-9A-Z]{12,24}"
    );
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    public TopupRequest {
        id = requireUuid(id);
        userId = requireText(userId, 128, "userId");
        validateAmount(amountVnd);
        if (creditedXu != calculateCreditedXu(
                amountVnd,
                discountPercent
        )) {
            throw new IllegalArgumentException(
                    "Credited XU does not match the discount snapshot."
            );
        }
        if (discountVersion < 0) {
            throw new IllegalArgumentException(
                    "Discount version cannot be negative."
            );
        }
        if (transferReference == null
                || !REFERENCE.matcher(transferReference).matches()) {
            throw new IllegalArgumentException(
                    "Transfer reference has an invalid format."
            );
        }
        qrPayload = requireText(qrPayload, 1024, "qrPayload");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException(
                    "Top-up expiry must be after creation."
            );
        }
        requireHash(idempotencyKeyHash, "idempotencyKeyHash");
        requireHash(requestHash, "requestHash");
    }

    public static long calculateCreditedXu(
            long amountVnd,
            BigDecimal discountPercent
    ) {
        validateAmount(amountVnd);
        BigDecimal discount = normalizeDiscount(discountPercent);
        return BigDecimal.valueOf(amountVnd)
                .multiply(BigDecimal.valueOf(100).subtract(discount))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR)
                .longValueExact();
    }

    private static void validateAmount(long amountVnd) {
        if (amountVnd < MINIMUM_AMOUNT_VND
                || amountVnd > MAXIMUM_AMOUNT_VND) {
            throw new IllegalArgumentException(
                    "Amount must be between 10000 and 1000000000 VND."
            );
        }
    }

    private static BigDecimal normalizeDiscount(BigDecimal value) {
        if (value == null
                || value.scale() > 2
                || value.compareTo(BigDecimal.ZERO) < 0
                || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(
                    "Discount percent must be between 0 and 100."
            );
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String requireUuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Top-up request id must be a UUID.",
                    exception
            );
        }
    }

    private static String requireText(
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

    private static void requireHash(String value, String field) {
        if (value == null || !HASH.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    field + " must be a lowercase SHA-256 hash."
            );
        }
    }

    public enum Status {
        AWAITING_PAYMENT
    }
}
