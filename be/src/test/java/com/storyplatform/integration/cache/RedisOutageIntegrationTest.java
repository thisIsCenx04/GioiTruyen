package com.storyplatform.integration.cache;

import com.storyplatform.shared.cache.RedisKey;
import com.storyplatform.shared.cache.RedisKeyFactory;
import com.storyplatform.shared.cache.RedisNamespace;
import com.storyplatform.shared.cache.ResilientRedisCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class RedisOutageIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:8.2.7-alpine")
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.url", () ->
                "redis://"
                        + REDIS.getHost()
                        + ":"
                        + REDIS.getMappedPort(6379)
        );
        registry.add("spring.data.redis.connect-timeout", () -> "100ms");
        registry.add("spring.data.redis.timeout", () -> "100ms");
    }

    @Autowired
    private ResilientRedisCache cache;

    @Autowired
    private RedisKeyFactory keys;

    @Test
    void redisOutageReturnsAuthoritativeDataWithinAControlBound() {
        RedisKey key = keys.create(
                RedisNamespace.CACHE,
                "story",
                "story-42",
                "representation-v1"
        );
        REDIS.stop();

        String result = assertTimeout(
                Duration.ofSeconds(5),
                () -> cache.getOrLoad(
                        key,
                        String.class,
                        Duration.ofMinutes(2),
                        () -> "source-of-truth"
                )
        );

        assertThat(result).isEqualTo("source-of-truth");
    }
}
