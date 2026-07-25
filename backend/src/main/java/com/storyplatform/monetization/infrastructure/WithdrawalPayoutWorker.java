package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application
        .WithdrawalPayoutClaimOperations;
import com.storyplatform.monetization.application
        .WithdrawalPayoutCompletionOperations;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutGateway;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

public final class WithdrawalPayoutWorker {

    private final WithdrawalPayoutClaimOperations claims;
    private final WithdrawalPayoutCompletionOperations completions;
    private final WithdrawalPayoutGateway gateway;
    private final int batchSize;

    public WithdrawalPayoutWorker(
            WithdrawalPayoutClaimOperations claims,
            WithdrawalPayoutCompletionOperations completions,
            WithdrawalPayoutGateway gateway,
            int batchSize
    ) {
        this.claims = Objects.requireNonNull(claims);
        this.completions = Objects.requireNonNull(completions);
        this.gateway = Objects.requireNonNull(gateway);
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException(
                    "Withdrawal payout batch size must be 1 to 100."
            );
        }
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString =
                    "${app.monetization.withdrawals.payout.poll-interval:5s}"
    )
    public void process() {
        for (int index = 0; index < batchSize; index++) {
            var claim = claims.claim();
            if (claim.isEmpty()) {
                return;
            }
            var value = claim.orElseThrow();
            WithdrawalPayoutGateway.Result result;
            try {
                result = gateway.submit(
                        new WithdrawalPayoutGateway.Command(
                                value.withdrawal().id(),
                                value.payout().providerIdempotencyKey(),
                                value.withdrawal().netAmountXu(),
                                value.withdrawal().destination()
                                        .encryptedPayload()
                        )
                );
            } catch (RuntimeException exception) {
                completions.retry(value, "PROVIDER_UNAVAILABLE");
                continue;
            }
            completions.complete(value, result);
        }
    }
}
