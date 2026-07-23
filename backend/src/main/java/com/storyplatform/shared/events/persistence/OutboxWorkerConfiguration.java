package com.storyplatform.shared.events.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "app.events.outbox.worker",
        name = "enabled",
        havingValue = "true"
)
public class OutboxWorkerConfiguration {

    @Bean
    OutboxPollingWorker outboxPollingWorker(OutboxProcessor processor) {
        return new OutboxPollingWorker(processor);
    }
}
