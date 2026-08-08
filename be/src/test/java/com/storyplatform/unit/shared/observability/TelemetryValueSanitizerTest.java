package com.storyplatform.unit.shared.observability;

import com.storyplatform.shared.observability.TelemetryValueSanitizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryValueSanitizerTest {

    @Test
    void preservesBoundedContractValues() {
        assertThat(TelemetryValueSanitizer.eventType(
                "publishing.story.published"
        )).isEqualTo("publishing.story.published");
        assertThat(TelemetryValueSanitizer.eventVersion(12))
                .isEqualTo("12");
    }

    @Test
    void collapsesUntrustedValuesToBoundMetricCardinality() {
        assertThat(TelemetryValueSanitizer.eventType(null))
                .isEqualTo("unknown");
        assertThat(TelemetryValueSanitizer.eventType(
                "Bearer secret-token"
        )).isEqualTo("unknown");
        assertThat(TelemetryValueSanitizer.eventVersion(-42))
                .isEqualTo("unknown");
        assertThat(TelemetryValueSanitizer.eventVersion(10_000))
                .isEqualTo("unknown");
    }
}
