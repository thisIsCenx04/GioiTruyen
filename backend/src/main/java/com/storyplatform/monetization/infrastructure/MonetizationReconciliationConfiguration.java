package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application
        .MonetizationReconciliationOperations;
import com.storyplatform.monetization.application
        .MonetizationReconciliationResolutionOperations;
import com.storyplatform.monetization.application
        .MonetizationReconciliationResolutionService;
import com.storyplatform.monetization.application
        .MonetizationReconciliationService;
import com.storyplatform.monetization.application.port
        .MonetizationReconciliationGateway;
import com.storyplatform.monetization.application.port
        .MonetizationReconciliationRepository;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoMonetizationReconciliationRepository;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition
        .ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "app.monetization.reconciliation.enabled",
        havingValue = "true"
)
public class MonetizationReconciliationConfiguration {

    @Bean
    MonetizationReconciliationRepository reconciliationRepository(
            MongoTemplate mongo
    ) {
        return new MongoMonetizationReconciliationRepository(mongo);
    }

    @Bean
    MonetizationReconciliationGateway reconciliationGateway(
            ObjectMapper json,
            @Value("${app.monetization.reconciliation.endpoint}")
            URI endpoint,
            @Value("${app.monetization.reconciliation.provider}")
            String provider,
            @Value("${app.monetization.reconciliation.token}")
            String token,
            @Value("${app.monetization.reconciliation.timeout}")
            Duration timeout
    ) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new HttpMonetizationReconciliationGateway(
                http,
                json,
                endpoint,
                provider,
                token,
                timeout
        );
    }

    @Bean
    MonetizationReconciliationOperations reconciliationOperations(
            MonetizationReconciliationRepository repository,
            OutboxAppender outbox
    ) {
        return new TransactionalMonetizationReconciliationOperations(
                new MonetizationReconciliationService(
                        repository,
                        outbox,
                        Clock.systemUTC(),
                        UUID::randomUUID
                )
        );
    }

    @Bean
    MonetizationReconciliationWorker reconciliationWorker(
            MonetizationReconciliationGateway gateway,
            MonetizationReconciliationOperations operations
    ) {
        return new MonetizationReconciliationWorker(
                gateway,
                operations,
                Clock.systemUTC()
        );
    }

    @Bean
    MonetizationReconciliationResolutionOperations
            reconciliationResolutions(
            MonetizationReconciliationRepository repository,
            OutboxAppender outbox
    ) {
        return new TransactionalMonetizationReconciliationResolutionOperations(
                new MonetizationReconciliationResolutionService(
                        repository,
                        outbox,
                        Clock.systemUTC(),
                        UUID::randomUUID
                )
        );
    }
}
