package com.storyplatform.monetization.application;

import java.math.BigInteger;

public record RewardRule(
        String version,
        long xuPerThousandValidViews,
        long teamCapXu
) {
    private static final BigInteger THOUSAND = BigInteger.valueOf(1_000);

    public RewardRule {
        if (version == null
                || !version.matches("[a-z0-9][a-z0-9._-]{2,63}")
                || xuPerThousandValidViews < 1 || teamCapXu < 1) {
            throw new IllegalArgumentException("Reward rule is invalid.");
        }
    }

    public Calculation calculate(long validViews) {
        if (validViews < 0) {
            throw new IllegalArgumentException(
                    "Valid views cannot be negative."
            );
        }
        BigInteger uncapped = BigInteger.valueOf(validViews)
                .multiply(BigInteger.valueOf(xuPerThousandValidViews))
                .divide(THOUSAND);
        BigInteger cap = BigInteger.valueOf(teamCapXu);
        boolean capped = uncapped.compareTo(cap) > 0;
        return new Calculation(
                (capped ? cap : uncapped).longValueExact(),
                capped
        );
    }

    public record Calculation(long amountXu, boolean capApplied) {
    }
}
