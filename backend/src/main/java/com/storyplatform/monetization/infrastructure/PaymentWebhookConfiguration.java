package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.PaymentWebhookOperations;
import com.storyplatform.monetization.application.PaymentWebhookService;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoPaymentEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "app.monetization.topup.webhook-enabled",
        havingValue = "true"
)
public class PaymentWebhookConfiguration {

    @Bean
    PaymentWebhookOperations paymentWebhookOperations(
            MongoTemplate mongo,
            ObjectMapper mapper,
            @Value("${app.monetization.topup.provider}") String provider,
            @Value("${app.monetization.topup.webhook-secret}") String secret,
            @Value("${app.monetization.topup.webhook-max-age}")
            Duration maximumAge
    ) {
        Clock clock = Clock.systemUTC();
        return new PaymentWebhookService(
                new HmacPaymentWebhookVerifier(
                        provider,
                        secret,
                        maximumAge,
                        clock
                ),
                new PaymentEventJsonDecoder(mapper),
                new MongoPaymentEventRepository(mongo),
                clock,
                UUID::randomUUID
        );
    }
}
