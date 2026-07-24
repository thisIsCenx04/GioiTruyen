package com.storyplatform.notifications.infrastructure;

import com.storyplatform.notifications.application.NotificationOperations;
import com.storyplatform.notifications.application.NotificationService;
import com.storyplatform.notifications.application.port
        .NotificationCursorCodec;
import com.storyplatform.notifications.application.port
        .NotificationRepository;
import com.storyplatform.notifications.infrastructure.persistence
        .MongoNotificationRepository;
import com.storyplatform.notifications.infrastructure.security
        .HmacNotificationCursorCodec;
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
        return new NotificationService(
                repository,
                cursors,
                Clock.systemUTC(),
                () -> UUID.randomUUID().toString()
        );
    }
}
