package com.storyplatform.analytics.infrastructure;

import com.storyplatform.analytics.application.TrafficFraudOperations;
import org.springframework.boot.autoconfigure.condition
        .ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "app.analytics.fraud",
        name = "enabled",
        havingValue = "true"
)
public class TrafficFraudWorkerConfiguration {

    @Bean
    TrafficFraudWorker trafficFraudWorker(TrafficFraudOperations fraud) {
        return new TrafficFraudWorker(fraud);
    }
}
