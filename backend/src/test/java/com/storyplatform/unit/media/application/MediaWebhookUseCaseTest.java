package com.storyplatform.unit.media.application;

import com.storyplatform.media.application.MediaRequestException;
import com.storyplatform.media.application.MediaWebhookOperations;
import com.storyplatform.media.application.MediaWebhookUseCase;
import com.storyplatform.media.domain.MediaOwnerType;
import com.storyplatform.media.domain.UploadPurpose;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaWebhookUseCaseTest {

    private static final byte[] BODY = "{\"asset_id\":\"one\"}".getBytes(
            java.nio.charset.StandardCharsets.UTF_8
    );

    @Test
    void verifiesRawBodyBeforeParsingAndPersistsOnce() {
        AtomicInteger decoded = new AtomicInteger();
        MediaWebhookUseCase useCase = new MediaWebhookUseCase(
                (body, timestamp, signature) -> true,
                (body, eventId) -> {
                    decoded.incrementAndGet();
                    return event(eventId);
                },
                event -> true
        );

        MediaWebhookOperations.Result result = useCase.accept(
                BODY,
                "1784851200",
                "a".repeat(64)
        );

        assertThat(result).isEqualTo(
                MediaWebhookOperations.Result.ACCEPTED
        );
        assertThat(decoded).hasValue(1);
    }

    @Test
    void duplicateDeliveryIsAcknowledgedWithoutASecondAsset() {
        MediaWebhookUseCase useCase = new MediaWebhookUseCase(
                (body, timestamp, signature) -> true,
                (body, eventId) -> event(eventId),
                event -> false
        );

        assertThat(useCase.accept(
                BODY,
                "1784851200",
                "a".repeat(64)
        )).isEqualTo(MediaWebhookOperations.Result.DUPLICATE);
    }

    @Test
    void invalidSignatureStopsBeforeJsonParsing() {
        AtomicInteger decoded = new AtomicInteger();
        MediaWebhookUseCase useCase = new MediaWebhookUseCase(
                (body, timestamp, signature) -> false,
                (body, eventId) -> {
                    decoded.incrementAndGet();
                    return event(eventId);
                },
                event -> true
        );

        assertThatThrownBy(() -> useCase.accept(
                BODY,
                "old",
                "invalid"
        ))
                .isInstanceOf(MediaRequestException.class)
                .extracting("code")
                .isEqualTo("CLOUDINARY_SIGNATURE_INVALID");
        assertThat(decoded).hasValue(0);
    }

    @Test
    void emptyBodyIsRejectedBeforeVerification() {
        MediaWebhookUseCase useCase = new MediaWebhookUseCase(
                (body, timestamp, signature) -> {
                    throw new AssertionError("must not verify");
                },
                (body, eventId) -> event(eventId),
                event -> true
        );

        assertThatThrownBy(() -> useCase.accept(
                new byte[0],
                "1784851200",
                "a".repeat(64)
        ))
                .isInstanceOf(MediaRequestException.class)
                .extracting("code")
                .isEqualTo("CLOUDINARY_WEBHOOK_EMPTY");
    }

    private static MediaWebhookOperations.AssetEvent event(
            String eventId
    ) {
        return new MediaWebhookOperations.AssetEvent(
                eventId,
                "asset",
                "public",
                "00000000-0000-4000-8000-000000000003",
                MediaOwnerType.USER,
                "00000000-0000-4000-8000-000000000001",
                UploadPurpose.AVATAR,
                "image",
                "authenticated",
                "png",
                100,
                10,
                10,
                Instant.parse("2026-07-24T00:00:00Z")
        );
    }
}
