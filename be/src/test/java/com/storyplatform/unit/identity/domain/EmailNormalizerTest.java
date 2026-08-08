package com.storyplatform.unit.identity.domain;

import com.storyplatform.identity.domain.EmailNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailNormalizerTest {

    private final EmailNormalizer normalizer = new EmailNormalizer();

    @Test
    void normalizesWhitespaceCaseAndInternationalDomain() {
        assertThat(normalizer.normalize("  Reader@BÜCHER.example  "))
                .isEqualTo("reader@xn--bcher-kva.example");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "reader",
            "@example.com",
            "reader@@example.com",
            ".reader@example.com",
            "reader.@example.com",
            "reader..name@example.com",
            "reader@example",
            "reader@-example.com",
            "ngườiđọc@example.com"
    })
    void rejectsUnsupportedOrMalformedAddress(String email) {
        assertThatThrownBy(() -> normalizer.normalize(email))
                .isInstanceOf(EmailNormalizer.InvalidEmailException.class);
    }

    @Test
    void rejectsNullAddress() {
        assertThatThrownBy(() -> normalizer.normalize(null))
                .isInstanceOf(EmailNormalizer.InvalidEmailException.class);
    }

    @Test
    void rejectsAddressBeyondLocalOrTotalLengthLimits() {
        assertThatThrownBy(() ->
                normalizer.normalize("a".repeat(65) + "@example.com")
        ).isInstanceOf(EmailNormalizer.InvalidEmailException.class);

        String longDomain = String.join(
                ".",
                "a".repeat(63),
                "b".repeat(63),
                "c".repeat(63),
                "example"
        );
        assertThatThrownBy(() ->
                normalizer.normalize("a".repeat(64) + "@" + longDomain)
        ).isInstanceOf(EmailNormalizer.InvalidEmailException.class);
    }
}
