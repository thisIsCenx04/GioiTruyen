package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.port.TopupQrPayloadFactory;
import com.storyplatform.monetization.application.port.TopupRequestRepository;
import com.storyplatform.monetization.domain.TopupRequest;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class TopupRequestService implements TopupRequestOperations {

    private static final String ROUTE = "/wallets/me/topups";
    private static final int RECENT_LIMIT = 50;
    private final TopupRequestRepository repository;
    private final TopupDiscountOperations discounts;
    private final TopupQrPayloadFactory qrPayloads;
    private final OutboxAppender outbox;
    private final Clock clock;
    private final Duration timeToLive;
    private final Supplier<UUID> ids;
    private final Supplier<String> references;

    public TopupRequestService(
            TopupRequestRepository repository,
            TopupDiscountOperations discounts,
            TopupQrPayloadFactory qrPayloads,
            OutboxAppender outbox,
            Clock clock,
            Duration timeToLive,
            Supplier<UUID> ids,
            Supplier<String> references
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.discounts = Objects.requireNonNull(discounts);
        this.qrPayloads = Objects.requireNonNull(qrPayloads);
        this.outbox = Objects.requireNonNull(outbox);
        this.clock = Objects.requireNonNull(clock);
        this.timeToLive = requireTtl(timeToLive);
        this.ids = Objects.requireNonNull(ids);
        this.references = Objects.requireNonNull(references);
    }

    @Override
    public TopupView create(
            String userId,
            String idempotencyKey,
            long amountVnd
    ) {
        String owner = requireUserId(userId);
        String keyHash = hash(owner + "\n" + ROUTE + "\n"
                + requireIdempotencyKey(idempotencyKey));
        String requestHash = hash(Long.toString(amountVnd));
        var replay = repository.findByIdempotencyKeyHash(keyHash);
        if (replay.isPresent()) {
            if (!MessageDigest.isEqual(
                    replay.get().requestHash().getBytes(StandardCharsets.US_ASCII),
                    requestHash.getBytes(StandardCharsets.US_ASCII)
            )) {
                throw conflict(
                        "Idempotency-Key was already used with another amount."
                );
            }
            return view(replay.get());
        }

        var discount = discounts.current();
        long creditedXu;
        try {
            creditedXu = TopupRequest.calculateCreditedXu(
                    amountVnd,
                    discount.discountPercent()
            );
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage());
        }
        Instant now = clock.instant();
        String reference = references.get();
        var request = new TopupRequest(
                ids.get().toString(),
                owner,
                amountVnd,
                creditedXu,
                discount.discountPercent(),
                discount.version(),
                reference,
                qrPayloads.create(amountVnd, reference),
                TopupRequest.Status.AWAITING_PAYMENT,
                now.plus(timeToLive),
                now,
                keyHash,
                requestHash
        );
        TopupRequest stored;
        try {
            stored = repository.insert(request);
        } catch (TopupRequestException exception) {
            if (exception.kind() != TopupRequestException.Kind.CONFLICT) {
                throw exception;
            }
            var concurrent = repository.findByIdempotencyKeyHash(keyHash);
            if (concurrent.isPresent()
                    && MessageDigest.isEqual(
                    concurrent.get().requestHash().getBytes(
                            StandardCharsets.US_ASCII
                    ),
                    requestHash.getBytes(StandardCharsets.US_ASCII)
            )) {
                return view(concurrent.get());
            }
            throw exception;
        }
        outbox.append(new IntegrationEvent(
                ids.get(),
                "monetization.topup.requested",
                1,
                now,
                stored.id(),
                "topup_request",
                stored.id(),
                owner,
                null,
                new TopupRequested(
                        amountVnd,
                        creditedXu,
                        discount.version(),
                        stored.expiresAt()
                )
        ));
        return view(stored);
    }

    @Override
    public TopupView get(String userId, String requestId) {
        return repository.findByIdAndUserId(
                requestId,
                requireUserId(userId)
        ).map(TopupRequestService::view).orElseThrow(() ->
                new TopupRequestException(
                        "Top-up request was not found.",
                        TopupRequestException.Kind.NOT_FOUND
                ));
    }

    @Override
    public List<TopupView> recent(String userId) {
        return repository.findRecentByUserId(
                requireUserId(userId),
                RECENT_LIMIT
        ).stream().map(TopupRequestService::view).toList();
    }

    private static TopupView view(TopupRequest value) {
        return new TopupView(
                value.id(),
                value.amountVnd(),
                value.creditedXu(),
                value.discountPercent(),
                value.discountVersion(),
                value.transferReference(),
                value.qrPayload(),
                value.status().name(),
                value.expiresAt(),
                value.createdAt()
        );
    }

    private static String requireUserId(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw invalid("Authenticated user id is invalid.");
        }
        return value;
    }

    private static String requireIdempotencyKey(String value) {
        if (value == null
                || !value.matches("[A-Za-z0-9._:-]{16,128}")) {
            throw invalid(
                    "Idempotency-Key must contain 16 to 128 safe characters."
            );
        }
        return value;
    }

    private static Duration requireTtl(Duration value) {
        if (value == null
                || value.compareTo(Duration.ofMinutes(5)) < 0
                || value.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException(
                    "Top-up request TTL must be 5 minutes to 24 hours."
            );
        }
        return value;
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static TopupRequestException invalid(String message) {
        return new TopupRequestException(
                message,
                TopupRequestException.Kind.INVALID
        );
    }

    private static TopupRequestException conflict(String message) {
        return new TopupRequestException(
                message,
                TopupRequestException.Kind.CONFLICT
        );
    }

    public record TopupRequested(
            long amountVnd,
            long creditedXu,
            long discountVersion,
            Instant expiresAt
    ) {
    }
}
