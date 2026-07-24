package com.storyplatform.media.infrastructure;

import com.storyplatform.media.application.MediaContentValidator;
import com.storyplatform.media.application.MediaProcessingOperations;
import com.storyplatform.media.application.MediaProcessingService;
import com.storyplatform.media.application.port.MediaProcessingGateway;
import com.storyplatform.media.infrastructure.persistence
        .MongoMediaProcessingRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "app.media.cloudinary",
        name = "processing-enabled",
        havingValue = "true"
)
public class MediaProcessingConfiguration {

    @Bean
    MediaProcessingOperations.Repository mediaProcessingRepository(
            MongoTemplate mongo
    ) {
        return new MongoMediaProcessingRepository(mongo);
    }

    @Bean
    MediaProcessingGateway mediaProcessingGateway(
            ObjectMapper mapper,
            CloudinaryProperties properties
    ) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(properties.processingTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new CloudinaryMediaProcessingGateway(
                new JdkCloudinaryHttpTransport(http),
                mapper,
                properties.cloudName(),
                properties.apiKey(),
                properties.apiSecret(),
                Clock.systemUTC(),
                properties.processingTimeout()
        );
    }

    @Bean
    MediaProcessingOperations mediaProcessingOperations(
            MediaProcessingOperations.Repository repository,
            MediaProcessingGateway gateway,
            CloudinaryProperties properties
    ) {
        return new MediaProcessingService(
                repository,
                gateway,
                new MediaContentValidator(),
                Clock.systemUTC(),
                properties.processingLeaseDuration(),
                properties.processingRetryDelay(),
                properties.processingMaxAttempts()
        );
    }

    @Bean
    MediaProcessingWorker mediaProcessingWorker(
            MediaProcessingOperations processing
    ) {
        return new MediaProcessingWorker(processing);
    }
}
