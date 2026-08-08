package com.storyplatform.unit.identity.domain;

import com.storyplatform.identity.domain.PasswordPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsLongPassphraseWithoutForcingCompositionRules() {
        assertThat(policy.accepts("correct horse battery staple"))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "short",
            "password1234",
            "PASSWORD1234",
            "valid-length\n"
    })
    void rejectsWeakOrControlCharacterPassword(String password) {
        assertThat(policy.accepts(password)).isFalse();
    }

    @Test
    void rejectsNullAndOversizedPassword() {
        assertThat(policy.accepts(null)).isFalse();
        assertThat(policy.accepts("a".repeat(129))).isFalse();
    }
}
