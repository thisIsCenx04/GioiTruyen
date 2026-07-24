package com.storyplatform.monetization.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record LedgerTransaction(
        String id,
        Type type,
        String referenceType,
        String referenceId,
        State state,
        List<LedgerEntry> entries,
        String idempotencyKeyHash,
        String compensatesTransactionId,
        Instant createdAt
) {
    private static final Pattern SAFE_REFERENCE = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"
    );
    private static final Pattern KEY_HASH = Pattern.compile("[a-f0-9]{64}");

    public LedgerTransaction {
        id = uuid(id, "Ledger transaction id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
        requireReference(referenceType, "referenceType");
        requireReference(referenceId, "referenceId");
        if (entries == null || entries.size() < 2 || entries.size() > 20) {
            throw new IllegalArgumentException(
                    "A ledger transaction requires 2 to 20 entries."
            );
        }
        entries = List.copyOf(entries);
        if (new HashSet<>(entries.stream()
                .map(LedgerEntry::accountId)
                .toList()).size() != entries.size()) {
            throw new IllegalArgumentException(
                    "A ledger account may appear only once per transaction."
            );
        }
        long debits = sum(entries, LedgerEntry.Side.DEBIT);
        long credits = sum(entries, LedgerEntry.Side.CREDIT);
        if (debits != credits) {
            throw new IllegalArgumentException(
                    "Ledger debit and credit totals must be equal."
            );
        }
        if (idempotencyKeyHash == null
                || !KEY_HASH.matcher(idempotencyKeyHash).matches()) {
            throw new IllegalArgumentException(
                    "Idempotency key hash must be lowercase SHA-256."
            );
        }
        if (type == Type.REVERSAL && compensatesTransactionId == null) {
            throw new IllegalArgumentException(
                    "A reversal must reference the original transaction."
            );
        }
        if (type != Type.REVERSAL && compensatesTransactionId != null) {
            throw new IllegalArgumentException(
                    "Only a reversal may compensate another transaction."
            );
        }
        if (compensatesTransactionId != null) {
            compensatesTransactionId = uuid(
                    compensatesTransactionId,
                    "Compensated transaction id"
            );
            if (id.equals(compensatesTransactionId)) {
                throw new IllegalArgumentException(
                        "A transaction cannot compensate itself."
                );
            }
        }
    }

    public static LedgerTransaction post(
            String id,
            Type type,
            String referenceType,
            String referenceId,
            List<LedgerEntry> entries,
            String idempotencyKeyHash,
            Instant createdAt
    ) {
        return new LedgerTransaction(
                id,
                type,
                referenceType,
                referenceId,
                State.POSTED,
                entries,
                idempotencyKeyHash,
                null,
                createdAt
        );
    }

    public static LedgerTransaction reverse(
            String id,
            LedgerTransaction original,
            String referenceType,
            String referenceId,
            String idempotencyKeyHash,
            Instant createdAt
    ) {
        Objects.requireNonNull(original, "original");
        return new LedgerTransaction(
                id,
                Type.REVERSAL,
                referenceType,
                referenceId,
                State.POSTED,
                original.entries().stream()
                        .map(LedgerEntry::reverse)
                        .toList(),
                idempotencyKeyHash,
                original.id(),
                createdAt
        );
    }

    public long amountXu() {
        return sum(entries, LedgerEntry.Side.DEBIT);
    }

    private static long sum(
            List<LedgerEntry> entries,
            LedgerEntry.Side side
    ) {
        try {
            return entries.stream()
                    .filter(entry -> entry.side() == side)
                    .mapToLong(LedgerEntry::amountXu)
                    .reduce(0, Math::addExact);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Ledger total exceeds the supported integer range.",
                    exception
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

    private static void requireReference(String value, String field) {
        if (value == null || !SAFE_REFERENCE.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    field + " has an invalid format."
            );
        }
    }

    public enum State {
        POSTED
    }

    public enum Type {
        TOPUP,
        DONATION,
        REWARD,
        WITHDRAWAL_RESERVE,
        WITHDRAWAL_SETTLEMENT,
        ADJUSTMENT,
        REVERSAL
    }
}
