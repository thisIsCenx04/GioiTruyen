package com.storyplatform.moderation.infrastructure;

import com.storyplatform.moderation.application.CommunityReportOperations;
import com.storyplatform.moderation.application.CommunityReportService;
import com.storyplatform.moderation.application.CopyrightCaseOperations;
import com.storyplatform.moderation.application.CopyrightCaseService;
import com.storyplatform.moderation.application.ModerationAppealOperations;
import com.storyplatform.moderation.application.ModerationAppealService;
import com.storyplatform.moderation.application.ModerationQueueOperations;
import com.storyplatform.moderation.application.ModerationQueueService;
import com.storyplatform.moderation.application.ModerationDecisionOperations;
import com.storyplatform.moderation.application.ModerationDecisionService;
import com.storyplatform.moderation.application.ReportRateLimiter;
import com.storyplatform.moderation.application.port
        .CommunityReportRepository;
import com.storyplatform.moderation.application.port.CopyrightCaseRepository;
import com.storyplatform.moderation.application.port
        .ModerationAppealRepository;
import com.storyplatform.moderation.application.port
        .ModerationDecisionRepository;
import com.storyplatform.moderation.application.port
        .ModerationQueueCursorCodec;
import com.storyplatform.moderation.application.port
        .ModerationQueueRepository;
import com.storyplatform.moderation.infrastructure.persistence
        .MongoCommunityReportRepository;
import com.storyplatform.moderation.infrastructure.persistence
        .MongoCopyrightCaseRepository;
import com.storyplatform.moderation.infrastructure.persistence
        .MongoModerationAppealRepository;
import com.storyplatform.moderation.infrastructure.persistence
        .MongoModerationQueueRepository;
import com.storyplatform.moderation.infrastructure.persistence
        .MongoModerationDecisionRepository;
import com.storyplatform.moderation.infrastructure.security
        .HmacModerationQueueCursorCodec;
import com.storyplatform.shared.cache.RedisKeyFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ModerationQueueProperties.class)
public class ModerationConfiguration {

    @Bean
    CopyrightCaseRepository copyrightCaseRepository(MongoTemplate mongo) {
        return new MongoCopyrightCaseRepository(mongo);
    }

    @Bean
    CopyrightCaseOperations copyrightCaseOperations(
            CopyrightCaseRepository repository,
            @Value("${app.moderation.copyright.response-sla}")
            Duration responseSla,
            @Value("${app.moderation.copyright.hold-duration}")
            Duration holdDuration
    ) {
        return new TransactionalCopyrightCaseOperations(
                new CopyrightCaseService(
                        repository,
                        Clock.systemUTC(),
                        responseSla,
                        holdDuration,
                        () -> UUID.randomUUID().toString()
                )
        );
    }

    @Bean
    ModerationAppealRepository moderationAppealRepository(
            MongoTemplate mongo
    ) {
        return new MongoModerationAppealRepository(mongo);
    }

    @Bean
    ModerationAppealOperations moderationAppealOperations(
            ModerationAppealRepository repository,
            @Value("${app.moderation.appeals.window}") Duration window
    ) {
        return new TransactionalModerationAppealOperations(
                new ModerationAppealService(
                        repository,
                        Clock.systemUTC(),
                        window,
                        () -> UUID.randomUUID().toString()
                )
        );
    }

    @Bean
    CommunityReportRepository communityReportRepository(
            MongoTemplate mongo
    ) {
        return new MongoCommunityReportRepository(mongo);
    }

    @Bean
    ReportRateLimiter reportRateLimiter(
            StringRedisTemplate redis,
            RedisKeyFactory keys,
            @Value("${app.moderation.reports.rate-limit.window}")
            Duration window,
            @Value("${app.identity.login-risk.hmac-key}")
            String encodedKey
    ) {
        return new RedisReportRateLimiter(
                redis,
                keys,
                window,
                Base64.getDecoder().decode(encodedKey)
        );
    }

    @Bean
    CommunityReportOperations communityReportOperations(
            CommunityReportRepository repository,
            ReportRateLimiter limiter
    ) {
        return new CommunityReportService(
                repository,
                limiter,
                Clock.systemUTC()
        );
    }

    @Bean
    ModerationQueueRepository moderationQueueRepository(
            MongoTemplate mongo
    ) {
        return new MongoModerationQueueRepository(mongo);
    }

    @Bean
    ModerationDecisionRepository moderationDecisionRepository(
            MongoTemplate mongo
    ) {
        return new MongoModerationDecisionRepository(
                mongo,
                () -> UUID.randomUUID().toString()
        );
    }

    @Bean
    ModerationQueueCursorCodec moderationQueueCursorCodec(
            @Value("${app.identity.login-risk.hmac-key}") String encodedKey
    ) {
        try {
            return new HmacModerationQueueCursorCodec(
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
    ModerationQueueOperations moderationQueueOperations(
            ModerationQueueRepository repository,
            ModerationQueueCursorCodec cursors,
            ModerationQueueProperties properties
    ) {
        return new ModerationQueueService(
                repository,
                cursors,
                Clock.systemUTC(),
                properties.claimLease()
        );
    }

    @Bean
    ModerationDecisionOperations moderationDecisionOperations(
            ModerationDecisionRepository repository
    ) {
        return new TransactionalModerationDecisionOperations(
                new ModerationDecisionService(
                        repository,
                        Clock.systemUTC()
                )
        );
    }
}
