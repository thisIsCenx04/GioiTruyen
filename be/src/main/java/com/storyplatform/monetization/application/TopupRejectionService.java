package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.port.TopupRejectionRepository;
import com.storyplatform.monetization.domain.PaymentEvent;
import com.storyplatform.monetization.domain.TopupRequest;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class TopupRejectionService
        implements TopupRejectionOperations {

    private final TopupRejectionRepository repository;
    private final OutboxAppender outbox;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public TopupRejectionService(
            TopupRejectionRepository repository,
            OutboxAppender outbox,
            Clock clock,
            Supplier<UUID> ids
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.outbox = Objects.requireNonNull(outbox);
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
    }

    @Override
    public Rejection reject(
            String actorId,
            String topupId,
            ReasonCode reasonCode,
            String reason,
            String evidenceReference
    ) {
        Objects.requireNonNull(reasonCode, "reasonCode");
        String detail = bounded(reason, 10, 500, "Rejection reason");
        String evidence = bounded(
                evidenceReference, 3, 512, "Evidence reference"
        );
        if (!evidence.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{2,511}")) {
            throw invalid("Evidence reference has an invalid format.");
        }
        var review = repository.find(topupId)
                .orElseThrow(() -> notFound(
                        "Top-up review was not found."
                ));
        if (review.topup().status() == TopupRequest.Status.REJECTED
                && review.paymentEvent().status()
                == PaymentEvent.Status.REJECTED) {
            return result(review, true);
        }
        if (review.topup().status()
                != TopupRequest.Status.PENDING_REVIEW
                || review.paymentEvent().status()
                != PaymentEvent.Status.PENDING_REVIEW) {
            throw conflict("Top-up review already has a final decision.");
        }
        Instant now = clock.instant();
        if (!repository.reject(
                review,
                actorId,
                reasonCode.name(),
                detail,
                evidence,
                now
        )) {
            var winner = repository.find(topupId);
            if (winner.isPresent()
                    && winner.get().topup().status()
                    == TopupRequest.Status.REJECTED
                    && winner.get().paymentEvent().status()
                    == PaymentEvent.Status.REJECTED) {
                return result(winner.orElseThrow(), true);
            }
            throw conflict("Top-up review lost a concurrent decision.");
        }
        outbox.append(new IntegrationEvent(
                ids.get(),
                "monetization.topup.rejected",
                1,
                now,
                review.paymentEvent().id(),
                "topup_request",
                review.topup().id(),
                actorId,
                null,
                new RejectedNotification(
                        review.topup().userId(),
                        reasonCode.name()
                )
        ));
        return result(review, false);
    }

    private static Rejection result(
            TopupRejectionRepository.Review review,
            boolean replayed
    ) {
        return new Rejection(
                review.topup().id(),
                review.paymentEvent().id(),
                "REJECTED",
                replayed
        );
    }

    private static String bounded(
            String value,
            int minimum,
            int maximum,
            String field
    ) {
        if (value == null
                || value.strip().length() < minimum
                || value.strip().length() > maximum) {
            throw invalid(field + " has an invalid length.");
        }
        return value.strip();
    }

    private static ManualTopupException invalid(String message) {
        return new ManualTopupException(
                message,
                ManualTopupException.Kind.INVALID
        );
    }

    private static ManualTopupException notFound(String message) {
        return new ManualTopupException(
                message,
                ManualTopupException.Kind.NOT_FOUND
        );
    }

    private static ManualTopupException conflict(String message) {
        return new ManualTopupException(
                message,
                ManualTopupException.Kind.CONFLICT
        );
    }

    public record RejectedNotification(
            String userId,
            String reasonCode
    ) {
    }
}
