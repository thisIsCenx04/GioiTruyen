package com.storyplatform.shared.events.persistence;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OutboxProperties.class)
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
}
