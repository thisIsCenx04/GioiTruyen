package com.storyplatform.unit.identity.infrastructure;

import com.storyplatform.identity.infrastructure.security
        .TotpMfaCryptography;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Instant;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TotpMfaCryptographyTest {

    private final TotpMfaCryptography cryptography =
            new TotpMfaCryptography(
                    new byte[32],
                    new SecureRandom(new byte[]{1, 2, 3, 4})
            );

    @Test
    void secretsAreProvisionableButProtectedAtRest() {
        var secret = cryptography.generateSecret();

        assertThat(secret.provisioningSecret())
                .matches("^[A-Z2-7]{32}$");
        assertThat(secret.protectedSecret())
                .doesNotContain(secret.provisioningSecret());
        assertThat(cryptography.verifyTotp(
                secret.protectedSecret(),
                "000000",
                Instant.parse("2026-07-24T00:00:00Z")
        )).isFalse();
        assertThat(cryptography.verifyTotp(
                "corrupted",
                "000000",
                Instant.parse("2026-07-24T00:00:00Z")
        )).isFalse();
    }

    @Test
    void recoveryCodesAreUniqueAndHashed() {
        var codes = cryptography.generateRecoveryCodes();

        assertThat(codes).hasSize(8);
        assertThat(codes).extracting(
                value -> value.rawCode()
        ).doesNotHaveDuplicates();
        assertThat(codes).allSatisfy(code -> {
            assertThat(code.rawCode()).hasSize(16);
            assertThat(code.hash()).doesNotContain(code.rawCode());
            assertThat(cryptography.hashRecoveryCode(code.rawCode()))
                    .isEqualTo(code.hash());
        });
    }

    @Test
    void rejectsInvalidEncryptionKeySizes() {
        assertThatThrownBy(() -> new TotpMfaCryptography(
                new byte[16],
                new SecureRandom()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatedSecretProducesValidTotp() throws Exception {
        var secret = cryptography.generateSecret();
        Instant at = Instant.parse("2026-07-24T00:00:00Z");
        String code = totp(
                decodeBase32(secret.provisioningSecret()),
                at.getEpochSecond() / 30
        );

        assertThat(cryptography.verifyTotp(
                secret.protectedSecret(),
                code,
                at
        )).isTrue();
    }

    private static byte[] decodeBase32(String value) {
        ByteArrayOutputStream decoded = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char character : value.toCharArray()) {
            int index = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
                    .indexOf(character);
            buffer = (buffer << 5) | index;
            bits += 5;
            if (bits >= 8) {
                decoded.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return decoded.toByteArray();
    }

    private static String totp(byte[] secret, long counter)
            throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret, "HmacSHA1"));
        byte[] digest = mac.doFinal(
                ByteBuffer.allocate(Long.BYTES)
                        .putLong(counter)
                        .array()
        );
        int offset = digest[digest.length - 1] & 0x0f;
        int binary = ((digest[offset] & 0x7f) << 24)
                | ((digest[offset + 1] & 0xff) << 16)
                | ((digest[offset + 2] & 0xff) << 8)
                | (digest[offset + 3] & 0xff);
        return "%06d".formatted(binary % 1_000_000);
    }
}
