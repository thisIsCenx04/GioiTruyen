package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.port
        .WithdrawalPayoutCallbackDecoder;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutCallbackRepository;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutCallbackVerifier;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutGateway;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutRepository;
import com.storyplatform.monetization.application.port.WithdrawalRepository;
import com.storyplatform.monetization.domain.Withdrawal;
import com.storyplatform.monetization.domain.WithdrawalPayout;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class WithdrawalPayoutCallbackService
        implements WithdrawalPayoutCallbackOperations {

    private final WithdrawalPayoutCallbackVerifier verifier;
    private final WithdrawalPayoutCallbackDecoder decoder;
    private final WithdrawalPayoutCallbackRepository callbacks;
    private final WithdrawalRepository withdrawals;
    private final WithdrawalPayoutRepository payouts;
    private final WithdrawalPayoutCompletionOperations completions;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public WithdrawalPayoutCallbackService(
            WithdrawalPayoutCallbackVerifier verifier,
            WithdrawalPayoutCallbackDecoder decoder,
            WithdrawalPayoutCallbackRepository callbacks,
            WithdrawalRepository withdrawals,
            WithdrawalPayoutRepository payouts,
            WithdrawalPayoutCompletionOperations completions,
            Clock clock,
            Supplier<UUID> ids
    ) {
        this.verifier = Objects.requireNonNull(verifier);
        this.decoder = Objects.requireNonNull(decoder);
        this.callbacks = Objects.requireNonNull(callbacks);
        this.withdrawals = Objects.requireNonNull(withdrawals);
        this.payouts = Objects.requireNonNull(payouts);
        this.completions = Objects.requireNonNull(completions);
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
    }

    @Override
    public void accept(
            String provider,
            byte[] rawBody,
            String timestamp,
            String signature
    ) {
        if (rawBody == null || rawBody.length > 65_536) {
            throw invalid("Payout callback body is invalid.");
        }
        if (!verifier.verify(
                provider,
                rawBody,
                timestamp,
                signature
        )) {
            throw unauthorized();
        }
        var callback = decoder.decode(rawBody);
        String requestHash = hash(rawBody);
        var replay = callbacks.find(provider, callback.eventId());
        if (replay.isPresent()) {
            if (!MessageDigest.isEqual(
                    replay.orElseThrow().requestHash().getBytes(
                            StandardCharsets.US_ASCII
                    ),
                    requestHash.getBytes(StandardCharsets.US_ASCII)
            )) {
                throw conflict("Provider event ID was reused.");
            }
            return;
        }
        WithdrawalPayout payout = payouts.findByWithdrawalId(
                callback.withdrawalId()
        ).orElseThrow(() -> invalid("Payout was not found."));
        Withdrawal withdrawal = withdrawals.findById(
                callback.withdrawalId()
        ).orElseThrow(() -> invalid("Withdrawal was not found."));
        if (payout.state() != WithdrawalPayout.State.PROCESSING) {
            requireMatchingTerminal(payout, callback.status());
            store(provider, callback, requestHash);
            return;
        }
        if (withdrawal.state() != Withdrawal.State.PROCESSING) {
            throw conflict("Withdrawal payout state is inconsistent.");
        }
        store(provider, callback, requestHash);
        completions.complete(
                new WithdrawalPayoutClaimOperations.Claim(
                        withdrawal,
                        payout
                ),
                new WithdrawalPayoutGateway.Result(
                        callback.status(),
                        callback.providerReference(),
                        callback.errorCode()
                )
        );
    }

    private void store(
            String provider,
            WithdrawalPayoutCallbackDecoder.Callback callback,
            String requestHash
    ) {
        callbacks.insert(new WithdrawalPayoutCallbackRepository.Receipt(
                ids.get().toString(),
                provider,
                callback.eventId(),
                callback.withdrawalId(),
                requestHash,
                clock.instant()
        ));
    }

    private static void requireMatchingTerminal(
            WithdrawalPayout payout,
            WithdrawalPayoutGateway.Status callback
    ) {
        boolean matches = payout.state() == WithdrawalPayout.State.PAID
                && callback == WithdrawalPayoutGateway.Status.PAID
                || payout.state() == WithdrawalPayout.State.FAILED
                && callback == WithdrawalPayoutGateway.Status.FAILED;
        if (!matches) {
            throw conflict(
                    "Callback conflicts with terminal payout state."
            );
        }
    }

    private static String hash(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static WithdrawalException invalid(String message) {
        return new WithdrawalException(
                message,
                WithdrawalException.Kind.INVALID
        );
    }

    private static WithdrawalException unauthorized() {
        return new WithdrawalException(
                "Payout callback signature is invalid.",
                WithdrawalException.Kind.FORBIDDEN
        );
    }

    private static WithdrawalException conflict(String message) {
        return new WithdrawalException(
                message,
                WithdrawalException.Kind.CONFLICT
        );
    }
}
