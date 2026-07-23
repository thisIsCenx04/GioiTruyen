package com.storyplatform.identity.infrastructure.persistence;

import com.storyplatform.identity.domain.EmailVerification;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoEmailVerificationDocument.COLLECTION)
public record MongoEmailVerificationDocument(
        @Id String id,
        String userId,
        String tokenHash,
        Instant expiresAt,
        Instant consumedAt,
        Instant createdAt
) {

    public static final String COLLECTION = "email_verification_tokens";

    static MongoEmailVerificationDocument from(
            EmailVerification verification
    ) {
        return new MongoEmailVerificationDocument(
                verification.id(),
                verification.userId(),
                verification.tokenHash(),
                verification.expiresAt(),
                verification.consumedAt(),
                verification.createdAt()
        );
    }

    EmailVerification toDomain() {
        return new EmailVerification(
                id,
                userId,
                tokenHash,
                expiresAt,
                consumedAt,
                createdAt
        );
    }
}
