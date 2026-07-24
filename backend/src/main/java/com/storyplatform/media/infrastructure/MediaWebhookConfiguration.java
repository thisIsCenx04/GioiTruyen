package com.storyplatform.media.infrastructure;

import com.storyplatform.media.application.MediaWebhookOperations;
import com.storyplatform.media.application.MediaWebhookUseCase;
import com.storyplatform.media.infrastructure.persistence
        .MongoMediaAssetRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CloudinaryProperties.class)
@ConditionalOnProperty(
        name = "app.media.cloudinary.webhook-enabled",
        havingValue = "true"
)
public class MediaWebhookConfiguration {

    @Bean
    MediaWebhookOperations mediaWebhookOperations(
            CloudinaryProperties properties,
            MongoTemplate mongo,
            ObjectMapper mapper
    ) {
        Clock clock = Clock.systemUTC();
        MediaWebhookUseCase useCase = new MediaWebhookUseCase(
                new CloudinaryWebhookSignatureVerifier(
                        properties.webhookSecret(),
                        properties.webhookMaxAge(),
                        clock
                ),
                new CloudinaryNotificationJsonDecoder(mapper, clock),
                new MongoMediaAssetRepository(mongo)
        );
        return new TransactionalMediaWebhookOperations(useCase);
    }
}
