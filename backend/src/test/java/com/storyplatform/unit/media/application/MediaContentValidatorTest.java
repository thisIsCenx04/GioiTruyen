package com.storyplatform.unit.media.application;

import com.storyplatform.media.application.MediaContentValidator;
import com.storyplatform.media.application.MediaPolicyException;
import com.storyplatform.media.application.MediaProcessingOperations;
import com.storyplatform.media.domain.MediaOwnerType;
import com.storyplatform.media.domain.UploadPurpose;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaContentValidatorTest {

    private final MediaContentValidator validator =
            new MediaContentValidator();

    @Test
    void acceptsMatchingJpegPngAndWebpSignatures() {
        byte[][] images = {
                {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01},
                {(byte) 0x89, 0x50, 0x4e, 0x47,
                        0x0d, 0x0a, 0x1a, 0x0a, 0x01},
                {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0,
                        0x57, 0x45, 0x42, 0x50, 0x01}
        };
        String[] formats = {"jpg", "png", "webp"};

        for (int index = 0; index < images.length; index++) {
            byte[] image = images[index];
            String format = formats[index];
            assertThatCode(() -> validator.validate(
                    candidate(format, image),
                    image
            )).doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsExecutableHtmlAndSvgPayloads() {
        byte[][] malicious = {
                "MZ executable".getBytes(),
                "\u007fELF binary".getBytes(),
                "<html>script</html>".getBytes(),
                "<svg onload='x()'/>".getBytes()
        };

        for (byte[] payload : malicious) {
            assertThatThrownBy(() -> validator.validate(
                    candidate("png", payload),
                    payload
            ))
                    .isInstanceOf(MediaPolicyException.class)
                    .extracting("code")
                    .isEqualTo("MEDIA_MAGIC_INVALID");
        }
    }

    @Test
    void rejectsWebhookSizeAndHashMismatches() {
        byte[] image = {
                (byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01
        };
        var wrongSize = new MediaProcessingOperations.Candidate(
                "asset", "source", 1, "jpg", sha256(image),
                image.length + 1L, 1, 1, MediaOwnerType.USER, "owner",
                UploadPurpose.AVATAR, 1, Instant.MAX
        );
        var wrongHash = new MediaProcessingOperations.Candidate(
                "asset", "source", 1, "jpg", "0".repeat(64),
                image.length, 1, 1, MediaOwnerType.USER, "owner",
                UploadPurpose.AVATAR, 1, Instant.MAX
        );

        assertThatThrownBy(() -> validator.validate(wrongSize, image))
                .extracting("code")
                .isEqualTo("MEDIA_SIZE_MISMATCH");
        assertThatThrownBy(() -> validator.validate(wrongHash, image))
                .extracting("code")
                .isEqualTo("MEDIA_HASH_MISMATCH");
    }

    @Test
    void rejectsEmptyShortAndUnknownImageFormats() {
        byte[] shortWebp = {0x52, 0x49, 0x46, 0x46};
        byte[] unknown = {1, 2, 3, 4};

        assertThatThrownBy(() -> validator.validate(
                candidate("jpg", new byte[]{1}),
                null
        )).extracting("code").isEqualTo("MEDIA_SIZE_MISMATCH");
        assertThatThrownBy(() -> validator.validate(
                candidate("jpg", new byte[]{1}),
                new byte[0]
        )).extracting("code").isEqualTo("MEDIA_SIZE_MISMATCH");
        assertThatThrownBy(() -> validator.validate(
                candidate("webp", shortWebp),
                shortWebp
        )).extracting("code").isEqualTo("MEDIA_MAGIC_INVALID");
        assertThatThrownBy(() -> validator.validate(
                candidate("gif", unknown),
                unknown
        )).extracting("code").isEqualTo("MEDIA_MAGIC_INVALID");
    }

    private static MediaProcessingOperations.Candidate candidate(
            String format,
            byte[] content
    ) {
        return new MediaProcessingOperations.Candidate(
                "asset", "source", 1, format, sha256(content),
                content.length, 1, 1, MediaOwnerType.USER, "owner",
                UploadPurpose.AVATAR, 1, Instant.MAX
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
