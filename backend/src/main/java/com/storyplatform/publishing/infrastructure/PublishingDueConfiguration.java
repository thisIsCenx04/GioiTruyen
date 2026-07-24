package com.storyplatform.publishing.infrastructure;

import com.storyplatform.publishing.application.PublishingDueOperations;
import com.storyplatform.publishing.application.PublishingDueService;
import com.storyplatform.publishing.application.port.PublishingDueRepository;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoPublishingDueRepository;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import com.storyplatform.teams.application.contract.TeamStatusDirectory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(PublishingDueProperties.class)
@ConditionalOnProperty(
        prefix = "app.publishing.due-worker",
        name = "enabled",
        havingValue = "true"
)
public class PublishingDueConfiguration {

    @Bean
    PublishingDueRepository publishingDueRepository(MongoTemplate mongo) {
        return new MongoPublishingDueRepository(mongo);
    }

    @Bean
    PublishingDueOperations publishingDueOperations(
            PublishingDueRepository repository,
            TeamStatusDirectory teams,
            OutboxAppender outbox,
            PublishingDueProperties properties
    ) {
        PublishingDueService service = new PublishingDueService(
                repository,
                teams,
                outbox,
                () -> UUID.randomUUID().toString(),
                Clock.systemUTC(),
                properties.leaseDuration(),
                properties.inactiveRetryDelay()
        );
        return new TransactionalPublishingDueOperations(service);
    }

    @Bean
    PublishingDueWorker publishingDueWorker(
            PublishingDueOperations operations
    ) {
        return new PublishingDueWorker(operations);
    }
}
