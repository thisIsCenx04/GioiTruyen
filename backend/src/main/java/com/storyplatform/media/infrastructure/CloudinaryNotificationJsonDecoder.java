package com.storyplatform.media.infrastructure;

import com.storyplatform.media.application.MediaRequestException;
import com.storyplatform.media.application.MediaWebhookOperations;
import com.storyplatform.media.application.port.CloudinaryNotificationDecoder;
import com.storyplatform.media.domain.MediaOwnerType;
import com.storyplatform.media.domain.UploadPurpose;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class CloudinaryNotificationJsonDecoder
        implements CloudinaryNotificationDecoder {

    private final ObjectMapper mapper;
    private final Clock clock;

    public CloudinaryNotificationJsonDecoder(
            ObjectMapper mapper,
            Clock clock
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public MediaWebhookOperations.AssetEvent decode(
            byte[] body,
            String eventId
    ) {
        try {
            JsonNode root = mapper.readTree(body);
            if (!"upload".equals(text(root, "notification_type"))) {
                throw invalid("Only upload notifications are supported.");
            }
            JsonNode context = root.path("context").path("custom");
            if (context.isMissingNode()) {
                context = root.path("context");
            }
            MediaOwnerType ownerType = enumValue(
                    MediaOwnerType.class,
                    text(context, "owner_type")
            );
            UploadPurpose purpose = enumValue(
                    UploadPurpose.class,
                    text(context, "purpose")
            );
            String ownerId = uuid(text(context, "owner_id"));
            String intentId = uuid(text(context, "intent_id"));
            long bytes = positive(root.path("bytes").asLong(-1), "bytes");
            int width = positiveInt(root.path("width").asInt(-1), "width");
            int height = positiveInt(
                    root.path("height").asInt(-1),
                    "height"
            );
            if (bytes > purpose.maximumBytes()) {
                throw invalid("Uploaded bytes exceed the purpose limit.");
            }
            if (purpose.ownerType() != ownerType) {
                throw invalid("Owner type does not match upload purpose.");
            }
            return new MediaWebhookOperations.AssetEvent(
                    token(eventId, "eventId", 64),
                    token(text(root, "asset_id"), "assetId", 128),
                    publicId(text(root, "public_id")),
                    intentId,
                    ownerType,
                    ownerId,
                    purpose,
                    exact(root, "resource_type", "image"),
                    exact(root, "type", "authenticated"),
                    format(text(root, "format")),
                    bytes,
                    width,
                    height,
                    clock.instant()
            );
        } catch (MediaRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid("The notification payload is malformed.");
        }
    }

    private static String text(JsonNode parent, String field) {
        String value = parent.path(field).asText("");
        if (value.isBlank()) {
            throw invalid("A required notification field is missing.");
        }
        return value;
    }

    private static String exact(
            JsonNode root,
            String field,
            String expected
    ) {
        String value = text(root, field);
        if (!expected.equals(value)) {
            throw invalid(field + " is not supported.");
        }
        return value;
    }

    private static <T extends Enum<T>> T enumValue(
            Class<T> type,
            String value
    ) {
        try {
            return Enum.valueOf(
                    type,
                    value.toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw invalid("Notification context is invalid.");
        }
    }

    private static String uuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            throw invalid("Notification context identifier is invalid.");
        }
    }

    private static long positive(long value, String field) {
        if (value < 1) {
            throw invalid(field + " must be positive.");
        }
        return value;
    }

    private static int positiveInt(int value, String field) {
        if (value < 1 || value > 100_000) {
            throw invalid(field + " is outside the accepted range.");
        }
        return value;
    }

    private static String token(
            String value,
            String field,
            int maximum
    ) {
        if (value == null
                || value.length() > maximum
                || !value.matches("[A-Za-z0-9_-]+")) {
            throw invalid(field + " is invalid.");
        }
        return value;
    }

    private static String publicId(String value) {
        if (value.length() > 255
                || !value.matches("[A-Za-z0-9_/-]+")
                || value.contains("..")) {
            throw invalid("publicId is invalid.");
        }
        return value;
    }

    private static String format(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!java.util.Set.of("jpg", "jpeg", "png", "webp")
                .contains(normalized)) {
            throw invalid("format is not allowed.");
        }
        return normalized;
    }

    private static MediaRequestException invalid(String detail) {
        return new MediaRequestException(
                "CLOUDINARY_PAYLOAD_INVALID",
                detail,
                false
        );
    }
}
