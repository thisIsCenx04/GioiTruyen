package com.storyplatform.monetization.domain;

import java.time.Instant;
import java.util.UUID;

public record MonetizationKillSwitch(
        Operation operation,
        boolean engaged,
        long version,
        String changedBy,
        Instant changedAt
) {
    public MonetizationKillSwitch {
        if (operation == null
                || version < 0
                || changedBy == null
                || changedAt == null) {
            throw new IllegalArgumentException(
                    "Monetization kill switch is invalid."
            );
        }
        if (!"system-default".equals(changedBy)) {
            try {
                changedBy = UUID.fromString(changedBy).toString();
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException(
                        "Kill switch actor must be a UUID.",
                        exception
                );
            }
        }
    }

    public enum Operation {
        TOPUP_CREDIT,
        WITHDRAWAL_REQUEST,
        WITHDRAWAL_PAYOUT
    }
}
