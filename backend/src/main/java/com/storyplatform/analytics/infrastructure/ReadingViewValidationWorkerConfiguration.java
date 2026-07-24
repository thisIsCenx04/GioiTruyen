package com.storyplatform.analytics.infrastructure;

import com.storyplatform.analytics.application
        .ReadingViewValidationOperations;
import org.springframework.boot.autoconfigure.condition
        .ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "app.analytics.validation",
        name = "enabled",
        havingValue = "true"
)
public class ReadingViewValidationWorkerConfiguration {

    @Bean
    ReadingViewValidationWorker readingViewValidationWorker(
            ReadingViewValidationOperations validation
    ) {
        return new ReadingViewValidationWorker(validation);
    }
}
