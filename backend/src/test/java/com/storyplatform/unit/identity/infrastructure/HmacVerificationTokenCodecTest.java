package com.storyplatform.unit.identity.infrastructure;

import com.storyplatform.identity.application.port.VerificationTokenCodec;
import com.storyplatform.identity.infrastructure.security
        .HmacVerificationTokenCodec;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class HmacVerificationTokenCodecTest {

    private static final UUID ID = UUID.fromString(
            "21a64aa0-c960-4a90-a97c-b953fd1eeb3c"
    );
    private static final Instant EXPIRY =
            Instant.parse("2026-07-25T00:00:00Z");
    private final HmacVerificationTokenCodec codec =
            new HmacVerificationTokenCodec(
                    "0123456789abcdef0123456789abcdef"
                            .getBytes(StandardCharsets.UTF_8),
                    () -> ID
            );

    @Test
    void storesOnlyDigestAndReconstructsDeliveryToken() {
        VerificationTokenCodec.IssuedVerificationToken issued =
                codec.issue("user-1", EXPIRY);
        String raw = codec.tokenForDelivery(
                issued.verificationId(),
                "user-1",
                EXPIRY
        );

        assertThat(raw).startsWith(ID + ".");
        assertThat(codec.isWellFormed(raw)).isTrue();
        assertThat(issued.tokenHash())
                .hasSize(64)
                .isEqualTo(codec.hash(raw))
                .doesNotContain(raw);
    }

    @Test
    void bindsSignatureToUserAndExpiry() {
        String original = codec.tokenForDelivery(
                ID.toString(),
                "user-1",
                EXPIRY
        );
        assertThat(codec.tokenForDelivery(
                ID.toString(),
                "user-2",
                EXPIRY
        )).isNotEqualTo(original);
        assertThat(codec.tokenForDelivery(
                ID.toString(),
                "user-1",
                EXPIRY.plusSeconds(1)
        )).isNotEqualTo(original);
        assertThat(codec.isWellFormed("invalid")).isFalse();
        assertThat(codec.isWellFormed(null)).isFalse();
    }

    @Test
    void rejectsShortKeys() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new HmacVerificationTokenCodec(
                        new byte[31],
                        UUID::randomUUID
                )
        ).withMessage(
                "Verification HMAC key must contain at least 32 bytes"
        );
    }
}
