package com.storyplatform.identity.infrastructure;

import com.storyplatform.identity.application.RegisterUserUseCase;
import com.storyplatform.identity.application.port.PasswordHasher;
import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.application.port.UserIdGenerator;
import com.storyplatform.identity.domain.EmailNormalizer;
import com.storyplatform.identity.domain.PasswordPolicy;
import com.storyplatform.identity.infrastructure.security.Argon2PasswordHasher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RegistrationProperties.class)
public class IdentityConfiguration {

    @Bean
    EmailNormalizer emailNormalizer() {
        return new EmailNormalizer();
    }

    @Bean
    PasswordPolicy passwordPolicy() {
        return new PasswordPolicy();
    }

    @Bean
    PasswordHasher passwordHasher(RegistrationProperties properties) {
        return new Argon2PasswordHasher(
                properties.argon2MemoryKb(),
                properties.argon2Iterations(),
                properties.argon2Parallelism()
        );
    }

    @Bean
    UserIdGenerator userIdGenerator() {
        return () -> UUID.randomUUID().toString();
    }

    @Bean
    RegisterUserUseCase registerUserUseCase(
            UserAccountRepository repository,
            PasswordHasher passwordHasher,
            UserIdGenerator idGenerator,
            EmailNormalizer emailNormalizer,
            PasswordPolicy passwordPolicy,
            RegistrationProperties properties
    ) {
        return new RegisterUserUseCase(
                repository,
                passwordHasher,
                idGenerator,
                emailNormalizer,
                passwordPolicy,
                Clock.systemUTC(),
                properties.currentConsentVersion()
        );
    }
}
