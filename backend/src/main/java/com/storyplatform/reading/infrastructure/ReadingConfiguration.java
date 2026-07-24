package com.storyplatform.reading.infrastructure;

import com.storyplatform.reading.application.ReadingProgressOperations;
import com.storyplatform.reading.application.ReadingProgressService;
import com.storyplatform.reading.application.ReadingHistoryOperations;
import com.storyplatform.reading.application.ReadingHistoryService;
import com.storyplatform.reading.application.ReadingHeartbeatOperations;
import com.storyplatform.reading.application.ReadingHeartbeatService;
import com.storyplatform.reading.application.ReadingCompletionOperations;
import com.storyplatform.reading.application.ReadingCompletionService;
import com.storyplatform.reading.application.ReadingSessionOperations;
import com.storyplatform.reading.application.ReadingSessionService;
import com.storyplatform.reading.application.port.ReadingHistoryCursorCodec;
import com.storyplatform.reading.application.port.ReadingProgressRepository;
import com.storyplatform.reading.application.port.ReadingSessionQuota;
import com.storyplatform.reading.application.port.ReadingSessionTokenCodec;
import com.storyplatform.reading.infrastructure.persistence
        .MongoReadingProgressRepository;
import com.storyplatform.reading.infrastructure.persistence
        .MongoReadingSessionRepository;
import com.storyplatform.reading.infrastructure.security
        .HmacReadingHistoryCursorCodec;
import com.storyplatform.reading.infrastructure.security
        .HmacReadingSessionTokenCodec;
import com.storyplatform.reading.infrastructure.security
        .RedisReadingSessionQuota;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.storyplatform.shared.cache.RedisKeyFactory;
import com.storyplatform.shared.events.persistence.OutboxAppender;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

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

    @Bean
    MongoReadingSessionRepository readingSessionRepository(
            MongoTemplate mongo
    ) {
        return new MongoReadingSessionRepository(mongo);
    }

    @Bean
    ReadingSessionTokenCodec readingSessionTokenCodec(
            @Value("${app.identity.login-risk.hmac-key}") String encodedKey
    ) {
        try {
            return new HmacReadingSessionTokenCodec(
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
    ReadingSessionQuota readingSessionQuota(
            StringRedisTemplate redis,
            RedisKeyFactory keys,
            @Value("${app.reading.session.start-quota.maximum}")
            int maximum,
            @Value("${app.reading.session.start-quota.window}")
            Duration window
    ) {
        return new RedisReadingSessionQuota(
                redis,
                keys,
                maximum,
                window
        );
    }

    @Bean
    ReadingSessionOperations readingSessionOperations(
            MongoReadingSessionRepository repository,
            ReadingSessionTokenCodec tokens,
            ReadingSessionQuota quota,
            @Value("${app.reading.session.ttl}") Duration ttl,
            @Value("${app.reading.session.heartbeat-interval-seconds}")
            int heartbeatInterval
    ) {
        return new ReadingSessionService(
                repository,
                tokens,
                quota,
                Clock.systemUTC(),
                ttl,
                heartbeatInterval,
                UUID::randomUUID
        );
    }

    @Bean
    ReadingHeartbeatOperations readingHeartbeatOperations(
            MongoReadingSessionRepository repository,
            ReadingSessionTokenCodec tokens,
            OutboxAppender outbox
    ) {
        return new ReadingHeartbeatService(
                repository,
                tokens,
                outbox,
                Clock.systemUTC()
        );
    }

    @Bean
    ReadingCompletionOperations readingCompletionOperations(
            MongoReadingSessionRepository repository,
            ReadingSessionTokenCodec tokens,
            OutboxAppender outbox
    ) {
        return new ReadingCompletionService(
                repository,
                tokens,
                outbox,
                Clock.systemUTC()
        );
    }
}
