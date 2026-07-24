package com.storyplatform.unit.analytics.infrastructure;

import com.storyplatform.analytics.infrastructure.security
        .HmacReadingSessionPseudonymizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacReadingSessionPseudonymizerTest {

    @Test
    void createsStableDomainSeparatedOpaqueReferences() {
        var first = new HmacReadingSessionPseudonymizer(
                "a".repeat(32).getBytes()
        );
        var second = new HmacReadingSessionPseudonymizer(
                "b".repeat(32).getBytes()
        );
        String session = "10000000-0000-4000-8000-000000000001";

        assertThat(first.pseudonymize(session))
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isEqualTo(first.pseudonymize(session))
                .isNotEqualTo(second.pseudonymize(session))
                .doesNotContain(session);
    }

    @Test
    void rejectsWeakKeysAndInvalidSessionIdentifiers() {
        assertThatThrownBy(() -> new HmacReadingSessionPseudonymizer(
                new byte[31]
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HmacReadingSessionPseudonymizer(null))
                .isInstanceOf(NullPointerException.class);
        var pseudonyms = new HmacReadingSessionPseudonymizer(new byte[32]);
        assertThatThrownBy(() -> pseudonyms.pseudonymize("user-or-ip"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
