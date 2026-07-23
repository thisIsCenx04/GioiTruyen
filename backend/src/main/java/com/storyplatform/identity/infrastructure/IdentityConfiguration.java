package com.storyplatform.identity.infrastructure;

import com.storyplatform.identity.application.IdentityService;
import com.storyplatform.identity.application.LoginUseCase;
import com.storyplatform.identity.application.RegisterUserUseCase;
import com.storyplatform.identity.application.RefreshSessionUseCase;
import com.storyplatform.identity.application.RequestPasswordResetUseCase;
import com.storyplatform.identity.application.ResetPasswordUseCase;
import com.storyplatform.identity.application.SessionManagementUseCase;
import com.storyplatform.identity.application.VerifyEmailUseCase;
import com.storyplatform.identity.application.port.EmailVerificationIssuer;
import com.storyplatform.identity.application.port.EmailVerificationRepository;
import com.storyplatform.identity.application.port.AccessTokenIssuer;
import com.storyplatform.identity.application.port.LoginRiskLimiter;
import com.storyplatform.identity.application.port.PasswordHasher;
import com.storyplatform.identity.application.port.PasswordResetRepository;
import com.storyplatform.identity.application.port.RefreshTokenCodec;
import com.storyplatform.identity.application.port
        .RefreshTokenFamilyRepository;
import com.storyplatform.identity.application.port.SessionTokenIssuer;
import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.application.port.UserIdGenerator;
import com.storyplatform.identity.application.port.VerificationTokenCodec;
import com.storyplatform.identity.domain.EmailNormalizer;
import com.storyplatform.identity.domain.PasswordPolicy;
import com.storyplatform.identity.infrastructure.persistence.MongoEmailVerificationIssuer;
import com.storyplatform.identity.infrastructure.security.Argon2PasswordHasher;
import com.storyplatform.identity.infrastructure.security.HmacVerificationTokenCodec;
import com.storyplatform.identity.infrastructure.security.JwtAccessTokenIssuer;
import com.storyplatform.identity.infrastructure.security.RedisLoginRiskLimiter;
import com.storyplatform.identity.infrastructure.security
        .PersistentSessionTokenIssuer;
import com.storyplatform.identity.infrastructure.security
        .SecureRefreshTokenCodec;
import com.storyplatform.identity.infrastructure.security
        .SessionJwtValidator;
import com.storyplatform.shared.cache.RedisKeyFactory;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core
        .DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        RegistrationProperties.class,
        VerificationProperties.class,
        AccessTokenProperties.class,
        LoginRiskProperties.class,
        RefreshSessionProperties.class
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
            VerifyEmailUseCase verifyEmail,
            LoginUseCase login,
            RefreshSessionUseCase refreshSession,
            SessionManagementUseCase sessions,
            RequestPasswordResetUseCase requestPasswordReset,
            ResetPasswordUseCase resetPassword
    ) {
        return new TransactionalIdentityService(
                registerUser,
                verifyEmail,
                login,
                refreshSession,
                sessions,
                requestPasswordReset,
                resetPassword
        );
    }

    @Bean
    LoginRiskLimiter loginRiskLimiter(
            StringRedisTemplate redis,
            RedisKeyFactory keys,
            LoginRiskProperties properties
    ) {
        return new RedisLoginRiskLimiter(
                redis,
                keys,
                properties,
                decodeKey(properties.hmacKey(), "LOGIN_RISK_HMAC_KEY")
        );
    }

    @Bean
    JwtEncoder jwtEncoder(AccessTokenProperties properties) {
        SecretKeySpec key = new SecretKeySpec(
                decodeKey(
                        properties.signingKey(),
                        "JWT_SIGNING_KEY"
                ),
                "HmacSHA256"
        );
        return NimbusJwtEncoder.withSecretKey(key).build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            AccessTokenProperties properties,
            RefreshTokenFamilyRepository families
    ) {
        SecretKeySpec key = new SecretKeySpec(
                decodeKey(
                        properties.signingKey(),
                        "JWT_SIGNING_KEY"
                ),
                "HmacSHA256"
        );
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(
                        properties.issuer()
                ),
                new SessionJwtValidator(
                        properties.audience(),
                        families,
                        Clock.systemUTC()
                )
        ));
        return decoder;
    }

    @Bean
    AccessTokenIssuer accessTokenIssuer(
            JwtEncoder encoder,
            AccessTokenProperties properties
    ) {
        return new JwtAccessTokenIssuer(
                encoder,
                properties,
                Clock.systemUTC()
        );
    }

    @Bean
    RefreshTokenCodec refreshTokenCodec() {
        return new SecureRefreshTokenCodec(new SecureRandom());
    }

    @Bean
    SessionTokenIssuer sessionTokenIssuer(
            AccessTokenIssuer accessTokens,
            RefreshTokenCodec refreshTokens,
            RefreshTokenFamilyRepository families,
            RefreshSessionProperties properties
    ) {
        return new PersistentSessionTokenIssuer(
                accessTokens,
                refreshTokens,
                families,
                properties,
                UUID::randomUUID,
                Clock.systemUTC()
        );
    }

    @Bean
    LoginUseCase loginUseCase(
            UserAccountRepository users,
            PasswordHasher passwords,
            LoginRiskLimiter riskLimiter,
            SessionTokenIssuer tokenIssuer,
            EmailNormalizer emailNormalizer
    ) {
        return new LoginUseCase(
                users,
                passwords,
                riskLimiter,
                tokenIssuer,
                emailNormalizer,
                passwords.hash("dummy login timing password")
        );
    }

    @Bean
    RefreshSessionUseCase refreshSessionUseCase(
            RefreshTokenCodec tokens,
            RefreshTokenFamilyRepository families,
            UserAccountRepository users,
            AccessTokenIssuer accessTokens,
            RefreshSessionProperties properties
    ) {
        return new RefreshSessionUseCase(
                tokens,
                families,
                users,
                accessTokens,
                properties.maximumGenerations(),
                Clock.systemUTC()
        );
    }

    @Bean
    SessionManagementUseCase sessionManagementUseCase(
            RefreshTokenFamilyRepository families
    ) {
        return new SessionManagementUseCase(
                families,
                Clock.systemUTC()
        );
    }

    @Bean
    RequestPasswordResetUseCase requestPasswordResetUseCase(
            UserAccountRepository users,
            PasswordResetRepository resets,
            VerificationTokenCodec tokens,
            EmailNormalizer emails,
            OutboxAppender outbox,
            VerificationProperties properties
    ) {
        return new RequestPasswordResetUseCase(
                users,
                resets,
                tokens,
                emails,
                outbox,
                properties.tokenTtl(),
                Clock.systemUTC()
        );
    }

    @Bean
    ResetPasswordUseCase resetPasswordUseCase(
            PasswordResetRepository resets,
            UserAccountRepository users,
            RefreshTokenFamilyRepository sessions,
            VerificationTokenCodec tokens,
            PasswordHasher passwords,
            PasswordPolicy policy
    ) {
        return new ResetPasswordUseCase(
                resets,
                users,
                sessions,
                tokens,
                passwords,
                policy,
                Clock.systemUTC()
        );
    }

    private static byte[] decodeKey(String value, String name) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (decoded.length < 32) {
                throw new IllegalArgumentException(
                        name + " must contain at least 32 bytes"
                );
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    name + " must be Base64 encoded with at least 32 bytes",
                    exception
            );
        }
    }
}
