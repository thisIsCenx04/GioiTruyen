package com.storyplatform.integration.cache;

import com.storyplatform.shared.cache.RedisKey;
import com.storyplatform.shared.cache.RedisKeyFactory;
import com.storyplatform.shared.cache.RedisNamespace;
import com.storyplatform.shared.cache.ResilientRedisCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class RedisCacheIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:8.2.7-alpine")
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.url", () -> redisUrl(REDIS));
    }

    @Autowired
    private ResilientRedisCache cache;

    @Autowired
    private RedisKeyFactory keys;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void realRedisStoresNamespacedJsonWithAnExpiry() {
        RedisKey key = keys.create(
                RedisNamespace.CACHE,
                "catalog",
                "representation-v1",
                "query-a42"
        );

        CatalogValue result = cache.getOrLoad(
                key,
                CatalogValue.class,
                Duration.ofMinutes(2),
                () -> new CatalogValue("story-42", "Published")
        );

        assertThat(result).isEqualTo(
                new CatalogValue("story-42", "Published")
        );
        assertThat(redis.opsForValue().get(key.value()))
                .contains("\"storyId\":\"story-42\"");
        assertThat(redis.getExpire(key.value(), TimeUnit.SECONDS))
                .isBetween(107L, 132L);
    }

    private static String redisUrl(GenericContainer<?> container) {
        return "redis://"
                + container.getHost()
                + ":"
                + container.getMappedPort(6379);
    }

    private record CatalogValue(String storyId, String status) {
    }
}
