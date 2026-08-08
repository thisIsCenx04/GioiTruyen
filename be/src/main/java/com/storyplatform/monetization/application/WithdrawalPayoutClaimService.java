package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.port
        .WithdrawalPayoutRepository;
import com.storyplatform.monetization.application.port.WithdrawalRepository;
import com.storyplatform.monetization.domain.Withdrawal;
import com.storyplatform.monetization.domain.WithdrawalPayout;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class WithdrawalPayoutClaimService
        implements WithdrawalPayoutClaimOperations {

    private final WithdrawalRepository withdrawals;
    private final WithdrawalPayoutRepository payouts;
    private final String provider;
    private final Duration leaseDuration;
    private final Clock clock;

    public WithdrawalPayoutClaimService(
            WithdrawalRepository withdrawals,
            WithdrawalPayoutRepository payouts,
            String provider,
            Duration leaseDuration,
            Clock clock
    ) {
        this.withdrawals = Objects.requireNonNull(withdrawals);
        this.payouts = Objects.requireNonNull(payouts);
        if (provider == null
                || !provider.matches("[a-z0-9][a-z0-9-]{1,31}")
                || leaseDuration == null
                || leaseDuration.isNegative()
                || leaseDuration.isZero()) {
            throw new IllegalArgumentException(
                    "Withdrawal payout claim configuration is invalid."
            );
        }
        this.provider = provider;
        this.leaseDuration = leaseDuration;
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Optional<Claim> claim() {
        Instant now = clock.instant();
        var retry = payouts.claimRetryable(
                now,
                now.plus(leaseDuration)
        );
        if (retry.isPresent()) {
            WithdrawalPayout payout = retry.orElseThrow();
            Withdrawal withdrawal = withdrawals.findById(
                    payout.withdrawalId()
            ).filter(value ->
                    value.state() == Withdrawal.State.PROCESSING
            ).orElseThrow(() -> new IllegalStateException(
                    "Retryable payout has no processing withdrawal."
            ));
            return Optional.of(new Claim(withdrawal, payout));
        }
        var approved = withdrawals.findOldestApproved();
        if (approved.isEmpty()) {
            return Optional.empty();
        }
        Withdrawal withdrawal = approved.orElseThrow();
        if (!withdrawals.transition(
                withdrawal.id(),
                Withdrawal.State.APPROVED,
                Withdrawal.State.PROCESSING,
                null
        )) {
            throw conflict();
        }
        WithdrawalPayout payout = payouts.insert(new WithdrawalPayout(
                withdrawal.id(),
                provider,
                "withdrawal:" + withdrawal.id(),
                WithdrawalPayout.State.PROCESSING,
                1,
                null,
                null,
                null,
                now.plus(leaseDuration),
                now,
                null,
                null,
                null
        ));
        return Optional.of(new Claim(withdrawal.processing(), payout));
    }

    private static WithdrawalException conflict() {
        return new WithdrawalException(
                "Withdrawal payout claim changed concurrently.",
                WithdrawalException.Kind.CONFLICT
        );
    }
}
