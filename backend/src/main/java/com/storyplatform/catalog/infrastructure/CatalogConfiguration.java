package com.storyplatform.catalog.infrastructure;

import com.storyplatform.catalog.application.CategoryOperations;
import com.storyplatform.catalog.application.CategoryQueryService;
import com.storyplatform.catalog.application.port.CategoryRepository;
import com.storyplatform.shared.cache.RedisKeyFactory;
import com.storyplatform.shared.cache.ResilientRedisCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CatalogConfiguration {

    @Bean
    CategoryOperations categoryOperations(
            CategoryRepository categories,
            ResilientRedisCache cache,
            RedisKeyFactory keys
    ) {
        CategoryOperations source = new CategoryQueryService(categories);
        return new CachedCategoryOperations(source, cache, keys);
    }
}
