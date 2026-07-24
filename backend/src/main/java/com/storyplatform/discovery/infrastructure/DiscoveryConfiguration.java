package com.storyplatform.discovery.infrastructure;

import com.storyplatform.discovery.application.HomeOperations;
import com.storyplatform.discovery.application.HomeSectionBuilder;
import com.storyplatform.discovery.application.HomeService;
import com.storyplatform.discovery.application.port.HomeReadModelRepository;
import com.storyplatform.discovery.application.port.HomeStorySource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

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
}
