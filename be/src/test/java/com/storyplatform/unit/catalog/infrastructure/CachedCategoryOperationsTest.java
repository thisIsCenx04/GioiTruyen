package com.storyplatform.unit.catalog.infrastructure;

import com.storyplatform.catalog.application.CategoryOperations;
import com.storyplatform.catalog.infrastructure.CachedCategoryOperations;
import com.storyplatform.shared.cache.RedisKey;
import com.storyplatform.shared.cache.RedisKeyFactory;
import com.storyplatform.shared.cache.ResilientRedisCache;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CachedCategoryOperationsTest {

    @Test
    @SuppressWarnings("unchecked")
    void cachesTheWholeVersionedTaxonomyWithABoundedTtl() {
        CategoryOperations source = mock(CategoryOperations.class);
        ResilientRedisCache cache = mock(ResilientRedisCache.class);
        var taxonomy = new CategoryOperations.TaxonomyView(
                "2026-07-24.1",
                List.of()
        );
        when(source.getTaxonomy()).thenReturn(taxonomy);
        when(cache.getOrLoad(
                any(RedisKey.class),
                eq(CategoryOperations.TaxonomyView.class),
                eq(Duration.ofMinutes(30)),
                any(Supplier.class)
        )).thenAnswer(invocation -> {
            Supplier<CategoryOperations.TaxonomyView> loader =
                    invocation.getArgument(3);
            return loader.get();
        });
        CachedCategoryOperations operations = new CachedCategoryOperations(
                source,
                cache,
                new RedisKeyFactory("story", "v1", "test")
        );

        assertThat(operations.getTaxonomy()).isSameAs(taxonomy);

        ArgumentCaptor<RedisKey> key =
                ArgumentCaptor.forClass(RedisKey.class);
        verify(cache).getOrLoad(
                key.capture(),
                eq(CategoryOperations.TaxonomyView.class),
                eq(Duration.ofMinutes(30)),
                any(Supplier.class)
        );
        assertThat(key.getValue().value())
                .isEqualTo("story:v1:test:cache:taxonomy:2026-07-24.1");
    }
}
