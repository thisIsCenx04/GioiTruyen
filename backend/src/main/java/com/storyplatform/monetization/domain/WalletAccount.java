package com.storyplatform.monetization.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WalletAccount(
        String id,
        OwnerType ownerType,
        String ownerId,
        LedgerEntry.Side normalSide,
        Status status,
        String currency,
        Instant createdAt
) {
    public WalletAccount {
        try {
            id = UUID.fromString(id).toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Wallet account id must be a UUID.",
                    exception
            );
        }
        Objects.requireNonNull(ownerType, "ownerType");
        Objects.requireNonNull(normalSide, "normalSide");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (ownerId == null || ownerId.isBlank() || ownerId.length() > 128) {
            throw new IllegalArgumentException("Wallet owner id is invalid.");
        }
        if (!"XU".equals(currency)) {
            throw new IllegalArgumentException(
                    "Wallet currency must be XU."
            );
        }
    }

    public enum OwnerType {
        USER,
        TEAM,
        PLATFORM
    }

    public enum Status {
        ACTIVE,
        FROZEN,
        CLOSED
    }
}
