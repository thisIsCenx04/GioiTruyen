package com.storyplatform.monetization.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReferralAttribution(
        String id,
        String referrerId,
        String refereeId,
        String referralCodeHash,
        String ruleVersion,
        long rewardXu,
        State state,
        List<RiskSignal> riskSignals,
        String ledgerTransactionId,
        Instant attributedAt,
        Instant eligibleAt,
        Instant rewardedAt
) {
    public ReferralAttribution {
        id = uuid(id, "Referral attribution id");
        if (referrerId == null || referrerId.isBlank()
                || refereeId == null || refereeId.isBlank()
                || referrerId.equals(refereeId)
                || referralCodeHash == null
                || !referralCodeHash.matches("[0-9a-f]{64}")
                || ruleVersion == null
                || !ruleVersion.matches("[a-z0-9][a-z0-9._-]{2,63}")
                || rewardXu < 1 || state == null
                || riskSignals == null || attributedAt == null
                || eligibleAt == null || eligibleAt.isBefore(attributedAt)
                || (state == State.PENDING
                && (ledgerTransactionId != null || rewardedAt != null))
                || (state == State.REWARDED
                && (ledgerTransactionId == null || rewardedAt == null))) {
            throw new IllegalArgumentException(
                    "Referral attribution is invalid."
            );
        }
        riskSignals = List.copyOf(riskSignals);
        if (ledgerTransactionId != null) {
            ledgerTransactionId = uuid(
                    ledgerTransactionId,
                    "Ledger transaction id"
            );
        }
    }

    public ReferralAttribution rewarded(
            String transactionId,
            Instant at
    ) {
        if (state != State.PENDING || at.isBefore(eligibleAt)) {
            throw new IllegalStateException(
                    "Referral attribution is not rewardable."
            );
        }
        return new ReferralAttribution(
                id, referrerId, refereeId, referralCodeHash,
                ruleVersion, rewardXu, State.REWARDED, riskSignals,
                transactionId, attributedAt, eligibleAt, at
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
        REWARDED
    }

    public enum RiskSignal {
        NONE
    }
}
