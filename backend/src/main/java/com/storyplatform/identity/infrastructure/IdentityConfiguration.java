package com.storyplatform.identity.infrastructure;

import com.storyplatform.identity.application.IdentityService;
import com.storyplatform.identity.application.RegisterUserUseCase;
import com.storyplatform.identity.application.VerifyEmailUseCase;
import com.storyplatform.identity.application.port.EmailVerificationIssuer;
import com.storyplatform.identity.application.port.EmailVerificationRepository;
import com.storyplatform.identity.application.port.PasswordHasher;
import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.application.port.UserIdGenerator;
import com.storyplatform.identity.application.port.VerificationTokenCodec;
import com.storyplatform.identity.domain.EmailNormalizer;
import com.storyplatform.identity.domain.PasswordPolicy;
import com.storyplatform.identity.infrastructure.persistence.MongoEmailVerificationIssuer;
import com.storyplatform.identity.infrastructure.security.Argon2PasswordHasher;
import com.storyplatform.identity.infrastructure.security.HmacVerificationTokenCodec;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.Base64;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        RegistrationProperties.class,
        VerificationProperties.class
})
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
            EmailVerificationIssuer verificationIssuer,
            UserIdGenerator idGenerator,
            EmailNormalizer emailNormalizer,
            PasswordPolicy passwordPolicy,
            RegistrationProperties properties
    ) {
        return new RegisterUserUseCase(
                repository,
                passwordHasher,
                verificationIssuer,
                idGenerator,
                emailNormalizer,
                passwordPolicy,
                Clock.systemUTC(),
                properties.currentConsentVersion()
        );
    }

    @Bean
    VerificationTokenCodec verificationTokenCodec(
            VerificationProperties properties
    ) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(properties.hmacKey());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "EMAIL_VERIFICATION_HMAC_KEY must be valid Base64",
                    exception
            );
        }
        return new HmacVerificationTokenCodec(key, UUID::randomUUID);
    }

    @Bean
    EmailVerificationIssuer emailVerificationIssuer(
            EmailVerificationRepository repository,
            VerificationTokenCodec tokenCodec,
            OutboxAppender outbox,
            VerificationProperties properties
    ) {
        return new MongoEmailVerificationIssuer(
                repository,
                tokenCodec,
                outbox,
                properties
        );
    }

    @Bean
    VerifyEmailUseCase verifyEmailUseCase(
            EmailVerificationRepository verificationRepository,
            UserAccountRepository userRepository,
            VerificationTokenCodec tokenCodec
    ) {
        return new VerifyEmailUseCase(
                verificationRepository,
                userRepository,
                tokenCodec,
                Clock.systemUTC()
        );
    }

    @Bean
    IdentityService identityService(
            RegisterUserUseCase registerUser,
            VerifyEmailUseCase verifyEmail
    ) {
        return new TransactionalIdentityService(
                registerUser,
                verifyEmail
        );
    }
}
