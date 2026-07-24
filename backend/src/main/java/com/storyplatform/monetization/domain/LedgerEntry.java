package com.storyplatform.monetization.domain;

import java.util.Objects;
import java.util.UUID;

public record LedgerEntry(
        String accountId,
        Side side,
        long amountXu
) {
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
        if (amountXu <= 0) {
            throw new IllegalArgumentException(
                    "Ledger entry amount must be positive."
            );
        }
    }

    public LedgerEntry reverse() {
        return new LedgerEntry(accountId, side.opposite(), amountXu);
    }

    public enum Side {
        DEBIT,
        CREDIT;

        Side opposite() {
            return this == DEBIT ? CREDIT : DEBIT;
        }
    }
}
