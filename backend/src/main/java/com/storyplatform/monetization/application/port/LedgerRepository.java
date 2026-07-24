package com.storyplatform.monetization.application.port;

import com.storyplatform.monetization.domain.LedgerTransaction;

import java.util.Optional;

public interface LedgerRepository {

    Optional<LedgerTransaction> findByIdempotencyKeyHash(String keyHash);

    LedgerTransaction insert(LedgerTransaction transaction);
}
