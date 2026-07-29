package com.storyplatform.discovery.infrastructure;

import com.storyplatform.discovery.application.HomeOperations;
import com.storyplatform.discovery.application.HomeSectionBuilder;
import com.storyplatform.discovery.application.HomeService;
import com.storyplatform.discovery.application.RankingOperations;
import com.storyplatform.discovery.application.RankingService;
import com.storyplatform.discovery.application.SearchOperations;
import com.storyplatform.discovery.application.StorySearchService;
import com.storyplatform.discovery.application.SuggestionOperations;
import com.storyplatform.discovery.application.SuggestionService;
import com.storyplatform.discovery.application.port.HomeReadModelRepository;
import com.storyplatform.discovery.application.port.HomeStorySource;
import com.storyplatform.discovery.application.port.RankingRepository;
import com.storyplatform.discovery.application.port.SearchCursorCodec;
import com.storyplatform.discovery.application.port.StorySearchRepository;
import com.storyplatform.discovery.application.port.SuggestionRateLimiter;
import com.storyplatform.discovery.application.port.SuggestionRepository;
import com.storyplatform.discovery.infrastructure.persistence.DisabledRankingRepository;
import com.storyplatform.discovery.infrastructure.persistence.JdbcStorySearchRepository;
import com.storyplatform.discovery.infrastructure.persistence.JdbcSuggestionRepository;
import com.storyplatform.discovery.infrastructure.security.HmacSearchCursorCodec;
import com.storyplatform.discovery.infrastructure.security.RedisSuggestionRateLimiter;
import com.storyplatform.shared.cache.RedisKeyFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

@Configuration(proxyBeanMethods = false)
public class DiscoveryConfiguration {

    @Bean
    RankingRepository rankingRepository() {
        return new DisabledRankingRepository();
    }

    @Bean
    RankingOperations rankingOperations(RankingRepository repository) {
        return new RankingService(repository, Clock.systemUTC());
    }

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
    HomeReadModelProjector homeReadModelProjector(HomeOperations homes) {
        return new HomeReadModelProjector(homes);
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
    StorySearchRepository storySearchRepository(JdbcClient jdbc) {
        return new JdbcStorySearchRepository(jdbc);
    }

    @Bean
    SearchOperations searchOperations(
            StorySearchRepository repository,
            SearchCursorCodec cursors
    ) {
        return new StorySearchService(repository, cursors);
    }

    @Bean
    SuggestionRepository suggestionRepository(JdbcClient jdbc) {
        return new JdbcSuggestionRepository(jdbc);
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
