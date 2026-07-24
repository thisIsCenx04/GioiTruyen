package com.storyplatform.unit.media.infrastructure;

import com.storyplatform.media.application.MediaRequestException;
import com.storyplatform.media.domain.MediaOwnerType;
import com.storyplatform.media.domain.UploadPurpose;
import com.storyplatform.media.infrastructure
        .CloudinaryNotificationJsonDecoder;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudinaryNotificationJsonDecoderTest {

    private final CloudinaryNotificationJsonDecoder decoder =
            new CloudinaryNotificationJsonDecoder(
                    new ObjectMapper(),
                    Clock.fixed(
                            Instant.parse("2026-07-24T00:00:00Z"),
                            ZoneOffset.UTC
                    )
            );

    @Test
    void decodesOnlyBoundAuthenticatedUploadMetadata() {
        var event = decoder.decode(payload(1024), "a".repeat(64));

        assertThat(event.assetId()).isEqualTo("cloudinary_asset_1");
        assertThat(event.intentId()).isEqualTo(
                "00000000-0000-4000-8000-000000000003"
        );
        assertThat(event.ownerType()).isEqualTo(MediaOwnerType.USER);
        assertThat(event.purpose()).isEqualTo(UploadPurpose.AVATAR);
        assertThat(event.deliveryType()).isEqualTo("authenticated");
        assertThat(event.bytes()).isEqualTo(1024);
    }

    @Test
    void rejectsOversizedAndPublicDeliveryPayloads() {
        assertThatThrownBy(() -> decoder.decode(
                payload(6L * 1024 * 1024),
                "a".repeat(64)
        )).isInstanceOf(MediaRequestException.class);
        String publicDelivery = new String(
                payload(1024),
                StandardCharsets.UTF_8
        ).replace("\"authenticated\"", "\"upload\"");
        assertThatThrownBy(() -> decoder.decode(
                publicDelivery.getBytes(StandardCharsets.UTF_8),
                "a".repeat(64)
        )).isInstanceOf(MediaRequestException.class);
    }

    private static byte[] payload(long bytes) {
        return """
                {
                  "notification_type": "upload",
                  "asset_id": "cloudinary_asset_1",
                  "public_id": "gioitruyen/avatar/one",
                  "resource_type": "image",
                  "type": "authenticated",
                  "format": "png",
                  "version": 42,
                  "bytes": %d,
                  "width": 128,
                  "height": 128,
                  "context": {
                    "custom": {
                      "intent_id":
                        "00000000-0000-4000-8000-000000000003",
                      "owner_type": "user",
                      "owner_id":
                        "00000000-0000-4000-8000-000000000001",
                      "purpose": "avatar",
                      "declared_sha256":
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    }
                  }
                }
                """.formatted(bytes).getBytes(StandardCharsets.UTF_8);
    }
}
