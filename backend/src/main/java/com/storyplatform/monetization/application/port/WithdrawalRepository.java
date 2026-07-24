package com.storyplatform.monetization.application.port;

import com.storyplatform.monetization.domain.Withdrawal;

import java.util.List;
import java.util.Optional;

public interface WithdrawalRepository {

    Optional<Withdrawal> findByIdempotencyKeyHash(String keyHash);

    Withdrawal insert(Withdrawal withdrawal);

    List<Withdrawal> findByTeam(
            String teamId,
            WithdrawalCursorCodec.Position after,
            int limit
    );
}
