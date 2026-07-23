package com.storyplatform.identity.application.port;

import com.storyplatform.identity.domain.EmailVerification;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationRepository {

    void save(EmailVerification verification);

    Optional<EmailVerification> consume(String tokenHash, Instant consumedAt);
}
