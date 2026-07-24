package com.storyplatform.analytics.infrastructure;

import com.storyplatform.analytics.application.port
        .RawReadingEventRepository;
import com.storyplatform.analytics.application.port
        .ReadingSessionPseudonymizer;
import com.storyplatform.analytics.application
        .ReadingViewClassifier;
import com.storyplatform.analytics.application
        .ReadingViewValidationOperations;
import com.storyplatform.analytics.application
        .ReadingViewValidationService;
import com.storyplatform.analytics.application.TrafficFraudOperations;
import com.storyplatform.analytics.application.TrafficFraudScorer;
import com.storyplatform.analytics.application.TrafficFraudService;
import com.storyplatform.analytics.application.ViewAggregateOperations;
import com.storyplatform.analytics.application.ViewAggregateService;
import com.storyplatform.analytics.application.port
        .ReadingViewValidationRepository;
import com.storyplatform.analytics.infrastructure.persistence
        .MongoRawReadingEventRepository;
import com.storyplatform.analytics.infrastructure.persistence
        .MongoReadingViewValidationRepository;
import com.storyplatform.analytics.application.port.TrafficFraudRepository;
import com.storyplatform.analytics.infrastructure.persistence
        .MongoTrafficFraudRepository;
import com.storyplatform.analytics.application.port.ViewAggregateRepository;
import com.storyplatform.analytics.infrastructure.persistence
        .MongoViewAggregateRepository;
import com.storyplatform.analytics.application.TeamAnalyticsOperations;
import com.storyplatform.analytics.application.TeamAnalyticsService;
import com.storyplatform.analytics.application.contract
        .RewardViewAggregateDirectory;
import com.storyplatform.analytics.application.port.TeamAnalyticsRepository;
import com.storyplatform.analytics.infrastructure.persistence
        .MongoTeamAnalyticsRepository;
import com.storyplatform.analytics.infrastructure.persistence
        .MongoRewardViewAggregateDirectory;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;
import com.storyplatform.analytics.infrastructure.security
        .HmacReadingSessionPseudonymizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import tools.jackson.databind.ObjectMapper;
import com.storyplatform.reading.application.contract
        .ReadingActorReferences;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

@Configuration(proxyBeanMethods = false)
public class AnalyticsConfiguration {

    @Bean
    RewardViewAggregateDirectory rewardViewAggregateDirectory(
            MongoTemplate mongo
    ) {
        return new MongoRewardViewAggregateDirectory(mongo);
    }

    @Bean
    TeamAnalyticsRepository teamAnalyticsRepository(MongoTemplate mongo) {
        return new MongoTeamAnalyticsRepository(mongo);
    }

    @Bean
    TeamAnalyticsOperations teamAnalyticsOperations(
            TeamPermissionAuthorizer permissions,
            TeamAnalyticsRepository repository
    ) {
        return new TeamAnalyticsService(
                permissions,
                repository,
                Clock.systemUTC()
        );
    }

    @Bean
    ViewAggregateRepository viewAggregateRepository(MongoTemplate mongo) {
        return new TransactionalViewAggregateRepository(
                new MongoViewAggregateRepository(mongo)
        );
    }

    @Bean
    ViewAggregateOperations viewAggregateOperations(
            ViewAggregateRepository repository,
            @Value("${app.analytics.aggregates.lease:1m}")
            Duration lease,
            @Value("${app.analytics.aggregates.retry-delay:30s}")
            Duration retryDelay
    ) {
        return new ViewAggregateService(
                repository,
                Clock.systemUTC(),
                lease,
                retryDelay
        );
    }

    @Bean
    TrafficFraudRepository trafficFraudRepository(MongoTemplate mongo) {
        return new TransactionalTrafficFraudRepository(
                new MongoTrafficFraudRepository(mongo)
        );
    }

    @Bean
    TrafficFraudOperations trafficFraudOperations(
            TrafficFraudRepository repository,
            @Value("${app.analytics.fraud.lease:1m}") Duration lease,
            @Value("${app.analytics.fraud.retry-delay:30s}")
            Duration retryDelay
    ) {
        return new TrafficFraudService(
                repository,
                new TrafficFraudScorer(),
                Clock.systemUTC(),
                lease,
                retryDelay
        );
    }

    @Bean
    ReadingViewValidationRepository readingViewValidationRepository(
            MongoTemplate mongo,
            ReadingActorReferences actorReferences,
            @Value("${app.analytics.raw-events.retention:90d}")
            Duration retention
    ) {
        return new MongoReadingViewValidationRepository(
                mongo,
                actorReferences,
                retention
        );
    }

    @Bean
    ReadingViewValidationOperations readingViewValidationOperations(
            ReadingViewValidationRepository repository,
            @Value("${app.analytics.validation.readiness-delay:2m}")
            Duration readinessDelay,
            @Value("${app.analytics.validation.lease:1m}")
            Duration lease,
            @Value("${app.analytics.validation.retry-delay:30s}")
            Duration retryDelay
    ) {
        return new ReadingViewValidationService(
                repository,
                new ReadingViewClassifier(),
                Clock.systemUTC(),
                readinessDelay,
                lease,
                retryDelay
        );
    }

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
