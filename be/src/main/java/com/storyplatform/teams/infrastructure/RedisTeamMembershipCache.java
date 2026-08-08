package com.storyplatform.teams.infrastructure;

import com.storyplatform.shared.cache.RedisKey;
import com.storyplatform.shared.cache.RedisKeyFactory;
import com.storyplatform.shared.cache.RedisNamespace;
import com.storyplatform.shared.cache.ResilientRedisCache;
import com.storyplatform.teams.application.port.TeamMembershipCache;
import com.storyplatform.teams.domain.TeamMembership;

import java.time.Duration;
import java.util.Objects;

public final class RedisTeamMembershipCache
        implements TeamMembershipCache {

    private static final Duration TTL = Duration.ofMinutes(2);

    private final ResilientRedisCache cache;
    private final RedisKeyFactory keys;

    public RedisTeamMembershipCache(
            ResilientRedisCache cache,
            RedisKeyFactory keys
    ) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.keys = Objects.requireNonNull(keys, "keys");
    }

    @Override
    public Snapshot get(TeamMembership authoritativeMembership) {
        Objects.requireNonNull(
                authoritativeMembership,
                "authoritativeMembership"
        );
        RedisKey key = keys.create(
                RedisNamespace.CACHE,
                "team-membership",
                authoritativeMembership.teamId(),
                authoritativeMembership.userId(),
                "v" + authoritativeMembership.version()
        );
        return cache.getOrLoad(
                key,
                Snapshot.class,
                TTL,
                () -> Snapshot.from(authoritativeMembership)
        );
    }
}
