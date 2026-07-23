package com.storyplatform.shared.events.persistence;

import com.storyplatform.shared.observability.OutboxTelemetry;
import com.storyplatform.shared.observability.TraceContextPropagation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.OpenTelemetry;
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
    OutboxTelemetry outboxTelemetry(
            ObservationRegistry observationRegistry,
            TraceContextPropagation traceContextPropagation
    ) {
        return new OutboxTelemetry(
                observationRegistry,
                traceContextPropagation
        );
    }

    @Bean
    TraceContextPropagation traceContextPropagation(
            OpenTelemetry openTelemetry
    ) {
        return new TraceContextPropagation(openTelemetry);
    }

    @Bean
    OutboxAppender outboxAppender(
            MongoTemplate mongoTemplate,
            ObjectMapper objectMapper,
            OutboxProperties properties,
            TraceContextPropagation traceContextPropagation
    ) {
        return new OutboxAppender(
                mongoTemplate,
                objectMapper,
                properties,
                Clock.systemUTC(),
                traceContextPropagation
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
            OutboxWorkerProperties properties,
            OutboxTelemetry telemetry
    ) {
        return new OutboxProcessor(
                store,
                dispatcher,
                properties,
                new RetryBackoff(
                        properties.initialBackoff(),
                        properties.maxBackoff()
                ),
                Clock.systemUTC(),
                telemetry
        );
    }
}
