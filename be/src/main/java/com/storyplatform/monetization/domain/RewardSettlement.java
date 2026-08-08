package com.storyplatform.monetization.domain;

import java.time.Instant;
import java.util.UUID;

public record RewardSettlement(
        String id,
        String periodId,
        String teamId,
        String teamAccountId,
        long validViews,
        long amountXu,
        boolean capApplied,
        String ruleVersion,
        String aggregateVersion,
        String ledgerTransactionId,
        State state,
        Instant createdAt,
        Instant postedAt
) {
    public RewardSettlement {
        id = uuid(id, "Reward settlement id");
        teamAccountId = uuid(teamAccountId, "Team account id");
        if (periodId == null || periodId.isBlank()
                || teamId == null || teamId.isBlank()
                || validViews < 0 || amountXu < 0
                || ruleVersion == null || ruleVersion.isBlank()
                || aggregateVersion == null || aggregateVersion.isBlank()
                || state == null || createdAt == null
                || (state == State.PENDING
                && (ledgerTransactionId != null || postedAt != null))
                || (state == State.POSTED
                && (ledgerTransactionId == null || postedAt == null))
                || (state == State.NO_REWARD
                && (amountXu != 0 || ledgerTransactionId != null
                || postedAt == null))) {
            throw new IllegalArgumentException(
                    "Reward settlement is invalid."
            );
        }
        if (ledgerTransactionId != null) {
            ledgerTransactionId = uuid(
                    ledgerTransactionId,
                    "Ledger transaction id"
            );
        }
    }

    public RewardSettlement posted(String transactionId, Instant at) {
        if (state != State.PENDING || amountXu == 0) {
            throw new IllegalStateException(
                    "Reward settlement is not postable."
            );
        }
        return new RewardSettlement(
                id, periodId, teamId, teamAccountId, validViews,
                amountXu, capApplied, ruleVersion, aggregateVersion,
                transactionId, State.POSTED, createdAt, at
        );
    }

    public RewardSettlement noReward(Instant at) {
        if (state != State.PENDING || amountXu != 0) {
            throw new IllegalStateException(
                    "Only a zero reward can be closed without posting."
            );
        }
        return new RewardSettlement(
                id, periodId, teamId, teamAccountId, validViews,
                amountXu, capApplied, ruleVersion, aggregateVersion,
                null, State.NO_REWARD, createdAt, at
        );
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
        PENDING,
        POSTED,
        NO_REWARD
    }
}
