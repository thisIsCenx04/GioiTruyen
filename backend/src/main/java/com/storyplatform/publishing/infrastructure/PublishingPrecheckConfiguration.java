package com.storyplatform.publishing.infrastructure;

import com.storyplatform.publishing.application
        .LocalPublishingPrecheckEngine;
import com.storyplatform.moderation.application.contract
        .ExternalDonationContentPolicy;
import com.storyplatform.publishing.application.PublishingPrecheckEngine;
import com.storyplatform.publishing.application.PublishingPrecheckOperations;
import com.storyplatform.publishing.application.PublishingPrecheckService;
import com.storyplatform.publishing.application.port
        .PublishingPrecheckRepository;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoPublishingPrecheckRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(PublishingPrecheckProperties.class)
@ConditionalOnProperty(
        prefix = "app.publishing.prechecks",
        name = "enabled",
        havingValue = "true"
)
public class PublishingPrecheckConfiguration {

    @Bean
    PublishingPrecheckRepository publishingPrecheckRepository(
            MongoTemplate mongo
    ) {
        return new MongoPublishingPrecheckRepository(mongo);
    }

    @Bean
    PublishingPrecheckEngine publishingPrecheckEngine(
            ExternalDonationContentPolicy donationPolicy
    ) {
        return new LocalPublishingPrecheckEngine(
                Clock.systemUTC(),
                donationPolicy
        );
    }

    @Bean
    PublishingPrecheckOperations publishingPrecheckOperations(
            PublishingPrecheckRepository repository,
            PublishingPrecheckEngine engine,
            PublishingPrecheckProperties properties
    ) {
        return new PublishingPrecheckService(
                repository,
                engine,
                Clock.systemUTC(),
                properties.leaseDuration(),
                properties.timeout()
        );
    }

    @Bean
    PublishingPrecheckWorker publishingPrecheckWorker(
            PublishingPrecheckOperations prechecks
    ) {
        return new PublishingPrecheckWorker(prechecks);
    }
}
