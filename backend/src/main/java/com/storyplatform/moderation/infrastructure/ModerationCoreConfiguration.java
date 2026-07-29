package com.storyplatform.moderation.infrastructure;

import com.storyplatform.moderation.application.ExternalDonationContentDetector;
import com.storyplatform.moderation.application.contract.ExternalDonationContentPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ModerationCoreConfiguration {

    @Bean
    ExternalDonationContentPolicy externalDonationContentPolicy() {
        return new ExternalDonationContentDetector();
    }
}
