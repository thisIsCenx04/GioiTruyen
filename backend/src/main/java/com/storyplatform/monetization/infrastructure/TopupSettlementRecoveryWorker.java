package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application
        .TopupSettlementOperations;
import com.storyplatform.monetization.application.port
        .TopupSettlementRepository;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

public final class TopupSettlementRecoveryWorker {

    private final TopupSettlementRepository repository;
    private final TopupSettlementOperations settlements;
    private final String provider;
    private final int batchSize;

    public TopupSettlementRecoveryWorker(
            TopupSettlementRepository repository,
            TopupSettlementOperations settlements,
            String provider,
            int batchSize
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.settlements = Objects.requireNonNull(settlements);
        if (provider == null
                || !provider.matches("[a-z0-9][a-z0-9-]{1,31}")
                || batchSize < 1
                || batchSize > 100) {
            throw new IllegalArgumentException(
                    "Top-up settlement recovery configuration is invalid."
            );
        }
        this.provider = provider;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString =
                    "${app.monetization.topup.recovery-poll-interval:30s}"
    )
    public void recover() {
        for (int index = 0; index < batchSize; index++) {
            var event = repository.findOldestReceived(provider);
            if (event.isEmpty()) {
                return;
            }
            var result = settlements.settle(
                    provider,
                    event.orElseThrow().providerEventId()
            );
            if (result == TopupSettlementOperations.Result.SUSPENDED) {
                return;
            }
        }
    }
}
