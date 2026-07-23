package com.storyplatform.unit.identity.infrastructure;

import com.storyplatform.identity.infrastructure.security.Argon2PasswordHasher;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class Argon2PasswordHasherTest {

    @Test
    void producesSaltedArgon2idHashVerifiableByEquivalentEncoder() {
        String rawPassword = "correct horse battery staple";
        Argon2PasswordHasher hasher = new Argon2PasswordHasher(
                19_456,
                2,
                1
        );

        String first = hasher.hash(rawPassword);
        String second = hasher.hash(rawPassword);

        assertThat(first).startsWith("$argon2id$");
        assertThat(first).isNotEqualTo(second);
        assertThat(first).doesNotContain(rawPassword);
        assertThat(new Argon2PasswordEncoder(
                16,
                32,
                1,
                19_456,
                2
        ).matches(rawPassword, first)).isTrue();
    }
}
