package com.storyplatform.catalog.infrastructure;

import com.storyplatform.catalog.application.CategoryOperations;
import com.storyplatform.catalog.application.CategoryQueryService;
import com.storyplatform.shared.cache.RedisKey;
import com.storyplatform.shared.cache.RedisKeyFactory;
import com.storyplatform.shared.cache.RedisNamespace;
import com.storyplatform.shared.cache.ResilientRedisCache;

import java.time.Duration;
import java.util.Objects;

public final class CachedCategoryOperations implements CategoryOperations {

    private static final Duration TTL = Duration.ofMinutes(30);

    private final CategoryOperations source;
    private final ResilientRedisCache cache;
    private final RedisKey key;

    public CachedCategoryOperations(
            CategoryOperations source,
            ResilientRedisCache cache,
            RedisKeyFactory keys
    ) {
        this.source = Objects.requireNonNull(source, "source");
        this.cache = Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(keys, "keys");
        key = keys.create(
                RedisNamespace.CACHE,
                "taxonomy",
                CategoryQueryService.TAXONOMY_VERSION
        );
    }

    @Override
    public TaxonomyView getTaxonomy() {
        return cache.getOrLoad(
                key,
                TaxonomyView.class,
                TTL,
                source::getTaxonomy
        );
    }
}
