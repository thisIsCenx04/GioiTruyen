package com.storyplatform.notifications.infrastructure;

import com.storyplatform.notifications.application.NotificationDeliveryOperations;
import com.storyplatform.notifications.application.NotificationDeliveryService;
import com.storyplatform.notifications.application.port.NotificationDeliveryProvider;
import com.storyplatform.notifications.application.port.NotificationDeliveryRepository;
import com.storyplatform.notifications.application.port.UnsubscribeTokenCodec;
import com.storyplatform.notifications.infrastructure.persistence.MongoNotificationDeliveryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "app.notifications.delivery",
        name = "enabled", havingValue = "true")
public class NotificationDeliveryConfiguration {

    @Bean
    NotificationDeliveryRepository notificationDeliveryRepository(
            MongoTemplate mongo
    ) {
        return new MongoNotificationDeliveryRepository(mongo);
    }

    @Bean
    NotificationDeliveryOperations notificationDeliveryOperations(
            NotificationDeliveryRepository repository,
            UnsubscribeTokenCodec tokens,
            ObjectMapper mapper,
            @Value("${app.notifications.delivery.email.endpoint}")
            URI emailEndpoint,
            @Value("${app.notifications.delivery.email.token}")
            String emailToken,
            @Value("${app.notifications.delivery.push.endpoint}")
            URI pushEndpoint,
            @Value("${app.notifications.delivery.push.token}")
            String pushToken,
            @Value("${app.notifications.delivery.timeout:5s}")
            Duration timeout,
            @Value("${app.notifications.delivery.lease:1m}")
            Duration lease,
            @Value("${app.notifications.delivery.base-retry:30s}")
            Duration retry,
            @Value("${app.notifications.delivery.unsubscribe-ttl:90d}")
            Duration unsubscribeTtl,
            @Value("${app.notifications.delivery.max-attempts:5}")
            int maximumAttempts
    ) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        Map<NotificationDeliveryRepository.Channel,
                NotificationDeliveryProvider> providers = Map.of(
                NotificationDeliveryRepository.Channel.EMAIL,
                new HttpNotificationDeliveryProvider(
                        http, mapper, emailEndpoint, emailToken, timeout
                ),
                NotificationDeliveryRepository.Channel.PUSH,
                new HttpNotificationDeliveryProvider(
                        http, mapper, pushEndpoint, pushToken, timeout
                )
        );
        return new NotificationDeliveryService(
                repository, providers, tokens, Clock.systemUTC(),
                lease, retry, unsubscribeTtl, maximumAttempts
        );
    }

    @Bean
    NotificationDeliveryWorker notificationDeliveryWorker(
            NotificationDeliveryOperations delivery
    ) {
        return new NotificationDeliveryWorker(delivery);
    }
}
