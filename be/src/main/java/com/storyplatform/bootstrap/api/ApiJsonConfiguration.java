package com.storyplatform.bootstrap.api;

import com.storyplatform.shared.api.ApiRequestLimits;
import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ApiJsonConfiguration {

    @Bean
    JsonFactoryBuilderCustomizer defensiveJsonReadConstraints() {
        return builder -> builder.streamReadConstraints(
                ApiRequestLimits.jsonConstraints()
        );
    }
}
