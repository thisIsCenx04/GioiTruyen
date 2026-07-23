package com.storyplatform.unit.shared.cache;

import com.storyplatform.shared.cache.RedisCacheTelemetry;
import com.storyplatform.shared.cache.RedisKey;
import com.storyplatform.shared.cache.RedisKeyFactory;
import com.storyplatform.shared.cache.RedisNamespace;
import com.storyplatform.shared.cache.RedisTtlPolicy;
import com.storyplatform.shared.cache.RedisValueCodec;
import com.storyplatform.shared.cache.RedisValueStore;
import com.storyplatform.shared.cache.ResilientRedisCache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientRedisCacheTest {

    private static final Duration TTL = Duration.ofMinutes(2);
    private static final RedisKeyFactory KEYS = new RedisKeyFactory(
            "gioitruyen",
            "v1",
            "test"
    );

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final StubRedisValueStore store = new StubRedisValueStore();
    private final ResilientRedisCache cache = new ResilientRedisCache(
            store,
            new RedisValueCodec(new ObjectMapper()),
            new RedisTtlPolicy(
                    Duration.ofSeconds(30),
                    Duration.ofMinutes(60),
                    0.0,
                    () -> 0.5
            ),
            new RedisCacheTelemetry(meters)
    );

    @Test
    void cacheHitAvoidsAuthoritativeLoader() {
        RedisKey key = cacheKey();
        store.values.put(key.value(), "\"cached\"");
        AtomicBoolean loaded = new AtomicBoolean();

        String result = cache.getOrLoad(key, String.class, TTL, () -> {
            loaded.set(true);
            return "source";
        });

        assertThat(result).isEqualTo("cached");
        assertThat(loaded).isFalse();
        assertMetric("read", "hit");
    }

    @Test
    void cacheMissLoadsAuthoritativeValueAndStoresItWithTtl() {
        RedisKey key = cacheKey();

        String result = cache.getOrLoad(
                key,
                String.class,
                TTL,
                () -> "source"
        );

        assertThat(result).isEqualTo("source");
        assertThat(store.values.get(key.value())).isEqualTo("\"source\"");
        assertThat(store.lastTtl).isEqualTo(TTL);
        assertMetric("read", "miss");
        assertMetric("write", "stored");
    }

    @Test
    void readOutageDegradesToSourceAndDoesNotExposeTheKeyInMetrics() {
        store.failReads = true;

        String result = cache.getOrLoad(
                cacheKey(),
                String.class,
                TTL,
                () -> "source"
        );

        assertThat(result).isEqualTo("source");
        assertMetric("read", "degraded");
        assertThat(meters.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags())
                        .noneMatch(tag -> tag.getValue().contains("story-42"))
        );
    }

    @Test
    void corruptValueAndWriteOutageStillReturnAuthoritativeValue() {
        RedisKey key = cacheKey();
        store.values.put(key.value(), "{broken");
        store.failWrites = true;

        String result = cache.getOrLoad(
                key,
                String.class,
                TTL,
                () -> "source"
        );

        assertThat(result).isEqualTo("source");
        assertMetric("read", "degraded");
        assertMetric("write", "degraded");
    }

    @Test
    void failClosedNamespacesCannotUseTheFallbackCache() {
        RedisKey sessionKey = KEYS.create(
                RedisNamespace.SESSION,
                "token-digest"
        );

        assertThatThrownBy(() -> cache.getOrLoad(
                sessionKey,
                String.class,
                TTL,
                () -> "unsafe"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only disposable cache keys may use source fallback");
    }

    private RedisKey cacheKey() {
        return KEYS.create(
                RedisNamespace.CACHE,
                "story",
                "story-42",
                "representation-v1"
        );
    }

    private void assertMetric(
            String operation,
            String outcome
    ) {
        assertThat(meters.get("story.redis.operations")
                .tags(
                        "namespace",
                        "cache",
                        "operation",
                        operation,
                        "outcome",
                        outcome
                )
                .counter()
                .count()).isEqualTo(1.0);
    }

    private static final class StubRedisValueStore
            implements RedisValueStore {

        private final Map<String, String> values = new HashMap<>();
        private boolean failReads;
        private boolean failWrites;
        private Duration lastTtl;

        @Override
        public Optional<String> get(RedisKey key) {
            if (failReads) {
                throw new IllegalStateException("Redis unavailable");
            }
            return Optional.ofNullable(values.get(key.value()));
        }

        @Override
        public void put(
                RedisKey key,
                String value,
                Duration ttl
        ) {
            if (failWrites) {
                throw new IllegalStateException("Redis unavailable");
            }
            values.put(key.value(), value);
            lastTtl = ttl;
        }
    }
}
