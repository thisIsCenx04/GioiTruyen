package com.storyplatform.unit.identity.infrastructure;

import com.storyplatform.identity.infrastructure.security
        .SecureReauthenticationTokenCodec;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

class SecureReauthenticationTokenCodecTest {

    private final SecureReauthenticationTokenCodec codec =
            new SecureReauthenticationTokenCodec(new SecureRandom());

    @Test
    void issuesOpaqueHighEntropyTokenAndStoresOnlyHash() {
        var first = codec.issue();
        var second = codec.issue();

        assertThat(first.value()).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(first.hash()).matches("^[0-9a-f]{64}$");
        assertThat(first.hash()).isEqualTo(codec.hash(first.value()));
        assertThat(first.value()).isNotEqualTo(second.value());
        assertThat(codec.isWellFormed(first.value())).isTrue();
        assertThat(codec.isWellFormed(null)).isFalse();
        assertThat(codec.isWellFormed("malformed")).isFalse();
    }
}
