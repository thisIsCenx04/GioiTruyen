package com.storyplatform.monetization.application.port;

import com.storyplatform.monetization.domain.Withdrawal;

import java.util.List;
import java.util.Optional;

public interface WithdrawalRepository {

    Optional<Withdrawal> findByIdempotencyKeyHash(String keyHash);

    Optional<Withdrawal> findById(String withdrawalId);

    Optional<Withdrawal> findByReviewKeyHash(String keyHash);

    Withdrawal insert(Withdrawal withdrawal);

    boolean decide(Withdrawal decision);

    List<Withdrawal> findByTeam(
            String teamId,
            WithdrawalCursorCodec.Position after,
            int limit
    );
}
