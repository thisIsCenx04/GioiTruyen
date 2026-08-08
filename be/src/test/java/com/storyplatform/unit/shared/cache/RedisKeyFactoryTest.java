package com.storyplatform.unit.shared.cache;

import com.storyplatform.shared.cache.RedisFailureMode;
import com.storyplatform.shared.cache.RedisKey;
import com.storyplatform.shared.cache.RedisKeyFactory;
import com.storyplatform.shared.cache.RedisNamespace;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisKeyFactoryTest {

    private final RedisKeyFactory keys = new RedisKeyFactory(
            "gioitruyen",
            "v1",
            "test"
    );

    @Test
    void createsVersionedAndEnvironmentScopedKeys() {
        RedisKey key = keys.create(
                RedisNamespace.CACHE,
                "catalog",
                "representation-v2",
                "query-93f8"
        );

        assertThat(key.value()).isEqualTo(
                "gioitruyen:v1:test:cache:"
                        + "catalog:representation-v2:query-93f8"
        );
        assertThat(key.toString()).isEqualTo(key.value());
    }

    @Test
    void eachWorkloadHasAnIsolatedPrefixAndExplicitFailureMode() {
        assertThat(RedisNamespace.values()).allSatisfy(namespace -> {
            RedisKey key = keys.create(namespace, "probe");

            assertThat(key.value())
                    .contains(":" + namespace.keySegment() + ":");
        });
        assertThat(RedisNamespace.CACHE.failureMode())
                .isEqualTo(RedisFailureMode.FALL_BACK_TO_SOURCE);
        assertThat(RedisNamespace.RATE_LIMIT.failureMode())
                .isEqualTo(RedisFailureMode.FAIL_CLOSED);
        assertThat(RedisNamespace.SESSION.failureMode())
                .isEqualTo(RedisFailureMode.FAIL_CLOSED);
        assertThat(RedisNamespace.COORDINATION.failureMode())
                .isEqualTo(RedisFailureMode.FAIL_CLOSED);
    }

    @Test
    void rejectsDelimiterWhitespaceControlAndUnboundedSegments() {
        assertThatThrownBy(() -> keys.create(
                RedisNamespace.CACHE,
                "user:42"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> keys.create(
                RedisNamespace.CACHE,
                "user 42"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> keys.create(
                RedisNamespace.CACHE,
                "x".repeat(129)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingKeySegmentsAndUnsafeRootConfiguration() {
        assertThatThrownBy(() -> keys.create(RedisNamespace.CACHE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisKeyFactory(
                "gioi:truyen",
                "v1",
                "test"
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
