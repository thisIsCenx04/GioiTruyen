package com.storyplatform.media.application;

import com.storyplatform.media.application.port.CloudinaryNotificationDecoder;
import com.storyplatform.media.application.port.MediaAssetRepository;
import com.storyplatform.media.application.port.WebhookSignatureVerifier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class MediaWebhookUseCase implements MediaWebhookOperations {

    private final WebhookSignatureVerifier verifier;
    private final CloudinaryNotificationDecoder decoder;
    private final MediaAssetRepository assets;

    public MediaWebhookUseCase(
            WebhookSignatureVerifier verifier,
            CloudinaryNotificationDecoder decoder,
            MediaAssetRepository assets
    ) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        this.assets = Objects.requireNonNull(assets, "assets");
    }

    @Override
    public Result accept(
            byte[] body,
            String timestamp,
            String signature
    ) {
        if (body == null || body.length == 0) {
            throw rejected(
                    "CLOUDINARY_WEBHOOK_EMPTY",
                    "The notification body is required."
            );
        }
        if (!verifier.verify(body, timestamp, signature)) {
            throw rejected(
                    "CLOUDINARY_SIGNATURE_INVALID",
                    "The notification signature or timestamp is invalid."
            );
        }
        AssetEvent event = decoder.decode(
                body,
                eventId(body, timestamp, signature)
        );
        return assets.recordPending(event)
                ? Result.ACCEPTED
                : Result.DUPLICATE;
    }

    private static String eventId(
            byte[] body,
            String timestamp,
            String signature
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(body);
            digest.update(timestamp.getBytes(StandardCharsets.UTF_8));
            digest.update(signature.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static MediaRequestException rejected(
            String code,
            String message
    ) {
        return new MediaRequestException(code, message, false);
    }
}
