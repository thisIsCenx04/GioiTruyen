package com.storyplatform.monetization.application;

import com.storyplatform.monetization.domain.Withdrawal;
import com.storyplatform.monetization.domain.WithdrawalPayout;

import java.util.Optional;

public interface WithdrawalPayoutClaimOperations {

    Optional<Claim> claim();

    record Claim(
            Withdrawal withdrawal,
            WithdrawalPayout payout
    ) {
    }
}
