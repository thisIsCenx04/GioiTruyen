package com.storyplatform.media.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

public final class MediaContentValidator {

    private static final Map<String, byte[]> MAGIC = Map.of(
            "jpg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff},
            "jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff},
            "png", new byte[]{
                    (byte) 0x89, 0x50, 0x4e, 0x47,
                    0x0d, 0x0a, 0x1a, 0x0a
            }
    );

    public void validate(
            MediaProcessingOperations.Candidate candidate,
            byte[] content
    ) {
        if (content == null
                || content.length == 0
                || content.length != candidate.bytes()) {
            throw rejected(
                    "MEDIA_SIZE_MISMATCH",
                    "Downloaded content size differs from the webhook."
            );
        }
        String format = candidate.format().toLowerCase(Locale.ROOT);
        boolean recognized = "webp".equals(format)
                ? webp(content)
                : startsWith(content, MAGIC.get(format));
        if (!recognized) {
            throw rejected(
                    "MEDIA_MAGIC_INVALID",
                    "File signature does not match the declared image format."
            );
        }
        if (!sha256(content).equals(candidate.declaredSha256())) {
            throw rejected(
                    "MEDIA_HASH_MISMATCH",
                    "Downloaded content does not match the upload intent."
            );
        }
    }

    private static boolean webp(byte[] content) {
        return content.length >= 12
                && startsWith(content, new byte[]{0x52, 0x49, 0x46, 0x46})
                && content[8] == 0x57
                && content[9] == 0x45
                && content[10] == 0x42
                && content[11] == 0x50;
    }

    private static boolean startsWith(byte[] content, byte[] expected) {
        if (expected == null || content.length < expected.length) {
            return false;
        }
        return MessageDigest.isEqual(
                expected,
                java.util.Arrays.copyOf(content, expected.length)
        );
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static MediaPolicyException rejected(
            String code,
            String message
    ) {
        return new MediaPolicyException(code, message);
    }
}
