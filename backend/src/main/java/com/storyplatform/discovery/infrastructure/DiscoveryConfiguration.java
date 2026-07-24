package com.storyplatform.discovery.infrastructure;

import com.storyplatform.discovery.application.HomeOperations;
import com.storyplatform.discovery.application.HomeSectionBuilder;
import com.storyplatform.discovery.application.HomeService;
import com.storyplatform.discovery.application.SearchOperations;
import com.storyplatform.discovery.application.StorySearchService;
import com.storyplatform.discovery.application.port.HomeReadModelRepository;
import com.storyplatform.discovery.application.port.HomeStorySource;
import com.storyplatform.discovery.application.port.SearchCursorCodec;
import com.storyplatform.discovery.application.port.StorySearchRepository;
import com.storyplatform.discovery.infrastructure.persistence
        .AtlasStorySearchRepository;
import com.storyplatform.discovery.infrastructure.persistence
        .TextStorySearchRepository;
import com.storyplatform.discovery.infrastructure.security
        .HmacSearchCursorCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;
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
}
