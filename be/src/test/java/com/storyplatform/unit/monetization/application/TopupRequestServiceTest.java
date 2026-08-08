package com.storyplatform.unit.monetization.application;

import com.storyplatform.monetization.application.TopupDiscountOperations;
import com.storyplatform.monetization.application.TopupRequestException;
import com.storyplatform.monetization.application.TopupRequestService;
import com.storyplatform.monetization.application.port.TopupQrPayloadFactory;
import com.storyplatform.monetization.application.port.TopupRequestRepository;
import com.storyplatform.monetization.domain.TopupRequest;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TopupRequestServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T00:00:00Z");
    private static final String KEY = "request-key-000001";
    private final TopupRequestRepository repository =
            mock(TopupRequestRepository.class);
    private final TopupDiscountOperations discounts =
            mock(TopupDiscountOperations.class);
    private final TopupQrPayloadFactory qr =
            mock(TopupQrPayloadFactory.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final AtomicInteger sequence = new AtomicInteger(1);
    private final TopupRequestService service = new TopupRequestService(
            repository,
            discounts,
            qr,
            outbox,
            Clock.fixed(NOW, ZoneOffset.UTC),
            Duration.ofMinutes(30),
            () -> UUID.fromString(
                    "10000000-0000-4000-8000-%012d".formatted(
                            sequence.getAndIncrement()
                    )
            ),
            () -> "GT12345678901234"
    );

    @Test
    void createsServerAuthoritativeRequestWithDiscountSnapshot() {
        when(repository.findByIdempotencyKeyHash(any()))
                .thenReturn(Optional.empty());
        when(discounts.current()).thenReturn(
                new TopupDiscountOperations.DiscountView(
                        new BigDecimal("10.00"),
                        7,
                        NOW.minusSeconds(60),
                        "admin"
                )
        );
        when(qr.create(100_000, "GT12345678901234"))
                .thenReturn("server-qr");
        when(repository.insert(any())).thenAnswer(
                invocation -> invocation.getArgument(0)
        );
        ArgumentCaptor<TopupRequest> stored =
                ArgumentCaptor.forClass(TopupRequest.class);
        ArgumentCaptor<IntegrationEvent> event =
                ArgumentCaptor.forClass(IntegrationEvent.class);

        var result = service.create("reader-1", KEY, 100_000);

        assertThat(result.creditedXu()).isEqualTo(90_000);
        assertThat(result.discountVersion()).isEqualTo(7);
        assertThat(result.qrPayload()).isEqualTo("server-qr");
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(1800));
        verify(repository).insert(stored.capture());
        assertThat(stored.getValue().idempotencyKeyHash())
                .hasSize(64)
                .doesNotContain(KEY);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().eventType())
                .isEqualTo("monetization.topup.requested");
    }

    @Test
    void replaysSameKeyAndRejectsChangedAmount() {
        TopupRequest existing = request(100_000, hash("100000"));
        when(repository.findByIdempotencyKeyHash(any()))
                .thenReturn(Optional.of(existing));

        var replay = service.create("reader-1", KEY, 100_000);

        assertThat(replay.id()).isEqualTo(existing.id());
        verify(repository, never()).insert(any());
        verify(outbox, never()).append(any());

        assertThatThrownBy(() ->
                service.create("reader-1", KEY, 200_000)
        ).isInstanceOf(TopupRequestException.class)
                .extracting("kind")
                .isEqualTo(TopupRequestException.Kind.CONFLICT);
    }

    @Test
    void keepsReadsBoundToAuthenticatedOwner() {
        TopupRequest existing = request(100_000, hash("100000"));
        when(repository.findByIdAndUserId(existing.id(), "reader-1"))
                .thenReturn(Optional.of(existing));
        when(repository.findRecentByUserId("reader-1", 50))
                .thenReturn(List.of(existing));

        assertThat(service.get("reader-1", existing.id()).id())
                .isEqualTo(existing.id());
        assertThat(service.recent("reader-1")).hasSize(1);

        assertThatThrownBy(() -> service.get("reader-2", existing.id()))
                .isInstanceOf(TopupRequestException.class)
                .extracting("kind")
                .isEqualTo(TopupRequestException.Kind.NOT_FOUND);
    }

    @Test
    void validatesKeyAndAmountBeforeAnyWrite() {
        when(repository.findByIdempotencyKeyHash(any()))
                .thenReturn(Optional.empty());
        when(discounts.current()).thenReturn(
                new TopupDiscountOperations.DiscountView(
                        BigDecimal.TEN,
                        0,
                        Instant.EPOCH,
                        "system-default"
                )
        );

        assertThatThrownBy(() ->
                service.create("reader-1", "short", 100_000)
        ).isInstanceOf(TopupRequestException.class)
                .extracting("kind")
                .isEqualTo(TopupRequestException.Kind.INVALID);
        assertThatThrownBy(() ->
                service.create("reader-1", KEY, 9_999)
        ).isInstanceOf(TopupRequestException.class)
                .extracting("kind")
                .isEqualTo(TopupRequestException.Kind.INVALID);
        verify(repository, never()).insert(any());
    }

    private static TopupRequest request(long amount, String requestHash) {
        return new TopupRequest(
                "20000000-0000-4000-8000-000000000001",
                "reader-1",
                amount,
                amount * 9 / 10,
                BigDecimal.TEN,
                0,
                "GT12345678901234",
                "server-qr",
                TopupRequest.Status.AWAITING_PAYMENT,
                NOW.plusSeconds(1800),
                NOW,
                "a".repeat(64),
                requestHash
        );
    }

    private static String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(
                                    java.nio.charset.StandardCharsets.UTF_8
                            ))
            );
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
