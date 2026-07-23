package com.storyplatform.shared.events.persistence;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        OutboxProperties.class,
        OutboxWorkerProperties.class
})
public class OutboxConfiguration {

    @Bean
    OutboxAppender outboxAppender(
            MongoTemplate mongoTemplate,
            ObjectMapper objectMapper,
            OutboxProperties properties
    ) {
        return new OutboxAppender(
                mongoTemplate,
                objectMapper,
                properties,
                Clock.systemUTC()
        );
    }

    @Bean
    OutboxMessageStore outboxMessageStore(MongoTemplate mongoTemplate) {
        return new OutboxMessageStore(mongoTemplate);
    }

    @Bean
    InboxDispatcher inboxDispatcher(
            MongoTemplate mongoTemplate,
            java.util.List<com.storyplatform.shared.events
                    .IntegrationEventHandler> handlers
    ) {
        return new InboxDispatcher(
                mongoTemplate,
                handlers,
                Clock.systemUTC()
        );
    }

    @Bean
    OutboxProcessor outboxProcessor(
            OutboxMessageStore store,
            InboxDispatcher dispatcher,
            OutboxWorkerProperties properties
    ) {
        return new OutboxProcessor(
                store,
                dispatcher,
                properties,
                new RetryBackoff(
                        properties.initialBackoff(),
                        properties.maxBackoff()
                ),
                Clock.systemUTC()
        );
    }
}
