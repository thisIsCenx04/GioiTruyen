package com.storyplatform.analytics.infrastructure;

import com.storyplatform.analytics.application.ViewAggregateOperations;
import org.springframework.boot.autoconfigure.condition
        .ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "app.analytics.aggregates",
        name = "enabled",
        havingValue = "true"
)
public class ViewAggregateWorkerConfiguration {

    @Bean
    ViewAggregateWorker viewAggregateWorker(
            ViewAggregateOperations aggregates
    ) {
        return new ViewAggregateWorker(aggregates);
    }
}
