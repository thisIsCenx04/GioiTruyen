package com.storyplatform.monetization.application;

import com.storyplatform.monetization.domain.Withdrawal;

public record WithdrawalFeePolicy(
        String version,
        long minimumGrossXu,
        long freeFromGrossXu,
        long flatFeeXu,
        long maximumGrossXu
) {
    public WithdrawalFeePolicy {
        if (version == null
                || !version.matches("[a-z0-9][a-z0-9._-]{2,63}")
                || minimumGrossXu < 1
                || flatFeeXu < 0
                || flatFeeXu >= minimumGrossXu
                || freeFromGrossXu <= minimumGrossXu
                || maximumGrossXu < freeFromGrossXu) {
            throw new IllegalArgumentException(
                    "Withdrawal fee policy is invalid."
            );
        }
    }

    public Quote quote(long grossAmountXu) {
        if (grossAmountXu < minimumGrossXu
                || grossAmountXu > maximumGrossXu) {
            throw new WithdrawalException(
                    "Withdrawal gross amount is outside policy boundaries.",
                    WithdrawalException.Kind.INVALID
            );
        }
        long fee = grossAmountXu < freeFromGrossXu ? flatFeeXu : 0;
        return new Quote(
                grossAmountXu,
                fee,
                Math.subtractExact(grossAmountXu, fee),
                version
        );
    }

    public static WithdrawalFeePolicy defaults() {
        return new WithdrawalFeePolicy(
                "withdrawal-fee-2026.1",
                Withdrawal.MINIMUM_GROSS_XU,
                1_000_000,
                20_000,
                Withdrawal.MAXIMUM_GROSS_XU
        );
    }

    public record Quote(
            long grossAmountXu,
            long feeXu,
            long netAmountXu,
            String version
    ) {
        public Quote {
            if (grossAmountXu < 1
                    || feeXu < 0
                    || feeXu > grossAmountXu
                    || netAmountXu < 1
                    || netAmountXu != grossAmountXu - feeXu
                    || version == null) {
                throw new IllegalArgumentException(
                        "Withdrawal fee quote is invalid."
                );
            }
        }
    }
}
