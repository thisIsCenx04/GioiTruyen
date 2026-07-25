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
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaliciousMediaCorpusTest {

    private final MediaContentValidator validator =
            new MediaContentValidator();

    @Test
    void rejectsActiveContentAndExecutableCorpusForEveryImageFormat() {
        List<byte[]> attacks = List.of(
                "<svg onload=alert(document.domain)>".getBytes(UTF_8),
                "<script>fetch('//evil.invalid')</script>".getBytes(UTF_8),
                "<?xml version=\"1.0\"?><svg/>".getBytes(UTF_8),
                "MZ\u0090\u0000PE executable".getBytes(UTF_8),
                new byte[]{0x7f, 0x45, 0x4c, 0x46, 0x02, 0x01},
                "%PDF-1.7 javascript".getBytes(UTF_8)
        );

        for (String format : List.of("jpg", "jpeg", "png", "webp")) {
            for (byte[] attack : attacks) {
                assertThatThrownBy(() -> validator.validate(
                        candidate(format, hash(attack), attack.length),
                        attack
                ))
                        .isInstanceOf(MediaPolicyException.class)
                        .extracting("code")
                        .isEqualTo("MEDIA_MAGIC_INVALID");
            }
        }
    }

    @Test
    void rejectsTruncatedHeadersAndFormatConfusion() {
        byte[] jpeg = {
                (byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01
        };
        byte[] png = {
                (byte) 0x89, 0x50, 0x4e, 0x47,
                0x0d, 0x0a, 0x1a, 0x0a
        };
        byte[] webp = {
                0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0,
                0x57, 0x45, 0x42, 0x50
        };

        assertRejected("png", jpeg, "MEDIA_MAGIC_INVALID");
        assertRejected("webp", png, "MEDIA_MAGIC_INVALID");
        assertRejected("jpg", webp, "MEDIA_MAGIC_INVALID");
        assertRejected(
                "png",
                java.util.Arrays.copyOf(png, png.length - 1),
                "MEDIA_MAGIC_INVALID"
        );
    }

    @Test
    void rejectsTamperingEvenWhenTheImageHeaderIsValid() {
        byte[] original = {
                (byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01
        };
        byte[] tampered = original.clone();
        tampered[tampered.length - 1] = 0x02;

        assertThatThrownBy(() -> validator.validate(
                candidate(
                        "jpg",
                        hash(original),
                        original.length
                ),
                tampered
        ))
                .isInstanceOf(MediaPolicyException.class)
                .extracting("code")
                .isEqualTo("MEDIA_HASH_MISMATCH");
    }

    @Test
    void rejectsDecompressionMetadataSizeMismatchBeforeParsing() {
        byte[] headerOnly = {
                (byte) 0x89, 0x50, 0x4e, 0x47,
                0x0d, 0x0a, 0x1a, 0x0a
        };

        assertThatThrownBy(() -> validator.validate(
                candidate(
                        "png",
                        hash(headerOnly),
                        Integer.MAX_VALUE
                ),
                headerOnly
        ))
                .isInstanceOf(MediaPolicyException.class)
                .extracting("code")
                .isEqualTo("MEDIA_SIZE_MISMATCH");
    }

    private void assertRejected(
            String declaredFormat,
            byte[] content,
            String code
    ) {
        assertThatThrownBy(() -> validator.validate(
                candidate(
                        declaredFormat,
                        hash(content),
                        content.length
                ),
                content
        ))
                .isInstanceOf(MediaPolicyException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    private static MediaProcessingOperations.Candidate candidate(
            String format,
            String declaredHash,
            long declaredBytes
    ) {
        return new MediaProcessingOperations.Candidate(
                "asset",
                "source",
                1,
                format,
                declaredHash,
                declaredBytes,
                1,
                1,
                MediaOwnerType.USER,
                "owner",
                UploadPurpose.AVATAR,
                1,
                Instant.MAX
        );
    }

    private static String hash(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content)
            );
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
