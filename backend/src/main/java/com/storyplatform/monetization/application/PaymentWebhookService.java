package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.port.PaymentEventDecoder;
import com.storyplatform.monetization.application.port.PaymentEventRepository;
import com.storyplatform.monetization.application.port.PaymentWebhookVerifier;
import com.storyplatform.monetization.domain.PaymentEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class PaymentWebhookService
        implements PaymentWebhookOperations {

    private final PaymentWebhookVerifier verifier;
    private final PaymentEventDecoder decoder;
    private final PaymentEventRepository repository;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public PaymentWebhookService(
            PaymentWebhookVerifier verifier,
            PaymentEventDecoder decoder,
            PaymentEventRepository repository,
            Clock clock,
            Supplier<UUID> ids
    ) {
        this.verifier = Objects.requireNonNull(verifier);
        this.decoder = Objects.requireNonNull(decoder);
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
    }

    @Override
    public Result accept(
            String provider,
            byte[] rawBody,
            String timestamp,
            String signature
    ) {
        if (rawBody == null
                || rawBody.length == 0
                || rawBody.length > 16_384) {
            throw invalid("Payment webhook body is invalid.");
        }
        if (!verifier.verify(
                provider,
                rawBody,
                timestamp,
                signature
        )) {
            throw unauthorized(
                    "Payment webhook signature or timestamp is invalid."
            );
        }
        var decoded = decoder.decode(rawBody);
        PaymentEvent event;
        try {
            event = new PaymentEvent(
                    ids.get().toString(),
                    provider,
                    decoded.eventId(),
                    decoded.bankReference(),
                    decoded.amountVnd(),
                    decoded.transferReference(),
                    decoded.occurredAt(),
                    clock.instant(),
                    hash(rawBody),
                    new String(rawBody, StandardCharsets.UTF_8),
                    PaymentEvent.Status.RECEIVED
            );
        } catch (IllegalArgumentException exception) {
            throw invalid("Payment webhook payload is invalid.");
        }
        return repository.insertIfAbsent(event)
                ? Result.ACCEPTED
                : Result.DUPLICATE;
    }

    private static String hash(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static PaymentWebhookException invalid(String message) {
        return new PaymentWebhookException(
                message,
                PaymentWebhookException.Kind.INVALID
        );
    }

    private static PaymentWebhookException unauthorized(String message) {
        return new PaymentWebhookException(
                message,
                PaymentWebhookException.Kind.UNAUTHORIZED
        );
    }
}
