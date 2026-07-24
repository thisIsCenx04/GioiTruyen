package com.storyplatform.discovery.infrastructure;

import com.storyplatform.discovery.application.HomeOperations;
import com.storyplatform.discovery.application.HomeSectionBuilder;
import com.storyplatform.discovery.application.HomeService;
import com.storyplatform.discovery.application.SearchOperations;
import com.storyplatform.discovery.application.StorySearchService;
import com.storyplatform.discovery.application.SuggestionOperations;
import com.storyplatform.discovery.application.SuggestionService;
import com.storyplatform.discovery.application.port.HomeReadModelRepository;
import com.storyplatform.discovery.application.port.HomeStorySource;
import com.storyplatform.discovery.application.port.SearchCursorCodec;
import com.storyplatform.discovery.application.port.StorySearchRepository;
import com.storyplatform.discovery.application.port.SuggestionRateLimiter;
import com.storyplatform.discovery.application.port.SuggestionRepository;
import com.storyplatform.discovery.infrastructure.persistence
        .AtlasSuggestionRepository;
import com.storyplatform.discovery.infrastructure.persistence
        .AtlasStorySearchRepository;
import com.storyplatform.discovery.infrastructure.persistence
        .TextStorySearchRepository;
import com.storyplatform.discovery.infrastructure.persistence
        .DisabledSuggestionRepository;
import com.storyplatform.discovery.infrastructure.security
        .HmacSearchCursorCodec;
import com.storyplatform.discovery.infrastructure.security
        .RedisSuggestionRateLimiter;
import com.storyplatform.shared.cache.RedisKeyFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

@Configuration(proxyBeanMethods = false)
public class DiscoveryConfiguration {

    @Bean
    HomeOperations homeOperations(
            HomeReadModelRepository models,
            HomeStorySource stories
    ) {
        return new HomeService(
                models,
                new HomeSectionBuilder(stories),
                Clock.systemUTC()
        );
    }

    @Bean
    HomeReadModelProjector homeReadModelProjector(
            HomeOperations homes
    ) {
        return new HomeReadModelProjector(homes);
    }

    @Bean
    PublishingDiscoveryProjector chapterPublishingDiscoveryProjector(
            HomeOperations homes
    ) {
        return new PublishingDiscoveryProjector(
                homes,
                "publishing.chapter.published"
        );
    }

    @Bean
    PublishingDiscoveryProjector visibilityPublishingDiscoveryProjector(
            HomeOperations homes
    ) {
        return new PublishingDiscoveryProjector(
                homes,
                "publishing.visibility.changed"
        );
    }

    @Bean
    SearchCursorCodec searchCursorCodec(
            @Value("${app.identity.login-risk.hmac-key}") String encodedKey
    ) {
        return new HmacSearchCursorCodec(
                Base64.getDecoder().decode(encodedKey)
        );
    }

    @Bean
    StorySearchRepository storySearchRepository(
            MongoTemplate mongo,
            @Value("${app.discovery.search.backend}") String backend,
            @Value("${app.discovery.search.atlas-index}") String index
    ) {
        return switch (backend) {
            case "atlas" -> new AtlasStorySearchRepository(mongo, index);
            case "text" -> new TextStorySearchRepository(mongo);
            default -> throw new IllegalStateException(
                    "SEARCH_BACKEND must be atlas or text"
            );
        };
    }

    @Bean
    SearchOperations searchOperations(
            StorySearchRepository repository,
            SearchCursorCodec cursors
    ) {
        return new StorySearchService(repository, cursors);
    }

    @Bean
    SuggestionRepository suggestionRepository(
            MongoTemplate mongo,
            @Value("${app.discovery.search.backend}") String backend,
            @Value("${app.discovery.search.atlas-index}") String index
    ) {
        return "atlas".equals(backend)
                ? new AtlasSuggestionRepository(mongo, index)
                : new DisabledSuggestionRepository();
    }

    @Bean
    SuggestionRateLimiter suggestionRateLimiter(
            StringRedisTemplate redis,
            RedisKeyFactory keys,
            @Value("${app.discovery.suggestions.maximum}") int maximum,
            @Value("${app.discovery.suggestions.window}") Duration window,
            @Value("${app.identity.login-risk.hmac-key}") String encodedKey
    ) {
        return new RedisSuggestionRateLimiter(
                redis,
                keys,
                maximum,
                window,
                Base64.getDecoder().decode(encodedKey)
        );
    }

    @Bean
    SuggestionOperations suggestionOperations(
            SuggestionRepository repository,
            SearchCursorCodec cursors,
            SuggestionRateLimiter limiter
    ) {
        return new SuggestionService(repository, cursors, limiter);
    }
}
