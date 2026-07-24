package com.storyplatform.analytics.infrastructure;

import com.storyplatform.analytics.application.port
        .RawReadingEventRepository;
import com.storyplatform.analytics.application.port
        .ReadingSessionPseudonymizer;
import com.storyplatform.analytics.infrastructure.persistence
        .MongoRawReadingEventRepository;
import com.storyplatform.analytics.infrastructure.security
        .HmacReadingSessionPseudonymizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Base64;

@Configuration(proxyBeanMethods = false)
public class AnalyticsConfiguration {

    @Bean
    RawReadingEventRepository rawReadingEventRepository(
            MongoTemplate mongo,
            @Value("${app.analytics.raw-events.retention:90d}")
            Duration retention
    ) {
        return new MongoRawReadingEventRepository(mongo, retention);
    }

    @Bean
    ReadingSessionPseudonymizer readingSessionPseudonymizer(
            @Value("${app.identity.login-risk.hmac-key}") String encodedKey
    ) {
        try {
            return new HmacReadingSessionPseudonymizer(
                    Base64.getDecoder().decode(encodedKey)
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "LOGIN_RISK_HMAC_KEY must be Base64 with 32 bytes",
                    exception
            );
        }
    }

    @Bean
    ReadingHeartbeatAnalyticsHandler readingHeartbeatAnalyticsHandler(
            RawReadingEventRepository events,
            ReadingSessionPseudonymizer pseudonyms,
            ObjectMapper mapper
    ) {
        return new ReadingHeartbeatAnalyticsHandler(
                events,
                pseudonyms,
                mapper
        );
    }

    @Bean
    ReadingCompletionAnalyticsHandler readingCompletionAnalyticsHandler(
            RawReadingEventRepository events,
            ReadingSessionPseudonymizer pseudonyms,
            ObjectMapper mapper
    ) {
        return new ReadingCompletionAnalyticsHandler(
                events,
                pseudonyms,
                mapper
        );
    }
}
