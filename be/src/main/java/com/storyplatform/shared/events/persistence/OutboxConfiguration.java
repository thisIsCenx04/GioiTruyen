package com.storyplatform.shared.events.persistence;

import com.storyplatform.shared.observability.OutboxTelemetry;
import com.storyplatform.shared.observability.TraceContextPropagation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        OutboxProperties.class,
        OutboxWorkerProperties.class
})
public class OutboxConfiguration {

    @Bean
    @ConditionalOnMissingBean
    OpenTelemetry openTelemetry() {
        return OpenTelemetry.noop();
    }

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
            JdbcClient jdbc,
            ObjectMapper objectMapper,
            OutboxProperties properties,
            TraceContextPropagation traceContextPropagation
    ) {
        return new OutboxAppender(
                jdbc,
                objectMapper,
                properties,
                Clock.systemUTC(),
                traceContextPropagation
        );
    }

    @Bean
    OutboxMessageStore outboxMessageStore(
            JdbcClient jdbc,
            ObjectMapper objectMapper
    ) {
        return new OutboxMessageStore(jdbc, objectMapper);
    }

    @Bean
    InboxDispatcher inboxDispatcher(
            JdbcClient jdbc,
            java.util.List<com.storyplatform.shared.events
                    .IntegrationEventHandler> handlers
    ) {
        return new InboxDispatcher(
                jdbc,
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
