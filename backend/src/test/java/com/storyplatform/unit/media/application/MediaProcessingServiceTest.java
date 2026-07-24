package com.storyplatform.unit.media.application;

import com.storyplatform.media.application.MediaContentValidator;
import com.storyplatform.media.application.MediaGatewayException;
import com.storyplatform.media.application.MediaProcessingOperations;
import com.storyplatform.media.application.MediaProcessingService;
import com.storyplatform.media.application.port.MediaProcessingGateway;
import com.storyplatform.media.domain.MediaOwnerType;
import com.storyplatform.media.domain.UploadPurpose;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaProcessingServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private static final String WORKER = "worker-12345678";
    private static final byte[] JPEG = {
            (byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01
    };

    @Test
    void claimsValidSourceAndPublishesNormalizedAsset() {
        var repository = mock(MediaProcessingOperations.Repository.class);
        var gateway = mock(MediaProcessingGateway.class);
        var candidate = candidate(1, sha256(JPEG));
        var published = new MediaProcessingOperations.PublishedAsset(
                "published/asset", 7, "png", 20
        );
        when(repository.claim(any(), any(), any(), eq(5)))
                .thenReturn(Optional.of(candidate));
        when(gateway.downloadOriginal(candidate, 5L * 1024 * 1024))
                .thenReturn(JPEG);
        when(gateway.publishNormalized(candidate, JPEG))
                .thenReturn(published);
        when(repository.complete(candidate, WORKER, published, NOW))
                .thenReturn(true);

        assertThat(service(repository, gateway).processNext(WORKER))
                .isTrue();

        verify(repository).complete(candidate, WORKER, published, NOW);
        verify(repository, never()).reject(any(), any(), any(), any());
        verify(repository, never()).reschedule(
                any(), any(), any(), any(), any(Boolean.class)
        );
    }

    @Test
    void rejectsAFileWhoseMagicDoesNotMatch() {
        var repository = mock(MediaProcessingOperations.Repository.class);
        var gateway = mock(MediaProcessingGateway.class);
        byte[] html = "<x/>".getBytes();
        var candidate = candidate(1, sha256(html));
        when(repository.claim(any(), any(), any(), eq(5)))
                .thenReturn(Optional.of(candidate));
        when(gateway.downloadOriginal(any(), any(Long.class)))
                .thenReturn(html);

        assertThat(service(repository, gateway).processNext(WORKER))
                .isTrue();

        verify(repository).reject(
                candidate,
                WORKER,
                "MEDIA_MAGIC_INVALID",
                NOW
        );
        verify(gateway, never()).publishNormalized(any(), any());
    }

    @Test
    void retriesTransientGatewayFailureAndExhaustsLastAttempt() {
        var repository = mock(MediaProcessingOperations.Repository.class);
        var gateway = mock(MediaProcessingGateway.class);
        var candidate = candidate(5, sha256(JPEG));
        when(repository.claim(any(), any(), any(), eq(5)))
                .thenReturn(Optional.of(candidate));
        when(gateway.downloadOriginal(any(), any(Long.class)))
                .thenThrow(new MediaGatewayException(
                        "CLOUDINARY_IO_FAILURE",
                        "temporary",
                        null
                ));

        assertThat(service(repository, gateway).processNext(WORKER))
                .isTrue();

        verify(repository).reschedule(
                candidate,
                WORKER,
                "CLOUDINARY_IO_FAILURE",
                NOW.plusSeconds(30),
                true
        );
    }

    @Test
    void returnsFalseWhenTheQueueIsEmpty() {
        var repository = mock(MediaProcessingOperations.Repository.class);
        var gateway = mock(MediaProcessingGateway.class);
        when(repository.claim(any(), any(), any(), eq(5)))
                .thenReturn(Optional.empty());

        assertThat(service(repository, gateway).processNext(WORKER))
                .isFalse();

        verify(gateway, never()).downloadOriginal(any(), any(Long.class));
    }

    @Test
    void retriesGenericLeaseLossBeforeMaximumAttempt() {
        var repository = mock(MediaProcessingOperations.Repository.class);
        var gateway = mock(MediaProcessingGateway.class);
        var candidate = candidate(2, sha256(JPEG));
        var published = new MediaProcessingOperations.PublishedAsset(
                "published/asset", 7, "png", 20
        );
        when(repository.claim(any(), any(), any(), eq(5)))
                .thenReturn(Optional.of(candidate));
        when(gateway.downloadOriginal(any(), any(Long.class)))
                .thenReturn(JPEG);
        when(gateway.publishNormalized(candidate, JPEG))
                .thenReturn(published);
        when(repository.complete(candidate, WORKER, published, NOW))
                .thenReturn(false);

        assertThat(service(repository, gateway).processNext(WORKER))
                .isTrue();

        verify(repository).reschedule(
                candidate,
                WORKER,
                "MEDIA_PROCESSING_FAILED",
                NOW.plusSeconds(30),
                false
        );
    }

    @Test
    void validatesWorkerAndProcessingBounds() {
        var repository = mock(MediaProcessingOperations.Repository.class);
        var gateway = mock(MediaProcessingGateway.class);

        assertThatThrownBy(() ->
                service(repository, gateway).processNext("short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MediaProcessingService(
                repository, gateway, new MediaContentValidator(),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ZERO,
                Duration.ofSeconds(1), 5
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MediaProcessingService(
                repository, gateway, new MediaContentValidator(),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(1),
                null, 5
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MediaProcessingService(
                repository, gateway, new MediaContentValidator(),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(1),
                Duration.ofSeconds(1), 21
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static MediaProcessingService service(
            MediaProcessingOperations.Repository repository,
            MediaProcessingGateway gateway
    ) {
        return new MediaProcessingService(
                repository,
                gateway,
                new MediaContentValidator(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                5
        );
    }

    private static MediaProcessingOperations.Candidate candidate(
            int attempt,
            String hash
    ) {
        return new MediaProcessingOperations.Candidate(
                "asset", "source", 1, "jpg", hash, JPEG.length,
                10, 10, MediaOwnerType.USER, "owner",
                UploadPurpose.AVATAR, attempt, NOW.plusSeconds(30)
        );
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content)
            );
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
