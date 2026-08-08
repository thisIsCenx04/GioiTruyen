package com.storyplatform.media.infrastructure;

import com.storyplatform.media.application.UploadSignatureOperations;
import com.storyplatform.media.application.UploadSignatureUseCase;
import com.storyplatform.media.application.port.UploadParameterSigner;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class MediaConfiguration {

    @Bean
    UploadParameterSigner uploadParameterSigner() {
        return parameters -> "dummy_signature";
    }

    @Bean
    UploadSignatureOperations uploadSignatureOperations(
            TeamPermissionAuthorizer teams,
            UploadParameterSigner signer
    ) {
        return new UploadSignatureUseCase(
                teams,
                signer,
                () -> UUID.randomUUID().toString(),
                Clock.systemUTC(),
                Duration.ofMinutes(15),
                "dummy_cloud",
                "dummy_api_key",
                "dummy_preset",
                "media"
        );
    }
}
