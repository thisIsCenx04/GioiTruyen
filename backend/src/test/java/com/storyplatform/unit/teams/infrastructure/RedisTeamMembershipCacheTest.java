package com.storyplatform.unit.teams.infrastructure;

import com.storyplatform.shared.cache.RedisCacheTelemetry;
import com.storyplatform.shared.cache.RedisKey;
import com.storyplatform.shared.cache.RedisKeyFactory;
import com.storyplatform.shared.cache.RedisTtlPolicy;
import com.storyplatform.shared.cache.RedisValueCodec;
import com.storyplatform.shared.cache.RedisValueStore;
import com.storyplatform.shared.cache.ResilientRedisCache;
import com.storyplatform.teams.domain.TeamMembership;
import com.storyplatform.teams.infrastructure.RedisTeamMembershipCache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RedisTeamMembershipCacheTest {

    private final StubStore store = new StubStore();
    private final RedisTeamMembershipCache cache =
            new RedisTeamMembershipCache(
                    new ResilientRedisCache(
                            store,
                            new RedisValueCodec(new ObjectMapper()),
                            new RedisTtlPolicy(
                                    Duration.ofSeconds(30),
                                    Duration.ofMinutes(10),
                                    0,
                                    () -> 0.5
                            ),
                            new RedisCacheTelemetry(
                                    new SimpleMeterRegistry()
                            )
                    ),
                    new RedisKeyFactory("gioitruyen", "v1", "test")
            );

    @Test
    void cacheKeyChangesWhenMembershipVersionChanges() {
        var versionOne = cache.get(member(1));
        var versionTwo = cache.get(member(2));

        assertThat(versionOne.version()).isEqualTo(1);
        assertThat(versionTwo.version()).isEqualTo(2);
        assertThat(store.values.keySet()).anySatisfy(key ->
                assertThat(key).endsWith(":v1"));
        assertThat(store.values.keySet()).anySatisfy(key ->
                assertThat(key).endsWith(":v2"));
    }

    private static TeamMembership member(long version) {
        return new TeamMembership(
                "team-1:user-1",
                "team-1",
                "user-1",
                TeamMembership.Role.MEMBER,
                Set.of("story:edit"),
                TeamMembership.State.ACTIVE,
                Instant.parse("2026-07-24T00:00:00Z"),
                version
        );
    }

    private static final class StubStore implements RedisValueStore {

        private final Map<String, String> values = new HashMap<>();

        @Override
        public Optional<String> get(RedisKey key) {
            return Optional.ofNullable(values.get(key.value()));
        }

        @Override
        public void put(
                RedisKey key,
                String value,
                Duration ttl
        ) {
            values.put(key.value(), value);
        }
    }
}
