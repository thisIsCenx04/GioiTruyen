package com.storyplatform.monetization.application.port;

import com.storyplatform.monetization.domain.Withdrawal;

import java.time.Instant;
import java.util.Optional;

public interface WithdrawalDestinationDirectory {

    Optional<Withdrawal.DestinationSnapshot> findEligible(
            String teamId,
            String destinationId,
            Instant now
    );
}
