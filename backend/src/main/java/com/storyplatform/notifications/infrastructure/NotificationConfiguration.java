package com.storyplatform.notifications.infrastructure;

import com.storyplatform.notifications.application.NotificationOperations;
import com.storyplatform.notifications.application
        .NotificationPreferenceOperations;
import com.storyplatform.notifications.application
        .NotificationPreferenceService;
import com.storyplatform.notifications.application.NotificationService;
import com.storyplatform.notifications.application.PushSubscriptionOperations;
import com.storyplatform.notifications.application.PushSubscriptionService;
import com.storyplatform.notifications.application.port
        .NotificationCursorCodec;
import com.storyplatform.notifications.application.port
        .NotificationRepository;
import com.storyplatform.notifications.application.port
        .NotificationPreferenceRepository;
import com.storyplatform.notifications.application.port
        .UnsubscribeTokenCodec;
import com.storyplatform.notifications.application.port
        .PushSubscriptionRepository;
import com.storyplatform.notifications.infrastructure.persistence
        .MongoNotificationRepository;
import com.storyplatform.notifications.infrastructure.persistence
        .MongoNotificationPreferenceRepository;
import com.storyplatform.notifications.infrastructure.persistence
        .MongoPushSubscriptionRepository;
import com.storyplatform.notifications.infrastructure.security
        .HmacNotificationCursorCodec;
import com.storyplatform.notifications.infrastructure.security
        .HmacUnsubscribeTokenCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;
import java.util.Base64;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class NotificationConfiguration {

    @Bean
    PushSubscriptionRepository pushSubscriptionRepository(
            MongoTemplate mongo
    ) {
        return new MongoPushSubscriptionRepository(mongo);
    }

    @Bean
    PushSubscriptionOperations pushSubscriptionOperations(
            PushSubscriptionRepository repository
    ) {
        return new PushSubscriptionService(
                repository,
                Clock.systemUTC(),
                () -> UUID.randomUUID().toString()
        );
    }

    @Bean
    NotificationPreferenceRepository notificationPreferenceRepository(
            MongoTemplate mongo
    ) {
        return new MongoNotificationPreferenceRepository(mongo);
    }

    @Bean
    UnsubscribeTokenCodec unsubscribeTokenCodec(
            @Value("${app.identity.login-risk.hmac-key}") String encodedKey
    ) {
        return new HmacUnsubscribeTokenCodec(
                Base64.getDecoder().decode(encodedKey)
        );
    }

    @Bean
    NotificationPreferenceOperations notificationPreferenceOperations(
            NotificationPreferenceRepository repository,
            UnsubscribeTokenCodec tokens
    ) {
        return new NotificationPreferenceService(
                repository,
                tokens,
                Clock.systemUTC()
        );
    }

    @Bean
    NotificationRepository notificationRepository(MongoTemplate mongo) {
        return new MongoNotificationRepository(mongo);
    }

    @Bean
    NotificationCursorCodec notificationCursorCodec(
            @Value("${app.identity.login-risk.hmac-key}") String encodedKey
    ) {
        try {
            return new HmacNotificationCursorCodec(
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
    NotificationOperations notificationOperations(
            NotificationRepository repository,
            NotificationCursorCodec cursors
    ) {
        return new TransactionalNotificationOperations(
                new NotificationService(
                        repository,
                        cursors,
                        Clock.systemUTC(),
                        () -> UUID.randomUUID().toString()
                )
        );
    }
}
