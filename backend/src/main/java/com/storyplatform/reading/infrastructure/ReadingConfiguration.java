package com.storyplatform.reading.infrastructure;

import com.storyplatform.reading.application.ReadingProgressOperations;
import com.storyplatform.reading.application.ReadingProgressService;
import com.storyplatform.reading.application.ReadingHistoryOperations;
import com.storyplatform.reading.application.ReadingHistoryService;
import com.storyplatform.reading.application.port.ReadingHistoryCursorCodec;
import com.storyplatform.reading.application.port.ReadingProgressRepository;
import com.storyplatform.reading.infrastructure.persistence
        .MongoReadingProgressRepository;
import com.storyplatform.reading.infrastructure.security
        .HmacReadingHistoryCursorCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;
import java.util.Base64;

@Configuration(proxyBeanMethods = false)
public class ReadingConfiguration {

    @Bean
    MongoReadingProgressRepository readingProgressRepository(
            MongoTemplate mongo
    ) {
        return new MongoReadingProgressRepository(mongo);
    }

    @Bean
    ReadingProgressOperations readingProgressOperations(
            ReadingProgressRepository repository
    ) {
        return new ReadingProgressService(
                repository,
                Clock.systemUTC()
        );
    }

    @Bean
    ReadingHistoryCursorCodec readingHistoryCursorCodec(
            @Value("${app.identity.login-risk.hmac-key}") String encodedKey
    ) {
        try {
            return new HmacReadingHistoryCursorCodec(
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
    ReadingHistoryOperations readingHistoryOperations(
            MongoReadingProgressRepository repository,
            ReadingHistoryCursorCodec cursors
    ) {
        return new ReadingHistoryService(repository, cursors);
    }
}
