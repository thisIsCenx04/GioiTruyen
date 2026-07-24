package com.storyplatform.publishing.infrastructure;

import com.storyplatform.publishing.application.EdgePropagationGateway;
import com.storyplatform.publishing.application
        .PublishingPropagationOperations;
import com.storyplatform.publishing.application
        .PublishingPropagationService;
import com.storyplatform.publishing.application.port
        .PublishingPropagationRepository;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoPublishingPropagationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "app.publishing.propagation",
        name = "enabled",
        havingValue = "true"
)
public class PublishingPropagationConfiguration {

    @Bean
    PublishingPropagationRepository publishingPropagationRepository(
            MongoTemplate mongo
    ) {
        return new MongoPublishingPropagationRepository(mongo);
    }

    @Bean
    EdgePropagationGateway edgePropagationGateway(
            RestClient.Builder builder,
            @Value("${app.publishing.propagation.cloudflare-endpoint}")
            URI cloudflareEndpoint,
            @Value("${app.publishing.propagation.isr-endpoint}")
            URI isrEndpoint,
            @Value("${app.publishing.propagation.cloudflare-token}")
            String cloudflareToken,
            @Value("${app.publishing.propagation.isr-token}")
            String isrToken
    ) {
        return new HttpEdgePropagationGateway(
                builder.build(),
                cloudflareEndpoint,
                isrEndpoint,
                cloudflareToken,
                isrToken
        );
    }

    @Bean
    PublishingPropagationOperations publishingPropagationOperations(
            PublishingPropagationRepository repository,
            EdgePropagationGateway edge,
            @Value("${app.publishing.propagation.lease-duration:30s}")
            Duration leaseDuration,
            @Value("${app.publishing.propagation.initial-backoff:5s}")
            Duration initialBackoff,
            @Value("${app.publishing.propagation.max-attempts:8}")
            int maximumAttempts
    ) {
        return new PublishingPropagationService(
                repository,
                edge,
                Clock.systemUTC(),
                leaseDuration,
                initialBackoff,
                maximumAttempts
        );
    }

    @Bean
    PublishingPropagationWorker publishingPropagationWorker(
            PublishingPropagationOperations operations
    ) {
        return new PublishingPropagationWorker(operations);
    }
}
