package com.storyplatform.media.infrastructure;

import com.storyplatform.media.application.MediaRequestException;
import com.storyplatform.media.application.MediaWebhookOperations;
import com.storyplatform.media.domain.MediaOwnerType;
import com.storyplatform.media.domain.UploadPurpose;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Decodes a Cloudinary upload-notification JSON payload into a typed
 * {@link MediaWebhookOperations.AssetEvent}.
 *
 * <p>Only {@code authenticated} delivery type payloads with a body size
 * below 5 MB are accepted.
 */
public final class CloudinaryNotificationJsonDecoder {

    /** Sanity cap for the notification body itself (not the asset). */
    private static final int MAX_NOTIFICATION_BODY_BYTES = 64 * 1024;

    private final ObjectMapper mapper;
    private final Clock clock;

    public CloudinaryNotificationJsonDecoder(ObjectMapper mapper, Clock clock) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Decodes the notification payload.
     *
     * @param body      the raw JSON bytes from the Cloudinary webhook call
     * @param sha256    the declared SHA-256 hash (currently recorded but not
     *                  used for verification here)
     * @return the decoded asset event
     * @throws MediaRequestException if the payload is too large or the
     *                               delivery type is not {@code authenticated}
     */
    public MediaWebhookOperations.AssetEvent decode(byte[] body, String sha256) {
        Objects.requireNonNull(body, "body");
        if (body.length > MAX_NOTIFICATION_BODY_BYTES) {
            throw new MediaRequestException(
                    "CLOUDINARY_PAYLOAD_TOO_LARGE",
                    "Notification body is too large",
                    false
            );
        }
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (Exception exception) {
            throw new MediaRequestException(
                    "CLOUDINARY_PAYLOAD_INVALID",
                    "Failed to parse Cloudinary notification JSON",
                    false
            );
        }
        String deliveryType = root.path("type").textValue();
        if (!"authenticated".equals(deliveryType)) {
            throw new MediaRequestException(
                    "CLOUDINARY_DELIVERY_TYPE_REJECTED",
                    "Only authenticated delivery type is accepted, got: " + deliveryType,
                    false
            );
        }
        JsonNode context = root.path("context").path("custom");
        String intentId = context.path("intent_id").textValue();
        String ownerTypeRaw = context.path("owner_type").textValue();
        String ownerId = context.path("owner_id").textValue();
        String purposeRaw = context.path("purpose").textValue();
        String declaredSha256 = context.path("declared_sha256").textValue();

        MediaOwnerType ownerType = MediaOwnerType.valueOf(
                ownerTypeRaw.toUpperCase()
        );
        UploadPurpose purpose = UploadPurpose.valueOf(purposeRaw.toUpperCase());
        long declaredBytes = root.path("bytes").longValue();
        if (declaredBytes > purpose.maximumBytes()) {
            throw new MediaRequestException(
                    "CLOUDINARY_ASSET_TOO_LARGE",
                    "Declared asset size " + declaredBytes
                            + " bytes exceeds the maximum "
                            + purpose.maximumBytes()
                            + " bytes for purpose " + purpose.name(),
                    false
            );
        }

        return new MediaWebhookOperations.AssetEvent(
                UUID.randomUUID().toString(),
                root.path("asset_id").textValue(),
                root.path("public_id").textValue(),
                intentId,
                ownerType,
                ownerId,
                purpose,
                root.path("resource_type").textValue(),
                deliveryType,
                root.path("format").textValue(),
                root.path("version").longValue(),
                declaredSha256,
                declaredBytes,
                root.path("width").intValue(),
                root.path("height").intValue(),
                clock.instant()
        );
    }
}
