package com.storyplatform.media.infrastructure;

import com.storyplatform.media.application.UploadSignatureOperations;
import com.storyplatform.media.application.UploadSignatureUseCase;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CloudinaryProperties.class)
@ConditionalOnProperty(
        name = "app.media.cloudinary.enabled",
        havingValue = "true"
)
public class MediaConfiguration {

    @Bean
    UploadSignatureOperations uploadSignatureOperations(
            TeamPermissionAuthorizer teams,
            CloudinaryProperties properties
    ) {
        return new UploadSignatureUseCase(
                teams,
                new CloudinaryUploadParameterSigner(
                        properties.apiSecret()
                ),
                () -> UUID.randomUUID().toString(),
                Clock.systemUTC(),
                properties.signatureTtl(),
                properties.cloudName(),
                properties.apiKey(),
                properties.uploadPreset(),
                properties.rootFolder()
        );
    }
}
