package com.storyplatform.unit.identity.infrastructure;

import com.storyplatform.identity.application.port.RefreshTokenCodec;
import com.storyplatform.identity.infrastructure.security
        .SecureRefreshTokenCodec;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

class SecureRefreshTokenCodecTest {

    private final SecureRefreshTokenCodec codec =
            new SecureRefreshTokenCodec(new SecureRandom());

    @Test
    void issuesOpaqueHighEntropyTokensAndStoresOnlyDigest() {
        RefreshTokenCodec.IssuedRefreshToken first = codec.issue();
        RefreshTokenCodec.IssuedRefreshToken second = codec.issue();

        assertThat(first.value()).hasSize(43).isNotEqualTo(second.value());
        assertThat(codec.isWellFormed(first.value())).isTrue();
        assertThat(first.hash())
                .hasSize(64)
                .isEqualTo(codec.hash(first.value()))
                .doesNotContain(first.value());
        assertThat(codec.isWellFormed("malformed")).isFalse();
        assertThat(codec.isWellFormed(null)).isFalse();
    }
}
