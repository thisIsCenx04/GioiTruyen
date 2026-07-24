package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.port
        .ConfigurationChangeAuthorizer;
import com.storyplatform.monetization.application.port
        .TopupDiscountRepository;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class TopupDiscountService
        implements TopupDiscountOperations {

    public static final String KEY = "topup-discount";
    public static final BigDecimal DEFAULT_PERCENT = BigDecimal.TEN;
    private final TopupDiscountRepository repository;
    private final ConfigurationChangeAuthorizer authorizer;
    private final OutboxAppender outbox;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public TopupDiscountService(
            TopupDiscountRepository repository,
            ConfigurationChangeAuthorizer authorizer,
            OutboxAppender outbox,
            Clock clock,
            Supplier<UUID> ids
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.authorizer = Objects.requireNonNull(authorizer);
        this.outbox = Objects.requireNonNull(outbox);
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
    }

    @Override
    public DiscountView current() {
        return repository.current().map(TopupDiscountService::view)
                .orElseGet(() -> new DiscountView(
                        DEFAULT_PERCENT,
                        0,
                        Instant.EPOCH,
                        "system-default"
                ));
    }

    @Override
    public DiscountView update(
            String actorId,
            String reauthenticationToken,
            long expectedVersion,
            BigDecimal discountPercent,
            String reason
    ) {
        BigDecimal normalized = normalize(discountPercent);
        if (reason == null
                || reason.strip().length() < 10
                || reason.strip().length() > 500) {
            throw invalid("Reason must contain 10 to 500 characters.");
        }
        var previous = current();
        if (expectedVersion != previous.version()) {
            throw new TopupDiscountException(
                    "The discount configuration version is stale.",
                    TopupDiscountException.Kind.CONFLICT
            );
        }
        if (!authorizer.consume(actorId, reauthenticationToken)) {
            throw new TopupDiscountException(
                    "A scoped reauthentication grant is required.",
                    TopupDiscountException.Kind.FORBIDDEN
            );
        }
        Instant now = clock.instant();
        var stored = repository.insert(
                new TopupDiscountRepository.VersionedDiscount(
                        KEY,
                        normalized,
                        Math.addExact(previous.version(), 1),
                        now,
                        actorId
                ),
                previous.discountPercent(),
                reason.strip()
        );
        outbox.append(new IntegrationEvent(
                ids.get(),
                "monetization.topupdiscount.changed",
                1,
                now,
                ids.get().toString(),
                "monetization_config",
                KEY,
                actorId,
                null,
                new DiscountChanged(
                        previous.discountPercent(),
                        stored.discountPercent(),
                        stored.version()
                )
        ));
        return view(stored);
    }

    private static BigDecimal normalize(BigDecimal value) {
        if (value == null
                || value.scale() > 2
                || value.compareTo(BigDecimal.ZERO) < 0
                || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw invalid(
                    "Discount percent must be 0 to 100 with at most 2 decimals."
            );
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static TopupDiscountException invalid(String message) {
        return new TopupDiscountException(
                message,
                TopupDiscountException.Kind.INVALID
        );
    }

    private static DiscountView view(
            TopupDiscountRepository.VersionedDiscount value
    ) {
        return new DiscountView(
                value.discountPercent(),
                value.version(),
                value.effectiveAt(),
                value.changedBy()
        );
    }

    public record DiscountChanged(
            BigDecimal previousPercent,
            BigDecimal discountPercent,
            long version
    ) {
    }
}
