package com.storyplatform.monetization.domain;

import java.util.Objects;
import java.util.UUID;

public record LedgerEntry(
        String accountId,
        Side side,
        Bucket bucket,
        long amountXu
) {
    public LedgerEntry(String accountId, Side side, long amountXu) {
        this(accountId, side, Bucket.AVAILABLE, amountXu);
    }

    public LedgerEntry {
        try {
            accountId = UUID.fromString(accountId).toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Ledger account id must be a UUID.",
                    exception
            );
        }
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(bucket, "bucket");
        if (amountXu <= 0) {
            throw new IllegalArgumentException(
                    "Ledger entry amount must be positive."
            );
        }
    }

    public LedgerEntry reverse() {
        return new LedgerEntry(accountId, side.opposite(), bucket, amountXu);
    }

    public enum Side {
        DEBIT,
        CREDIT;

        Side opposite() {
            return this == DEBIT ? CREDIT : DEBIT;
        }
    }

    public enum Bucket {
        AVAILABLE,
        RESERVED
    }
}
