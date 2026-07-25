package com.storyplatform.monetization.application.port;

import com.storyplatform.monetization.domain.WithdrawalPayout;

import java.time.Instant;
import java.util.Optional;

public interface WithdrawalPayoutRepository {

    WithdrawalPayout insert(WithdrawalPayout payout);

    Optional<WithdrawalPayout> findByWithdrawalId(String withdrawalId);

    Optional<WithdrawalPayout> claimRetryable(
            Instant now,
            Instant leaseUntil
    );

    boolean update(
            WithdrawalPayout expected,
            WithdrawalPayout replacement
    );
}
