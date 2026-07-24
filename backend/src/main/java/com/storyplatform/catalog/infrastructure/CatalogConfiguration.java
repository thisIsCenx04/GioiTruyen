package com.storyplatform.catalog.infrastructure;

import com.storyplatform.catalog.application.CategoryOperations;
import com.storyplatform.catalog.application.CategoryQueryService;
import com.storyplatform.catalog.application.StoryCatalogOperations;
import com.storyplatform.catalog.application.StoryCatalogService;
import com.storyplatform.catalog.application.port.CategoryRepository;
import com.storyplatform.catalog.application.port.CatalogCursorCodec;
import com.storyplatform.catalog.application.port.StoryRepository;
import com.storyplatform.catalog.infrastructure.security
        .HmacCatalogCursorCodec;
import com.storyplatform.shared.cache.RedisKeyFactory;
import com.storyplatform.shared.cache.ResilientRedisCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.util.Base64;

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

    @Bean
    CatalogCursorCodec catalogCursorCodec(
            @Value("${app.identity.login-risk.hmac-key}") String encodedKey
    ) {
        try {
            return new HmacCatalogCursorCodec(
                    Base64.getDecoder().decode(encodedKey)
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "LOGIN_RISK_HMAC_KEY must be Base64 with 32 bytes",
                    exception
            );
        }
    }

    @Bean
    StoryCatalogOperations storyCatalogOperations(
            StoryRepository stories,
            CategoryOperations categories,
            CatalogCursorCodec cursors
    ) {
        return new StoryCatalogService(stories, categories, cursors);
    }
}
